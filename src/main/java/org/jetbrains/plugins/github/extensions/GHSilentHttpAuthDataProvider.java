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
import consulo.logging.Logger;
import consulo.project.Project;
import git4idea.remote.hosting.http.HostedGitAuthenticationFailureManager;
import git4idea.remote.hosting.http.SilentHostedGitHttpAuthDataProviderBase;
import jakarta.inject.Inject;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser;
import org.jetbrains.plugins.github.authentication.accounts.GHCachingAccountInformationProvider;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@ExtensionImpl(id = "GitHub.Silent", order = "before GitHub")
public class GHSilentHttpAuthDataProvider extends SilentHostedGitHttpAuthDataProviderBase {
    private static final Logger LOG = Logger.getInstance(GHSilentHttpAuthDataProvider.class);

    private final AccountManager myAccountManager;

    @Inject
    public GHSilentHttpAuthDataProvider(AccountManager accountManager) {
        myAccountManager = accountManager;
    }

    @Override
    protected String getProviderId() {
        return "GitHub Plugin";
    }

    @Override
    protected AccountManager getAccountManager() {
        return myAccountManager;
    }

    @Override
    protected AccountType<?> getAccountType() {
        return myAccountManager.findAccountType(GithubAccountType.ID);
    }

    @Override
    protected HostedGitAuthenticationFailureManager getAuthFailureManager(Project project) {
        return GHGitAuthenticationFailureManager.getInstance(project);
    }

    @Override
    protected CompletableFuture<@Nullable AuthData> getAuthData(Account account) {
        return myAccountManager.connect(account).thenCompose(connection -> {
            AccountBasicCredentials credentials = connection.getBasicCredentials();
            if (credentials == null) {
                return CompletableFuture.<@Nullable AuthData>completedFuture(null);
            }
            String token = credentials.password();
            return getAccountDetails(account).<@Nullable AuthData>thenApply(details -> details == null ? null : new AuthData(details.getLogin(), token));
        });
    }

    @Override
    public LocalizeValue getPresentableErrorMessage(List<String> errorOutput) {
        return GHGitErrorMessagesUtils.oauthRestrictionMessage(errorOutput);
    }

    public static CompletableFuture<Map<Account, @Nullable String>> getAccountsWithTokens(Project project, String url) {
        AccountManager accountManager = project.getApplication().getInstance(AccountManager.class);
        HostedGitAuthenticationFailureManager authFailureManager = GHGitAuthenticationFailureManager.getInstance(project);

        return getAccountsWithTokens(accountManager, accountManager.findAccountType(GithubAccountType.ID), project, authFailureManager, url)
            .thenApply(accounts -> {
                Map<Account, @Nullable String> result = new LinkedHashMap<>();
                accounts.forEach((account, credentials) -> result.put(account, credentials == null ? null : credentials.password()));
                return result;
            });
    }

    public static CompletableFuture<@Nullable GithubAuthenticatedUser> getAccountDetails(Account account) {
        GithubApiRequestExecutor executor = GithubApiRequestExecutorFactory.getInstance().create(account);
        return GHCachingAccountInformationProvider.getInstance().loadInformation(executor, account)
            .<@Nullable GithubAuthenticatedUser>thenApply(user -> user)
            .exceptionally(e -> {
                Throwable error = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                if (!(error instanceof ProcessCanceledException)) {
                    LOG.info("Cannot load details for " + account.getName(), error);
                }
                return null;
            });
    }
}
