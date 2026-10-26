// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountConnection;
import consulo.accountManagement.AccountListener;
import consulo.accountManagement.AccountManager;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.util.concurrent.coroutine.ObservableValue;
import git4idea.remote.hosting.SingleHostedGitRepositoryConnectionManager;
import git4idea.remote.hosting.SingleHostedGitRepositoryConnectionManagerImpl;
import git4idea.remote.hosting.ValidatingHostedGitRepositoryConnectionFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.GHRepositoryConnection;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContextRepository;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GHRepositoryConnectionManager implements SingleHostedGitRepositoryConnectionManager<GHGitRepositoryMapping, GHRepositoryConnection>, Disposable {
    private final Project myProject;
    private final AccountManager myAccountManager;
    private final SingleHostedGitRepositoryConnectionManagerImpl<GHGitRepositoryMapping, GHRepositoryConnection> myDelegate;

    @Inject
    public GHRepositoryConnectionManager(Project project, AccountManager accountManager) {
        myProject = project;
        myAccountManager = accountManager;

        ValidatingHostedGitRepositoryConnectionFactory<GHGitRepositoryMapping, GHRepositoryConnection> connectionFactory =
            new ValidatingHostedGitRepositoryConnectionFactory<>(() -> GHHostedRepositoriesManager.getInstance(myProject), () -> myAccountManager,
                project, this::createConnection);
        myDelegate = new SingleHostedGitRepositoryConnectionManagerImpl<>(this, connectionFactory);

        accountManager.addListener(new AccountListener() {
            @Override
            public void accountsChanged() {
                GHRepositoryConnection current = getConnectionState().get();
                Account currentAccount = current == null ? null : current.getAccount();
                if (currentAccount != null && myAccountManager.findAccount(currentAccount.getId(), myProject) == null) {
                    closeConnection();
                }
            }
        }, this);
    }

    public static GHRepositoryConnectionManager getInstance(Project project) {
        return project.getInstance(GHRepositoryConnectionManager.class);
    }

    @Override
    public ObservableValue<@Nullable GHRepositoryConnection> getConnectionState() {
        return myDelegate.getConnectionState();
    }

    private CompletableFuture<GHRepositoryConnection> createConnection(Disposable connectionScope,
                                                                       GHGitRepositoryMapping repo,
                                                                       Account account,
                                                                       ObservableValue<AccountConnection> tokenState) {
        GithubApiRequestExecutor executor = GithubApiRequestExecutorFactory.getInstance()
            .create(GithubAccountType.toServerPath(myAccountManager.getServerUrl(account)), tokenState::get);

        GHPRDataContextRepository dataContextRepository = GHPRDataContextRepository.getInstance(myProject);
        return dataContextRepository.getContext(repo.getRepository(), repo.getRemote(), account, executor).thenApply(dataContext -> {
            Disposer.register(connectionScope, () -> dataContextRepository.clearContext(repo.getRepository()));
            return new GHRepositoryConnection(connectionScope, repo, account, dataContext);
        });
    }

    @Override
    public CompletableFuture<@Nullable GHRepositoryConnection> openConnection(GHGitRepositoryMapping repo, Account account) {
        return myDelegate.openConnection(repo, account);
    }

    @Override
    public CompletableFuture<?> closeConnection() {
        return myDelegate.closeConnection();
    }

    @Override
    public void dispose() {
    }
}
