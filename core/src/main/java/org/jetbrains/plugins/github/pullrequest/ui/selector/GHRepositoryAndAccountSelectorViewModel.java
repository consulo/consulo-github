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
package org.jetbrains.plugins.github.pullrequest.ui.selector;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorViewModelBase;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class GHRepositoryAndAccountSelectorViewModel extends RepositoryAndAccountSelectorViewModelBase<GHGitRepositoryMapping> {
    private final MutableObservableValue<Boolean> myGithubLoginAvailableState = MutableObservableValue.of(false);
    private final MutableObservableValue<Boolean> myGheLoginAvailableState = MutableObservableValue.of(false);

    public GHRepositoryAndAccountSelectorViewModel(Disposable scope,
                                                   Project project,
                                                   GHHostedRepositoriesManager repositoriesManager,
                                                   AccountManager accountManager,
                                                   BiFunction<GHGitRepositoryMapping, Account, CompletableFuture<?>> onSelected) {
        super(scope, project, repositoriesManager, accountManager, accountManager.findAccountType(GithubAccountType.ID), onSelected);

        Runnable update = () -> {
            GHGitRepositoryMapping repo = getRepoSelectionState().get();
            Account account = getAccountSelectionState().get();
            Boolean credsMissing = getMissingCredentialsState().get();
            myGithubLoginAvailableState.set(isGithubLoginAvailable(repo, account, credsMissing));
            myGheLoginAvailableState.set(isGheLoginVisible(repo, account, credsMissing));
        };
        Disposer.register(scope, getRepoSelectionState().addListener(it -> update.run())::run);
        Disposer.register(scope, getAccountSelectionState().addListener(it -> update.run())::run);
        Disposer.register(scope, getMissingCredentialsState().addListener(it -> update.run())::run);
        update.run();
    }

    public ObservableValue<Boolean> getGithubLoginAvailableState() {
        return myGithubLoginAvailableState;
    }

    public ObservableValue<Boolean> getGheLoginAvailableState() {
        return myGheLoginAvailableState;
    }

    private static boolean isGithubLoginAvailable(@Nullable GHGitRepositoryMapping repo, @Nullable Account account, @Nullable Boolean credsMissing) {
        if (repo == null) {
            return false;
        }
        return repo.getRepository().getServerPath().isGithubDotCom() && (account == null || Boolean.TRUE.equals(credsMissing));
    }

    private static boolean isGheLoginVisible(@Nullable GHGitRepositoryMapping repo, @Nullable Account account, @Nullable Boolean credsMissing) {
        if (repo == null) {
            return false;
        }
        return !repo.getRepository().getServerPath().isGithubDotCom() && (account == null || Boolean.TRUE.equals(credsMissing));
    }
}
