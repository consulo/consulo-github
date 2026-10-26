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
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.annotation.component.ExtensionImpl;
import consulo.component.ProcessCanceledException;
import consulo.credentialStorage.AuthData;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import git4idea.remote.GitHttpAuthDataProvider;
import git4idea.remote.hosting.GitHostingUrlUtil;
import jakarta.inject.Inject;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.util.GithubUtil;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.jetbrains.plugins.github.api.GithubServerPath.DEFAULT_SERVER;

@ExtensionImpl(id = "GitHub")
public class GHComHttpAuthDataProvider implements GitHttpAuthDataProvider {
    private final AccountManager myAccountManager;

    @Inject
    public GHComHttpAuthDataProvider(AccountManager accountManager) {
        myAccountManager = accountManager;
    }

    @Override
    public boolean isSilent() {
        return false;
    }

    @Override
    public @Nullable AuthData getAuthData(Project project, String url, String login) {
        if (!GitHostingUrlUtil.matchHost(DEFAULT_SERVER.toURI(), url)) {
            return null;
        }

        return getAuthDataOrCancel(project, url, login);
    }

    @Override
    public @Nullable AuthData getAuthData(Project project, String url) {
        if (!GitHostingUrlUtil.matchHost(DEFAULT_SERVER.toURI(), url)) {
            return null;
        }

        return getAuthDataOrCancel(project, url, null);
    }

    @Override
    public LocalizeValue getPresentableErrorMessage(List<String> errorOutput) {
        return GHGitErrorMessagesUtils.oauthRestrictionMessage(errorOutput);
    }

    private AuthData getAuthDataOrCancel(Project project, String url, @Nullable String login) {
        AccountType<?> accountType = myAccountManager.findAccountType(GithubAccountType.ID);
        Map<Account, @Nullable String> accountsWithTokens = new LinkedHashMap<>();
        for (Account account : myAccountManager.getAccounts(accountType, project)) {
            String serverUrl = myAccountManager.getServerUrl(account);
            if (serverUrl != null && GitHostingUrlUtil.matchHost(GithubAccountType.toServerPath(serverUrl).toURI(), url)) {
                accountsWithTokens.put(account, findToken(account));
            }
        }

        CompletableFuture<@Nullable AuthData> authData = CompletableFuture.supplyAsync(() -> {
            if (accountsWithTokens.isEmpty()) {
                return myAccountManager.requestLogin(accountType, null, project, null).thenCompose(this::toAuthData);
            }
            if (accountsWithTokens.size() == 1) {
                Account account = accountsWithTokens.keySet().iterator().next();
                return myAccountManager.requestAuthorization(account, null, project, null).thenCompose(this::toAuthData);
            }
            return new GHSelectAccountHttpAuthDataProvider(project, accountsWithTokens).getAuthDataAsync(null);
        }, project.getUIAccess()).thenCompose(it -> it);

        AuthData result;
        try {
            result = authData.join();
        }
        catch (CompletionException e) {
            throw new ProcessCanceledException();
        }
        if (result == null) {
            throw new ProcessCanceledException();
        }
        return result;
    }

    private @Nullable String findToken(Account account) {
        try {
            AccountBasicCredentials credentials = myAccountManager.connect(account).join().getBasicCredentials();
            return credentials == null ? null : credentials.password();
        }
        catch (CompletionException e) {
            return null;
        }
    }

    private CompletableFuture<@Nullable AuthData> toAuthData(@Nullable Account account) {
        if (account == null) {
            return CompletableFuture.completedFuture(null);
        }
        return myAccountManager.connect(account).<@Nullable AuthData>thenApply(connection -> {
            AccountBasicCredentials credentials = connection.getBasicCredentials();
            if (credentials == null) {
                return null;
            }
            String login = account.getLogin();
            return new AuthData(login != null ? login : GithubUtil.GIT_AUTH_PASSWORD_SUBSTITUTE, credentials.password());
        });
    }
}
