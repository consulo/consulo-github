// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountListener;
import consulo.accountManagement.AccountManager;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.http.client.URIUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.util.FutureUtil;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.Pair;
import consulo.util.lang.lazy.LazyValue;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.GHRepositoryConnection;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.pullrequest.ui.selector.GHRepositoryAndAccountSelectorViewModel;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GHPRProjectViewModel implements Disposable {
    private static final Logger LOG = Logger.getInstance(GHPRProjectViewModel.class);

    private static final Duration PREFERRED_REPOSITORY_MAPPING_TIMEOUT = Duration.ofMinutes(1);

    private final Project myProject;
    private final AccountManager myAccountManager;
    private final GHHostedRepositoriesManager myRepositoriesManager;
    private final GHRepositoryConnectionManager myConnectionManager;
    private final GithubPullRequestsProjectUISettings mySettings;

    private final ObservableValue<Boolean> myIsAvailable;
    private final List<Runnable> myActivationRequests = new CopyOnWriteArrayList<>();

    private final MutableObservableValue<@Nullable Pair<GHGitRepositoryMapping, Account>> mySingleRepoAndAccountState = MutableObservableValue.of(null);

    private final LazyValue<GHRepositoryAndAccountSelectorViewModel> mySelectorVm;
    private final LazyValue<ObservableValue<@Nullable GHPRConnectedProjectViewModel>> myConnectedProjectVm;

    @Inject
    public GHPRProjectViewModel(Project project, AccountManager accountManager) {
        myProject = project;
        myAccountManager = accountManager;
        myRepositoriesManager = GHHostedRepositoriesManager.getInstance(project);
        myConnectionManager = GHRepositoryConnectionManager.getInstance(project);
        mySettings = GithubPullRequestsProjectUISettings.getInstance(project);

        myIsAvailable = myRepositoriesManager.getKnownRepositoriesState().map(it -> !it.isEmpty());

        Runnable updateSingle = () -> mySingleRepoAndAccountState.set(calcSingleRepoAndAccount());
        Disposer.register(this, myRepositoriesManager.getKnownRepositoriesState().addListener(it -> updateSingle.run())::run);
        accountManager.addListener(new AccountListener() {
            @Override
            public void accountsChanged() {
                updateSingle.run();
            }
        }, this);
        updateSingle.run();

        mySelectorVm = LazyValue.atomicNotNull(this::createSelectorVm);
        myConnectedProjectVm = LazyValue.atomicNotNull(this::createConnectedProjectVm);
    }

    public static GHPRProjectViewModel getInstance(Project project) {
        return project.getInstance(GHPRProjectViewModel.class);
    }

    private @Nullable Pair<GHGitRepositoryMapping, Account> calcSingleRepoAndAccount() {
        Set<GHGitRepositoryMapping> repos = myRepositoriesManager.getKnownRepositoriesState().get();
        if (repos.size() != 1) {
            return null;
        }
        GHGitRepositoryMapping repo = repos.iterator().next();
        Account found = null;
        for (Account account : myAccountManager.getAccounts(myAccountManager.findAccountType(GithubAccountType.ID), myProject)) {
            URI accountUri = toUri(myAccountManager.getServerUrl(account));
            if (accountUri != null && URIUtil.equalWithoutSchema(accountUri, repo.getRepository().getServerPath().toURI())) {
                if (found != null) {
                    return null;
                }
                found = account;
            }
        }
        return found == null ? null : Pair.create(repo, found);
    }

    private static @Nullable URI toUri(@Nullable String url) {
        if (url == null) {
            return null;
        }
        try {
            return new URI(url);
        }
        catch (URISyntaxException e) {
            return null;
        }
    }

    public ObservableValue<Boolean> isAvailable() {
        return myIsAvailable;
    }

    public void addActivationRequestListener(Disposable parentDisposable, Runnable listener) {
        myActivationRequests.add(listener);
        Disposer.register(parentDisposable, () -> myActivationRequests.remove(listener));
    }

    public GHRepositoryAndAccountSelectorViewModel getSelectorVm() {
        return mySelectorVm.get();
    }

    private GHRepositoryAndAccountSelectorViewModel createSelectorVm() {
        GHRepositoryAndAccountSelectorViewModel vm =
            new GHRepositoryAndAccountSelectorViewModel(this, myProject, myRepositoriesManager, myAccountManager, this::connect);

        Runnable restoreSelection = () -> {
            if (myConnectionManager.getConnectionState().get() != null) {
                return;
            }
            Pair<String, Account> urlAndAccount = mySettings.getSelectedUrlAndAccount();
            if (urlAndAccount == null) {
                return;
            }
            GHGitRepositoryMapping repo = myRepositoriesManager.getKnownRepositoriesState().get().stream()
                .filter(it -> it.getRemote().url().equals(urlAndAccount.getFirst()))
                .findFirst()
                .orElse(null);
            if (repo == null) {
                return;
            }
            vm.getRepoSelectionState().set(repo);
            vm.getAccountSelectionState().set(urlAndAccount.getSecond());
            vm.submitSelection();
        };
        Disposer.register(this, myRepositoriesManager.getKnownRepositoriesState().addListener(repos -> restoreSelection.run())::run);
        restoreSelection.run();

        //TODO: only do when UI is showing
        Runnable connectSingle = () -> {
            Pair<GHGitRepositoryMapping, Account> it = mySingleRepoAndAccountState.get();
            // Skip once connected, else a later re-emission would reconnect and cancel in-flight work.
            if (it != null && myConnectionManager.getConnectionState().get() == null) {
                vm.getRepoSelectionState().set(it.getFirst());
                vm.getAccountSelectionState().set(it.getSecond());
                vm.submitSelection();
            }
        };
        Disposer.register(this, mySingleRepoAndAccountState.addListener(it -> connectSingle.run())::run);
        connectSingle.run();
        return vm;
    }

    private CompletableFuture<@Nullable GHRepositoryConnection> connect(GHGitRepositoryMapping repo, Account account) {
        // Compare by repository coordinates, not mapping equality: the same repo can re-resolve to a
        // structurally different mapping (e.g. after git sync), which would otherwise trigger a needless
        // reconnect that cancels in-flight work (e.g. a diff load).
        GHRepositoryConnection current = myConnectionManager.getConnectionState().get();
        if (current != null && current.getRepo().getRepository().equals(repo.getRepository()) && current.getAccount().getId().equals(account.getId())) {
            return CompletableFuture.completedFuture(current);
        }
        return myConnectionManager.openConnection(repo, account).whenComplete((connection, error) -> {
            if (error == null) {
                mySettings.setSelectedUrlAndAccount(Pair.create(repo.getRemote().url(), account));
            }
        });
    }

    public ObservableValue<@Nullable GHPRConnectedProjectViewModel> getConnectedProjectVm() {
        return myConnectedProjectVm.get();
    }

    private ObservableValue<@Nullable GHPRConnectedProjectViewModel> createConnectedProjectVm() {
        GHPRConnectedProjectViewModelFactory vmFactory = myProject.getInstance(GHPRConnectedProjectViewModelFactory.class);
        MutableObservableValue<@Nullable GHPRConnectedProjectViewModel> result = MutableObservableValue.of(null);
        Disposable[] scope = new Disposable[1];
        Consumer<@Nullable GHRepositoryConnection> update = connection -> {
            synchronized (scope) {
                if (scope[0] != null) {
                    Disposer.dispose(scope[0]);
                    scope[0] = null;
                }
                if (connection == null) {
                    result.set(null);
                    return;
                }
                Disposable vmScope = Disposable.newDisposable("GHPRConnectedProjectViewModel");
                Disposer.register(connection.getScope(), vmScope);
                scope[0] = vmScope;
                result.set(vmFactory.create(myProject, vmScope, connection, this::activate));
            }
        };
        Disposer.register(this, myConnectionManager.getConnectionState().addListener(update)::run);
        update.accept(myConnectionManager.getConnectionState().get());
        return result;
    }

    public boolean canResetRemoteOrAccount() {
        return myConnectionManager.getConnectionState().get() != null && mySingleRepoAndAccountState.get() == null;
    }

    public void resetRemoteAndAccount() {
        mySettings.setSelectedUrlAndAccount(null);
        myConnectionManager.closeConnection();
    }

    public void activate() {
        myActivationRequests.forEach(Runnable::run);
    }

    public void activateAndAwaitProject(Consumer<GHPRConnectedProjectViewModel> action) {
        activateAndAwaitProject(null, action);
    }

    /**
     * @param preferredRepoAndAccount connects directly to this repository and account.
     * The {@link #getSelectorVm()} heuristics need exactly one known repository and account, and a new pull request worktree can have more.
     * {@code action} always runs against the view model for {@code preferredRepoAndAccount}.
     * When no mapping for the repository appears within {@code mappingTimeout}, {@code action} does not run.
     * When the connection closes or changes before its view model appears, {@code action} does not run.
     */
    public void activateAndAwaitProject(@Nullable Pair<GHRepositoryCoordinates, Account> preferredRepoAndAccount,
                                        Consumer<GHPRConnectedProjectViewModel> action) {
        activateAndAwaitProject(preferredRepoAndAccount, PREFERRED_REPOSITORY_MAPPING_TIMEOUT, action);
    }

    public void activateAndAwaitProject(@Nullable Pair<GHRepositoryCoordinates, Account> preferredRepoAndAccount,
                                        Duration mappingTimeout,
                                        Consumer<GHPRConnectedProjectViewModel> action) {
        doActivateAndAwaitProject(preferredRepoAndAccount, mappingTimeout, action);
    }

    /**
     * Does the same as {@link #activateAndAwaitProject} and returns the future of the activation.
     */
    CompletableFuture<?> doActivateAndAwaitProject(@Nullable Pair<GHRepositoryCoordinates, Account> preferredRepoAndAccount,
                                                   Duration mappingTimeout,
                                                   Consumer<GHPRConnectedProjectViewModel> action) {
        activate();
        if (preferredRepoAndAccount == null) {
            return FutureUtil.first(this, getConnectedProjectVm(), Objects::nonNull)
                .thenAcceptAsync(action, myProject.getUIAccess());
        }

        GHRepositoryCoordinates expectedRepository = preferredRepoAndAccount.getFirst();
        Account account = preferredRepoAndAccount.getSecond();
        return FutureUtil.first(this, myRepositoriesManager.getKnownRepositoriesState(),
                repos -> repos.stream().anyMatch(it -> it.getRepository().equals(expectedRepository)))
            .orTimeout(mappingTimeout.toMillis(), TimeUnit.MILLISECONDS)
            .handle((repos, error) -> {
                if (error != null) {
                    if (FutureUtil.unwrap(error) instanceof TimeoutException) {
                        LOG.warn("No Git remote for GitHub repository " + expectedRepository + " appeared in " + mappingTimeout +
                            ", skipping the requested action");
                    }
                    return null;
                }
                return repos.stream().filter(it -> it.getRepository().equals(expectedRepository)).findFirst().orElse(null);
            })
            .thenCompose(mapping -> mapping == null ? CompletableFuture.completedFuture(null) : connect(mapping, account))
            .thenCompose(connection -> {
                if (connection == null) {
                    return CompletableFuture.completedFuture(null);
                }
                ObservableValue<Pair<@Nullable GHRepositoryConnection, @Nullable GHPRConnectedProjectViewModel>> state =
                    ObservableValue.combine(myConnectionManager.getConnectionState(), getConnectedProjectVm(), Pair::create);
                return FutureUtil.first(this, state, pair -> {
                    GHPRConnectedProjectViewModel vm = pair.getSecond();
                    return pair.getFirst() != connection || (vm != null && vm.getConnectionId().equals(connection.getId()));
                }).thenAcceptAsync(pair -> {
                    GHPRConnectedProjectViewModel vm = pair.getSecond();
                    if (pair.getFirst() == connection && vm != null) {
                        action.accept(vm);
                    }
                }, myProject.getUIAccess());
            });
    }

    public void loginIfPossible() {
        // init selector, so that auto-login is done automatically
        getSelectorVm();
    }

    @Override
    public void dispose() {
    }
}
