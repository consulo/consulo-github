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
package org.jetbrains.plugins.github.api;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountConnection;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountRequestDecorator;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.http.client.HttpClientUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException;
import org.jetbrains.plugins.github.exceptions.GithubParseException;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class GithubApiRequestExecutorFactory {
    private final Application myApplication;

    @Inject
    public GithubApiRequestExecutorFactory(Application application) {
        myApplication = application;
    }

    public static GithubApiRequestExecutorFactory getInstance() {
        return Application.get().getInstance(GithubApiRequestExecutorFactory.class);
    }

    /**
     * Create an executor which would lazily acquire the {@code account} credentials before every call
     * <p>
     * {@link GithubApiRequestExecutor#execute} will fail with a {@link GithubMissingTokenException} if the credentials are missing at the time of the request
     * This is not a normal situation and should be handled by the caller (e.g. by offering to re-login)
     */
    public GithubApiRequestExecutor create(Account account) {
        String serverUrl = myApplication.getInstance(AccountManager.class).getServerUrl(account);
        GithubServerPath serverPath = GithubServerPath.DEFAULT_SERVER;
        if (serverUrl != null) {
            try {
                serverPath = GithubServerPath.from(serverUrl);
            }
            catch (GithubParseException e) {
                GithubApiRequestExecutor.LOG.warn("Invalid GitHub server " + serverUrl + " of account " + account.getName(), e);
            }
        }
        return create(account, serverPath);
    }

    public GithubApiRequestExecutor create(Account account, GithubServerPath serverPath) {
        AccountManager accountManager = myApplication.getInstance(AccountManager.class);
        return create(true, serverPath, () -> accountManager.connect(account)
            .<Map<String, String>>thenApply(GithubApiRequestExecutorFactory::collectHeaders)
            .exceptionallyCompose(e -> CompletableFuture.failedFuture(new GithubMissingTokenException(account))));
    }

    public GithubApiRequestExecutor create(GithubServerPath serverPath, AccountConnection connection) {
        Map<String, String> headers = collectHeaders(connection);
        return create(true, serverPath, () -> CompletableFuture.completedFuture(headers));
    }

    /**
     * Create an executor which reads the current credentials from {@code connectionSupplier} before every call
     */
    public GithubApiRequestExecutor create(GithubServerPath serverPath, Supplier<AccountConnection> connectionSupplier) {
        return create(true, serverPath, () -> CompletableFuture.completedFuture(collectHeaders(connectionSupplier.get())));
    }

    public GithubApiRequestExecutor create(GithubServerPath serverPath, String token) {
        return create(true, serverPath, token);
    }

    GithubApiRequestExecutor create(GithubApiRequestExecutor.MutableTokenSupplier tokenSupplier) {
        return create(true, tokenSupplier.getServerPath(), () -> CompletableFuture.completedFuture(bearer(tokenSupplier.getToken())));
    }

    public GithubApiRequestExecutor create(boolean useProxy, GithubServerPath serverPath, String token) {
        Map<String, String> headers = bearer(token);
        return create(useProxy, serverPath, () -> CompletableFuture.completedFuture(headers));
    }

    private GithubApiRequestExecutor create(boolean useProxy,
                                            GithubServerPath serverPath,
                                            Supplier<CompletableFuture<Map<String, String>>> authHeadersSupplier) {
        Function<URL, CompletableFuture<Map<String, String>>> guardedSupplier = url ->
            GithubApiRequestExecutor.isAuthorizedUrl(serverPath, url) ? authHeadersSupplier.get() : CompletableFuture.completedFuture(Map.of());
        return new GithubApiHelperRequestExecutor(useProxy, guardedSupplier);
    }

    public GithubApiRequestExecutor create() {
        return new GithubApiHelperRequestExecutor(true, null);
    }

    private static Map<String, String> bearer(String token) {
        return Map.of(HttpClientUtil.AUTHORIZATION_HEADER, "Bearer " + token);
    }

    private static Map<String, String> collectHeaders(AccountConnection connection) {
        Map<String, String> headers = new LinkedHashMap<>();
        connection.decorate(new AccountRequestDecorator() {
            @Override
            public void header(String name, String value) {
                headers.put(name, value);
            }

            @Override
            public void queryParameter(String name, String value) {
            }
        });
        return headers;
    }
}
