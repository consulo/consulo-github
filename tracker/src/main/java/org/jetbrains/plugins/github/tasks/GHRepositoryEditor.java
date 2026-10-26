// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.tasks;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountAuthorizeConfig;
import consulo.accountManagement.AccountBasicCredentials;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.accountManagement.authorize.TokenAuthorizeConfig;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.exceptions.GithubParseException;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

final class GHRepositoryEditor {
    private GHRepositoryEditor() {
    }

    @RequiredUIAccess
    static CompletableFuture<@Nullable String> askToken(Project project, String host) {
        GithubServerPath server = tryParse(host);
        if (server == null) {
            return CompletableFuture.completedFuture(null);
        }

        AccountManager accountManager = project.getApplication().getInstance(AccountManager.class);
        AccountType<?> type = accountManager.findAccountType(GithubAccountType.ID);
        if (type == null) {
            return CompletableFuture.completedFuture(null);
        }

        @Nullable AccountAuthorizeConfig config = server.isGithubDotCom() ? null : findTokenConfig(accountManager, type);
        return accountManager.requestLogin(type, config, project, null)
            .thenCompose(account -> account == null
                ? CompletableFuture.<@Nullable String>completedFuture(null)
                : loadToken(accountManager, account));
    }

    private static CompletableFuture<@Nullable String> loadToken(AccountManager accountManager, Account account) {
        return accountManager.connect(account).thenApply(connection -> {
            AccountBasicCredentials credentials = connection.getBasicCredentials();
            return credentials == null ? null : credentials.password();
        });
    }

    private static @Nullable AccountAuthorizeConfig findTokenConfig(AccountManager accountManager, AccountType<?> type) {
        for (AccountAuthorizeConfig config : accountManager.getAuthorizeConfigs(type)) {
            if (config instanceof TokenAuthorizeConfig) {
                return config;
            }
        }
        return null;
    }

    private static @Nullable GithubServerPath tryParse(String host) {
        try {
            return GithubServerPath.from(host);
        }
        catch (GithubParseException ignored) {
            return null;
        }
    }
}
