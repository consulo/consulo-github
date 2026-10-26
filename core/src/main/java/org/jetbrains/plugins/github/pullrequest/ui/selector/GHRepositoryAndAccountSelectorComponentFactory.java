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
package org.jetbrains.plugins.github.pullrequest.ui.selector;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountAuthorizeConfig;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.accountManagement.authorize.TokenAuthorizeConfig;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.Button;
import consulo.ui.Component;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.concurrent.coroutine.ObservableValue;
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorComponentFactory;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.ui.util.GHUIUtil;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class GHRepositoryAndAccountSelectorComponentFactory {
    private final Project myProject;
    private final GHRepositoryAndAccountSelectorViewModel myVm;
    private final AccountManager myAccountManager;

    public GHRepositoryAndAccountSelectorComponentFactory(Project project, GHRepositoryAndAccountSelectorViewModel vm, AccountManager accountManager) {
        myProject = project;
        myVm = vm;
        myAccountManager = accountManager;
    }

    @RequiredUIAccess
    public Component create(Disposable scope) {
        GHSelectorErrorStatusPresenter errorPresenter = new GHSelectorErrorStatusPresenter(myProject, myVm::submitSelection);

        return new RepositoryAndAccountSelectorComponentFactory<>(myVm).create(
            scope,
            mapping -> {
                List<GHRepositoryCoordinates> allRepositories = myVm.getRepositoriesState().get().stream().map(GHGitRepositoryMapping::getRepository).toList();
                return LocalizeValue.of(GHUIUtil.getRepositoryDisplayName(allRepositories, mapping.getRepository(), true));
            },
            GithubLocalize.pullRequestViewList(),
            createLoginButtons(scope),
            errorPresenter
        );
    }

    @RequiredUIAccess
    private List<Button> createLoginButtons(Disposable scope) {
        Button githubLogin = Button.create(GithubLocalize.actionGithubAccountsAddghaccountText(), e -> loginToGithub(false, null));
        bindVisibility(scope, githubLogin, myVm.getGithubLoginAvailableState());
        bindDisabled(scope, githubLogin, myVm.getBusyState());

        Button tokenLogin = Button.create(GithubLocalize.actionGithubAccountsAddghaccountwithtokenText(), e -> loginToGithub(false, findTokenConfig()));
        bindVisibility(scope, tokenLogin, myVm.getGithubLoginAvailableState());
        bindDisabled(scope, tokenLogin, myVm.getBusyState());

        Button gheLogin = Button.create(GithubLocalize.actionGithubAccountsAddgheaccountText(), e -> {
            GHGitRepositoryMapping repo = myVm.getRepoSelectionState().get();
            if (repo != null) {
                loginToGhe(false, repo);
            }
        });
        bindVisibility(scope, gheLogin, myVm.getGheLoginAvailableState());
        bindDisabled(scope, gheLogin, myVm.getBusyState());

        return List.of(githubLogin, tokenLogin, gheLogin);
    }

    private @Nullable AccountAuthorizeConfig findTokenConfig() {
        for (AccountAuthorizeConfig config : myAccountManager.getAuthorizeConfigs(accountType())) {
            if (config instanceof TokenAuthorizeConfig) {
                return config;
            }
        }
        return null;
    }

    private AccountType<?> accountType() {
        return myAccountManager.findAccountType(GithubAccountType.ID);
    }

    @RequiredUIAccess
    private void loginToGithub(boolean forceNew, @Nullable AccountAuthorizeConfig config) {
        Account account = myVm.getAccountSelectionState().get();
        if (account == null || forceNew) {
            myAccountManager.requestLogin(accountType(), config, myProject, null).whenComplete((it, e) -> afterLogin(it, true));
        }
        else if (Boolean.TRUE.equals(myVm.getMissingCredentialsState().get())) {
            myAccountManager.requestAuthorization(account, null, myProject, null).whenComplete((it, e) -> afterLogin(it, false));
        }
    }

    @RequiredUIAccess
    private void loginToGhe(boolean forceNew, GHGitRepositoryMapping repo) {
        Account account = myVm.getAccountSelectionState().get();
        if (account == null || forceNew) {
            myAccountManager.requestLogin(accountType(), findTokenConfig(), myProject, null).whenComplete((it, e) -> afterLogin(it, true));
        }
        else if (Boolean.TRUE.equals(myVm.getMissingCredentialsState().get())) {
            myAccountManager.requestAuthorization(account, null, myProject, null).whenComplete((it, e) -> afterLogin(it, false));
        }
    }

    private void afterLogin(@Nullable Account account, boolean select) {
        if (account == null) {
            return;
        }
        myProject.getUIAccess().give(() -> {
            if (select) {
                myVm.getAccountSelectionState().set(account);
            }
            myVm.submitSelection();
        });
    }

    private static void bindVisibility(Disposable scope, Component component, ObservableValue<Boolean> state) {
        bind(scope, state, component::setVisible);
    }

    private static void bindDisabled(Disposable scope, Component component, ObservableValue<Boolean> state) {
        bind(scope, state, busy -> component.setEnabled(!busy));
    }

    private static void bind(Disposable scope, ObservableValue<Boolean> state, Consumer<Boolean> consumer) {
        UIAccess uiAccess = UIAccess.current();
        consumer.accept(state.get());
        Disposer.register(scope, state.addListener(value -> uiAccess.give(() -> consumer.accept(value)))::run);
    }
}
