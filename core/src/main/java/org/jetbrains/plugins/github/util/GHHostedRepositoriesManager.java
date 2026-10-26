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
import consulo.project.Project;
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

import java.util.LinkedHashSet;
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

        GitAsyncExtensions.discoverServers(gitRemotesState, myAccountsServersState, remote ->
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

        ObservableValue<Set<GHGitRepositoryMapping>> result =
            GitAsyncExtensions.mapToServers(gitRemotesState, myServersState, GHGitRepositoryMapping::create, this);
        Disposer.register(this, result.addListener(repositories -> LOG.debug("New list of known repos: " + repositories))::run);
        return result;
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
