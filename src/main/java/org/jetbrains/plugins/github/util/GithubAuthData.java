/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.util;

import org.jetbrains.plugins.github.api.GithubApiUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Container for authentication data:
 * - host
 * - login
 * - login/password pair
 * or
 * - OAuth2 access token
 *
 * @author Aleksey Pivovarov
 */
public class GithubAuthData {
    public enum AuthType {
        @Deprecated
        BASIC,
        TOKEN,
        ANONYMOUS,
    }

    @Nonnull
    private final AuthType myAuthType;
    @Nonnull
    private final String myHost;
    @Nullable
    private final BasicAuth myBasicAuth;
    @Nullable
    private final TokenAuth myTokenAuth;
    private final boolean myUseProxy;


    private GithubAuthData(
        @Nonnull AuthType authType,
        @Nonnull String host,
        @Nullable BasicAuth basicAuth,
        @Nullable TokenAuth tokenAuth,
        boolean useProxy
    ) {
        myAuthType = authType;
        myHost = host;
        myBasicAuth = basicAuth;
        myTokenAuth = tokenAuth;
        myUseProxy = useProxy;
    }

    public static GithubAuthData createAnonymous() {
        return createAnonymous(GithubApiUtil.DEFAULT_GITHUB_HOST);
    }

    public static GithubAuthData createAnonymous(@Nonnull String host) {
        return new GithubAuthData(AuthType.ANONYMOUS, host, null, null, true);
    }

    public static GithubAuthData createBasicAuth(@Nonnull String host, @Nonnull String login, @Nonnull String password) {
        return new GithubAuthData(AuthType.BASIC, host, new BasicAuth(login, password), null, true);
    }

    public static GithubAuthData createTokenAuth(@Nonnull String host, @Nonnull String token) {
        return new GithubAuthData(AuthType.TOKEN, host, null, new TokenAuth(token), true);
    }

    public static GithubAuthData createTokenAuth(@Nonnull String host, @Nonnull String token, boolean useProxy) {
        return new GithubAuthData(AuthType.TOKEN, host, null, new TokenAuth(token), useProxy);
    }

    @Nonnull
    public AuthType getAuthType() {
        return myAuthType;
    }

    @Nonnull
    public String getHost() {
        return myHost;
    }

    @Nullable
    public BasicAuth getBasicAuth() {
        return myBasicAuth;
    }

    @Nullable
    public TokenAuth getTokenAuth() {
        return myTokenAuth;
    }

    public boolean isUseProxy() {
        return myUseProxy;
    }

    public static class BasicAuth {
        @Nonnull
        private final String myLogin;
        @Nonnull
        private final String myPassword;

        private BasicAuth(@Nonnull String login, @Nonnull String password) {
            myLogin = login;
            myPassword = password;
        }

        @Nonnull
        public String getLogin() {
            return myLogin;
        }

        @Nonnull
        public String getPassword() {
            return myPassword;
        }
    }

    public static class TokenAuth {
        @Nonnull
        private final String myToken;

        private TokenAuth(@Nonnull String token) {
            myToken = token;
        }

        @Nonnull
        public String getToken() {
            return myToken;
        }
    }
}
