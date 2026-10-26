// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github.util;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountListener;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.http.client.ServerHostAliases;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import git4idea.remote.GitRemoteUrlCoordinates;
import git4idea.remote.hosting.GitAsyncExtensions;
import git4idea.remote.hosting.GitHostingUrlUtil;
import git4idea.remote.hosting.HostedGitRepositoriesManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.exceptions.GithubParseException;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GHHostedRepositoriesManager implements HostedGitRepositoriesManager<GHGitRepositoryMapping>, Disposable {
    private static final Logger LOG = Logger.getInstance(GHHostedRepositoriesManager.class);

    private final Project myProject;
    private final AccountManager myAccountManager;

    private final MutableObservableValue<Set<GithubServerPath>> myAccountsServersState = MutableObservableValue.of(Set.of());
    private final MutableObservableValue<Set<GithubServerPath>> myDiscoveredServersState = MutableObservableValue.of(Set.of());
    private final MutableObservableValue<Set<GithubServerPath>> myServersState = MutableObservableValue.of(Set.of());
    private volatile @Nullable ObservableValue<Set<GHGitRepositoryMapping>> myKnownRepositoriesState;

    @Inject
    public GHHostedRepositoriesManager(Project project, AccountManager accountManager) {
        myProject = project;
        myAccountManager = accountManager;
    }

    public static GHHostedRepositoriesManager getInstance(Project project) {
        return project.getInstance(GHHostedRepositoriesManager.class);
    }

    @Override
    public ObservableValue<Set<GHGitRepositoryMapping>> getKnownRepositoriesState() {
        ObservableValue<Set<GHGitRepositoryMapping>> state = myKnownRepositoriesState;
        if (state == null) {
            synchronized (this) {
                state = myKnownRepositoriesState;
                if (state == null) {
                    state = createKnownRepositoriesState();
                    myKnownRepositoriesState = state;
                }
            }
        }
        return state;
    }

    private ObservableValue<Set<GHGitRepositoryMapping>> createKnownRepositoriesState() {
        ObservableValue<Set<GitRemoteUrlCoordinates>> gitRemotesState = GitAsyncExtensions.gitRemotesStateIn(myProject, this);
        ObservableValue<Map<String, GithubServerPath>> aliasesState =
            GithubSettings.getInstance().getHostAliasesState().map(GHHostedRepositoriesManager::parseGitHubHostAliases);

        updateAccountsServers();
        myAccountManager.addListener(new AccountListener() {
            @Override
            public void accountsChanged() {
                updateAccountsServers();
            }

            @Override
            public void accountChanged(Account account) {
                updateAccountsServers();
            }
        }, this);

        Disposer.register(this, myAccountsServersState.addListener(servers -> updateServers())::run);
        Disposer.register(this, myDiscoveredServersState.addListener(servers -> updateServers())::run);
        updateServers();

        MutableObservableValue<Set<GitRemoteUrlCoordinates>> notAliasedRemotes =
            MutableObservableValue.of(filterNotAliased(gitRemotesState.get(), aliasesState.get()));
        Runnable updateNotAliased = () -> notAliasedRemotes.set(filterNotAliased(gitRemotesState.get(), aliasesState.get()));
        Disposer.register(this, gitRemotesState.addListener(remotes -> updateNotAliased.run())::run);
        Disposer.register(this, aliasesState.addListener(aliases -> updateNotAliased.run())::run);

        GitAsyncExtensions.discoverServers(notAliasedRemotes, myAccountsServersState, remote ->
            GitHostingUrlUtil.findServerAt(LOG, remote, uri -> {
                GithubServerPath server;
                try {
                    server = GithubServerPath.from(uri.toString());
                }
                catch (GithubParseException e) {
                    return CompletableFuture.completedFuture(null);
                }
                if (server.isGheDataResidency()) {
                    return CompletableFuture.completedFuture(server);
                }

                return GHEnterpriseServerMetadataLoader.getInstance().loadMetadata(server)
                    .handle((metadata, error) -> metadata != null && error == null ? server : null);
            }), server -> myDiscoveredServersState.getAndUpdate(it -> {
                Set<GithubServerPath> servers = new LinkedHashSet<>(it);
                servers.add(server);
                return servers;
            }), this);

        MutableObservableValue<Set<GHGitRepositoryMapping>> result =
            MutableObservableValue.of(mapRemotes(gitRemotesState.get(), myServersState.get(), myAccountsServersState.get(), aliasesState.get()));
        Runnable update = () -> result.set(mapRemotes(gitRemotesState.get(), myServersState.get(), myAccountsServersState.get(), aliasesState.get()));
        Disposer.register(this, gitRemotesState.addListener(remotes -> update.run())::run);
        Disposer.register(this, myServersState.addListener(servers -> update.run())::run);
        Disposer.register(this, myAccountsServersState.addListener(servers -> update.run())::run);
        Disposer.register(this, aliasesState.addListener(aliases -> update.run())::run);
        Disposer.register(this, result.addListener(repositories -> LOG.debug("New list of known repos: " + repositories))::run);
        return result;
    }

    private static Set<GitRemoteUrlCoordinates> filterNotAliased(Set<GitRemoteUrlCoordinates> remotes, Map<String, GithubServerPath> aliases) {
        Set<GitRemoteUrlCoordinates> result = new LinkedHashSet<>();
        for (GitRemoteUrlCoordinates remote : remotes) {
            String host = getHost(remote);
            if (host != null && !aliases.containsKey(host)) {
                result.add(remote);
            }
        }
        return result;
    }

    private static Set<GHGitRepositoryMapping> mapRemotes(Set<GitRemoteUrlCoordinates> remotes,
                                                          Set<GithubServerPath> servers,
                                                          Set<GithubServerPath> accountServers,
                                                          Map<String, GithubServerPath> aliases) {
        Set<GHGitRepositoryMapping> result = new LinkedHashSet<>();
        for (GitRemoteUrlCoordinates remote : remotes) {
            String host = getHost(remote);
            if (host == null) {
                continue;
            }
            GithubServerPath aliasServer = aliases.get(host);
            GithubServerPath server = aliasServer != null ? ServerHostAliases.findServerForAlias(accountServers, aliasServer) : null;
            if (server == null) {
                for (GithubServerPath candidate : servers) {
                    if (GitHostingUrlUtil.match(candidate.toURI(), remote.url())) {
                        server = candidate;
                        break;
                    }
                }
            }
            if (server != null) {
                GHGitRepositoryMapping mapping = GHGitRepositoryMapping.create(server, remote);
                if (mapping != null) {
                    result.add(mapping);
                }
            }
        }
        return result;
    }

    private static @Nullable String getHost(GitRemoteUrlCoordinates remote) {
        URI uri = GitHostingUrlUtil.getUriFromRemoteUrl(remote.url());
        String host = uri == null ? null : uri.getHost();
        return host == null ? null : host.toLowerCase(Locale.ROOT);
    }

    /**
     * Parses the value of the `github.aliases` setting into a map from an alias host to the GitHub server.
     * <p>
     * The value is a comma-separated list of `<alias>=<server>` and `<alias>` entries.
     * The server is a URL, such as `https://git.example.com:8443/github`, or a host.
     * An entry without a server maps the alias to {@link GithubServerPath#DEFAULT_SERVER}.
     * The function ignores an entry with an invalid server.
     */
    public static Map<String, GithubServerPath> parseGitHubHostAliases(String value) {
        return ServerHostAliases.parseServerHostAliases(value, GithubServerPath.DEFAULT_SERVER, GHHostedRepositoriesManager::parseAliasServer);
    }

    private static @Nullable GithubServerPath parseAliasServer(String value) {
        GithubServerPath server;
        try {
            server = GithubServerPath.from(StringUtil.trimTrailing(value, '/'));
        }
        catch (GithubParseException e) {
            return null;
        }
        // A host with an invalid character passes the parser, but it fails to build the URI.
        URI uri;
        try {
            uri = server.toURI();
        }
        catch (Exception e) {
            return null;
        }
        return uri.equals(GithubServerPath.DEFAULT_SERVER.toURI()) ? GithubServerPath.DEFAULT_SERVER : server;
    }

    private void updateAccountsServers() {
        Set<GithubServerPath> servers = new LinkedHashSet<>();
        servers.add(GithubServerPath.DEFAULT_SERVER);
        AccountType<?> type = myAccountManager.findAccountType(GithubAccountType.ID);
        if (type != null) {
            for (Account account : myAccountManager.getAccounts(type, myProject)) {
                servers.add(GithubAccountType.toServerPath(myAccountManager.getServerUrl(account)));
            }
        }
        myAccountsServersState.set(Set.copyOf(servers));
    }

    private void updateServers() {
        Set<GithubServerPath> servers = new LinkedHashSet<>(myAccountsServersState.get());
        servers.addAll(myDiscoveredServersState.get());
        myServersState.set(servers);
    }

    @Override
    public void dispose() {
    }
}
