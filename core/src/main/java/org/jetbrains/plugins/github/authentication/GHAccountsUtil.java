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
package org.jetbrains.plugins.github.authentication;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountAuthorizeConfig;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.application.Application;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.review.localize.ReviewLocalize;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.DumbAwareAction;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.authentication.accounts.GithubProjectDefaultAccountHolder;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class GHAccountsUtil {
    private GHAccountsUtil() {
    }

    public static AccountManager getAccountManager() {
        return Application.get().getInstance(AccountManager.class);
    }

    public static @Nullable AccountType<?> getAccountType() {
        return getAccountManager().findAccountType(GithubAccountType.ID);
    }

    public static List<Account> getAccounts(@Nullable Project project) {
        AccountType<?> type = getAccountType();
        return type == null ? List.of() : getAccountManager().getAccounts(type, project);
    }

    public static @Nullable Account getDefaultAccount(Project project) {
        return GithubProjectDefaultAccountHolder.getInstance(project).getAccount();
    }

    public static void setDefaultAccount(Project project, @Nullable Account account) {
        GithubProjectDefaultAccountHolder.getInstance(project).setAccount(account);
    }

    public static @Nullable Account getSingleOrDefaultAccount(Project project) {
        Account defaultAccount = getDefaultAccount(project);
        if (defaultAccount != null) {
            return defaultAccount;
        }
        List<Account> accounts = getAccounts(project);
        return accounts.size() == 1 ? accounts.get(0) : null;
    }

    /**
     * Ask the user to log into GitHub with a new account
     *
     * @param server          the target server if needed
     * @param project         parent project for the login dialog
     * @param parentComponent parent component for the login dialog
     * @param authType        type of authorization to use
     * @return a new account if the login was successful, `null` otherwise
     */
    @RequiredUIAccess
    public static CompletableFuture<@Nullable Account> logIn(@Nullable GithubServerPath server,
                                                             @Nullable Project project,
                                                             @Nullable Component parentComponent,
                                                             AuthorizationType authType) {
        AccountType<?> type = getAccountType();
        if (type == null) {
            return CompletableFuture.completedFuture(null);
        }
        AccountManager accountManager = getAccountManager();
        AccountAuthorizeConfig config = findAuthorizeConfig(accountManager, type, server, authType);
        return accountManager.requestLogin(type, config, project, parentComponent);
    }

    /**
     * Ask the user to re-login into the [account]
     *
     * @param project         parent project for the login dialog
     * @param parentComponent parent component for the login dialog
     * @return the account if the re-login was successful, `null` otherwise
     */
    @RequiredUIAccess
    public static CompletableFuture<@Nullable Account> reLogIn(Account account,
                                                               @Nullable Project project,
                                                               @Nullable Component parentComponent) {
        return getAccountManager().requestAuthorization(account, null, project, parentComponent);
    }

    private static @Nullable AccountAuthorizeConfig findAuthorizeConfig(AccountManager accountManager,
                                                                         AccountType<?> type,
                                                                         @Nullable GithubServerPath server,
                                                                         AuthorizationType authType) {
        List<? extends AccountAuthorizeConfig> configs = accountManager.getAuthorizeConfigs(type);
        if (server != null && !server.equals(GithubServerPath.DEFAULT_SERVER)) {
            return GithubAccountType.findAuthorizeConfig(configs, AuthorizationType.TOKEN);
        }
        return switch (authType) {
            case OAUTH -> {
                AccountAuthorizeConfig oauth = GithubAccountType.findAuthorizeConfig(configs, AuthorizationType.OAUTH);
                yield oauth != null ? oauth : GithubAccountType.findAuthorizeConfig(configs, AuthorizationType.TOKEN);
            }
            case TOKEN -> GithubAccountType.findAuthorizeConfig(configs, AuthorizationType.TOKEN);
            case UNDEFINED -> null;
        };
    }

    public static ActionGroup createAddAccountActionGroup(@Nullable Project project,
                                                          @Nullable Component parentComponent,
                                                          Consumer<@Nullable Account> onLogin) {
        DefaultActionGroup group = new DefaultActionGroup();
        if (GHOAuthService.isAvailable()) {
            group.add(createLoginAction(GithubLocalize.actionGithubAccountsAddghaccountText(),
                GithubServerPath.DEFAULT_SERVER, AuthorizationType.OAUTH, project, parentComponent, onLogin));
        }
        group.add(createLoginAction(ReviewLocalize.accountsActionAddAccountWithToken(),
            GithubServerPath.DEFAULT_SERVER, AuthorizationType.TOKEN, project, parentComponent, onLogin));
        group.add(AnSeparator.create());
        group.add(createLoginAction(GithubLocalize.actionGithubAccountsAddgheaccountText(),
            null, AuthorizationType.TOKEN, project, parentComponent, onLogin));
        return group;
    }

    private static DumbAwareAction createLoginAction(LocalizeValue text,
                                                     @Nullable GithubServerPath server,
                                                     AuthorizationType authType,
                                                     @Nullable Project project,
                                                     @Nullable Component parentComponent,
                                                     Consumer<@Nullable Account> onLogin) {
        return DumbAwareAction.create(text, e -> logIn(server, project, parentComponent, authType)
            .whenComplete((account, error) -> {
                if (error == null && account != null) {
                    onLogin.accept(account);
                }
            }));
    }
}
