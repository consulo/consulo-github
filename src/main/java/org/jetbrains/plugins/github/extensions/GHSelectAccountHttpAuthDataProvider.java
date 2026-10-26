// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.extensions;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountBasicCredentials;
import consulo.accountManagement.AccountConnection;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.credentialStorage.AuthData;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.review.localize.ReviewLocalize;
import consulo.ui.Component;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogService;
import git4idea.remote.InteractiveGitHttpAuthDataProvider;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.authentication.ui.GithubChooseAccountDialog;
import org.jetbrains.plugins.github.util.GithubUtil;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GHSelectAccountHttpAuthDataProvider implements InteractiveGitHttpAuthDataProvider {
    private final Project myProject;
    private final Map<Account, @Nullable String> myPotentialAccounts;

    public GHSelectAccountHttpAuthDataProvider(Project project, Map<Account, @Nullable String> potentialAccounts) {
        myProject = project;
        myPotentialAccounts = potentialAccounts;
    }

    @RequiredUIAccess
    @Override
    public @Nullable AuthData getAuthData(@Nullable Component parentComponent) {
        getAuthDataAsync(parentComponent);
        return null;
    }

    @RequiredUIAccess
    public CompletableFuture<@Nullable AuthData> getAuthDataAsync(@Nullable Component parentComponent) {
        UIAccess uiAccess = UIAccess.current();
        GithubChooseAccountDialog dialog = new GithubChooseAccountDialog(
            myPotentialAccounts.keySet(), LocalizeValue.empty(), false, true,
            GithubLocalize.accountChooseTitle(), ReviewLocalize.cloneDialogButtonLogin()
        );
        DialogService dialogService = myProject.getApplication().getInstance(DialogService.class);
        return (parentComponent == null ? dialogService.build(dialog) : dialogService.build(parentComponent, dialog))
            .showAsync()
            .thenComposeAsync(value -> {
                Account account = dialog.getAccount();
                if (account == null) {
                    return CompletableFuture.<@Nullable AuthData>completedFuture(null);
                }
                return getToken(account, parentComponent).<@Nullable AuthData>thenApply(token -> {
                    if (token == null) {
                        return null;
                    }
                    if (dialog.isSetDefault()) {
                        AccountManager accountManager = accountManager();
                        accountManager.setActiveAccount(accountType(accountManager), account, myProject);
                    }
                    return new AuthData(GithubUtil.GIT_AUTH_PASSWORD_SUBSTITUTE, token);
                });
            }, uiAccess)
            .exceptionally(e -> null);
    }

    @RequiredUIAccess
    private CompletableFuture<@Nullable String> getToken(Account account, @Nullable Component parentComponent) {
        String token = myPotentialAccounts.get(account);
        if (token != null) {
            return CompletableFuture.completedFuture(token);
        }
        AccountManager accountManager = accountManager();
        return accountManager.requestAuthorization(account, null, myProject, parentComponent)
            .thenCompose(authorized -> {
                if (authorized == null) {
                    return CompletableFuture.<@Nullable String>completedFuture(null);
                }
                return accountManager.connect(authorized).<@Nullable String>thenApply(GHSelectAccountHttpAuthDataProvider::getPassword);
            });
    }

    private static @Nullable String getPassword(AccountConnection connection) {
        AccountBasicCredentials credentials = connection.getBasicCredentials();
        return credentials == null ? null : credentials.password();
    }

    private AccountManager accountManager() {
        return myProject.getApplication().getInstance(AccountManager.class);
    }

    private static AccountType<?> accountType(AccountManager accountManager) {
        return accountManager.findAccountType(GithubAccountType.ID);
    }
}
