// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import consulo.accountManagement.oauth.OAuthCredentials;
import consulo.accountManagement.oauth.OAuthCredentialsAcquirer;
import consulo.accountManagement.oauth.OAuthRequest;
import consulo.accountManagement.oauth.OAuthServiceBase;
import consulo.accountManagement.oauth.PkceUtils;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.builtinWebServer.BuiltInServerManager;
import consulo.util.io.Url;
import consulo.util.io.Urls;
import consulo.util.lang.StringUtil;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class GHOAuthService extends OAuthServiceBase<OAuthCredentials> {
    private static final String SERVICE_NAME = "github/oauth";

    private static final String SERVICE_URL_PROPERTY = "consulo.github.oauth.service.url";

    public static GHOAuthService getInstance() {
        return Application.get().getInstance(GHOAuthService.class);
    }

    public static @Nullable Url getServiceUrl() {
        String url = StringUtil.nullize(System.getProperty(SERVICE_URL_PROPERTY), true);
        return url == null ? null : Urls.newFromEncoded(url);
    }

    static Url getServiceUrl(@Nullable OAuthRequest<?> request) {
        if (request instanceof GHOAuthRequest ghRequest) {
            return ghRequest.myServiceUrl;
        }
        return Objects.requireNonNull(getServiceUrl());
    }

    public static boolean isAvailable() {
        return getServiceUrl() != null;
    }

    @Override
    public String getName() {
        return SERVICE_NAME;
    }

    public CompletableFuture<OAuthCredentials> authorize() {
        Url serviceUrl = getServiceUrl();
        if (serviceUrl == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("GitHub OAuth service is not configured"));
        }
        return authorize(new GHOAuthRequest(serviceUrl));
    }

    @Override
    public void revokeToken(String token) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private static final class GHOAuthRequest implements OAuthRequest<OAuthCredentials> {
        private final Url myServiceUrl;

        private final String myCodeVerifier = PkceUtils.generateCodeVerifier();

        private final String myCodeChallenge = PkceUtils.generateShaCodeChallenge(myCodeVerifier, Base64.getEncoder());

        private final OAuthCredentialsAcquirer<OAuthCredentials> myCredentialsAcquirer;

        private GHOAuthRequest(Url serviceUrl) {
            myServiceUrl = serviceUrl;
            myCredentialsAcquirer = new GHOAuthCredentialsAcquirer(serviceUrl, myCodeVerifier);
        }

        private int getPort() {
            return BuiltInServerManager.getInstance().getPort();
        }

        @Override
        public Url getAuthorizationCodeUrl() {
            return Objects.requireNonNull(Urls.newFromEncoded("http://127.0.0.1:" + getPort() + "/api/" + SERVICE_NAME + "/authorization_code"));
        }

        @Override
        public OAuthCredentialsAcquirer<OAuthCredentials> getCredentialsAcquirer() {
            return myCredentialsAcquirer;
        }

        @Override
        public Url getAuthUrlWithParameters() {
            return myServiceUrl.resolve("authorize").addParameters(Map.of(
                "code_challenge", myCodeChallenge,
                "callback_url", getAuthorizationCodeUrl().toExternalForm()
            ));
        }
    }
}
