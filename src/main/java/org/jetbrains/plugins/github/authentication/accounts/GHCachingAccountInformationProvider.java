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
package org.jetbrains.plugins.github.authentication.accounts;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import consulo.accountManagement.Account;
import consulo.accountManagement.AccountListener;
import consulo.accountManagement.AccountManager;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.disposer.Disposable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Loads the account information or provides it from cache
 */
@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class GHCachingAccountInformationProvider implements Disposable {
    private final AccountManager myAccountManager;

    private final Cache<String, CompletableFuture<GithubAuthenticatedUser>> myInformationCache = CacheBuilder.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(30))
        .build();

    @Inject
    public GHCachingAccountInformationProvider(AccountManager accountManager) {
        myAccountManager = accountManager;
        accountManager.addListener(new AccountListener() {
            @Override
            public void accountsChanged() {
                myInformationCache.invalidateAll();
            }
        }, this);
    }

    public static GHCachingAccountInformationProvider getInstance() {
        return Application.get().getInstance(GHCachingAccountInformationProvider.class);
    }

    /**
     * Will either schedule loading with the supplied executor or await the running request
     */
    public CompletableFuture<GithubAuthenticatedUser> loadInformation(GithubApiRequestExecutor executor, Account account) {
        CompletableFuture<GithubAuthenticatedUser> deferred;
        try {
            deferred = myInformationCache.get(account.getId(), () ->
                executor.execute(GithubApiRequests.CurrentUser.get(GithubAccountType.toServerPath(myAccountManager.getServerUrl(account)))));
        }
        catch (ExecutionException e) {
            return CompletableFuture.failedFuture(e.getCause());
        }
        CompletableFuture<GithubAuthenticatedUser> result = deferred;
        return result.whenComplete((user, error) -> {
            if (error != null) {
                myInformationCache.asMap().remove(account.getId(), result);
            }
        });
    }

    @Override
    public void dispose() {
        myInformationCache.invalidateAll();
    }
}
