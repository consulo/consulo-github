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

import consulo.application.progress.ProgressIndicator;
import consulo.github.localize.GithubLocalize;
import consulo.logging.Logger;
import org.jetbrains.plugins.github.api.data.GithubErrorMessage;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubJsonException;
import org.jetbrains.plugins.github.exceptions.GithubRateLimitExceededException;
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Executes API requests taking care of authentication, headers, proxies, timeouts, etc.
 */
public abstract class GithubApiRequestExecutor {
    static final String PLUGIN_USER_AGENT_NAME = "Consulo-GitHub-Plugin";
    static final Logger LOG = Logger.getInstance(GithubApiRequestExecutor.class);

    static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";
    static final String RATE_LIMIT_RESOURCE_HEADER = "X-RateLimit-Resource";

    GithubApiRequestExecutor() {
    }

    /**
     * Blocking execution on a background thread
     */
    public abstract <T> T execute(ProgressIndicator indicator, GithubApiRequest<T> request) throws IOException;

    public abstract <T> CompletableFuture<T> execute(GithubApiRequest<T> request);


    static boolean isAuthorizedUrl(GithubServerPath serverPath, URL url) {
        String targetHost = url.getHost();
        String apiHost = serverPath.getApiHost();
        String mainHost = serverPath.getHost();

        String ghDotComRawHost = "raw.githubusercontent.com";
        // If GH Enterprise serves raw files from raw.<enterprise-host>
        String enterpriseRawHost = "raw." + mainHost;
        // GHE serves avatars from avatars.<enterprise-host>
        String enterpriseAvatarHost = "avatars." + mainHost;
        // GHE serves media attachments from media.<enterprise-host>
        String enterpriseMediaHost = "media." + mainHost;

        boolean hostMatches;
        if (targetHost.equals(mainHost)
            || targetHost.equals(apiHost)
            || targetHost.equals(enterpriseRawHost)
            || targetHost.equals(enterpriseAvatarHost)
            || targetHost.equals(enterpriseMediaHost)) {
            hostMatches = true;
        }
        else if (serverPath.isGithubDotCom() || serverPath.isGheDataResidency()) {
            hostMatches = targetHost.equals(ghDotComRawHost);
        }
        else {
            hostMatches = false;
        }

        if (!hostMatches) {
            // do not log for avatars to avoid log pollution
            if (!targetHost.equals("avatars.githubusercontent.com")) {
                LOG.info("URL " + url + " host does not match the server " + serverPath + ". Authorization will not be granted");
            }
            return false;
        }
        Integer port = serverPath.getPort();
        if (url.getPort() != (port == null ? -1 : port)) {
            LOG.info("URL " + url + " port does not match the server " + serverPath + ". Authorization will not be granted");
            return false;
        }
        if (url.getProtocol() != null && !url.getProtocol().equals(serverPath.getSchema())) {
            LOG.info("URL " + url + " protocol does not match the server " + serverPath + ". Authorization will not be granted");
            return false;
        }
        if (serverPath.getSchema().equals("http")) {
            LOG.warn("URL " + url + " use HTTP, not HTTPS, token leak is possible");
        }
        return true;
    }

    static IOException createApiException(String requestName,
                                          int responseCode,
                                          @Nullable String responseText,
                                          @Nullable String responseContentType) {
        GithubErrorMessage jsonError = responseText != null && responseContentType != null
            ? getJsonError(requestName, responseText, responseContentType)
            : null;

        return switch (responseCode) {
            case HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_PAYMENT_REQUIRED, HttpURLConnection.HTTP_FORBIDDEN -> {
                if (jsonError != null && jsonError.containsReasonMessage("API rate limit exceeded")) {
                    yield new GithubRateLimitExceededException(jsonError.getPresentableError());
                }
                String error = jsonError != null
                    ? jsonError.getPresentableError()
                    : Objects.requireNonNullElse(responseText, String.valueOf(responseCode));
                yield new GithubAuthenticationException(GithubLocalize.requestResponse0(error).get());
            }
            default -> {
                if (jsonError != null) {
                    yield new GithubStatusCodeException(responseCode + " - " + jsonError.getPresentableError(), jsonError, responseCode);
                }
                yield new GithubStatusCodeException(responseCode + " - " + responseText, responseCode);
            }
        };
    }

    static @Nullable GithubErrorMessage getJsonError(String requestName, String errorText, String contentType) {
        if (!contentType.startsWith(GithubApiContentHelper.JSON_MIME_TYPE)) {
            return null;
        }
        try {
            return GithubApiContentHelper.fromJson(errorText, GithubErrorMessage.class);
        }
        catch (GithubJsonException jse) {
            LOG.debug(requestName + " : Unable to parse JSON error from text \n" + errorText, jse);
            return null;
        }
    }

    static final class MutableTokenSupplier {
        private final GithubServerPath myServerPath;
        private volatile String myToken;

        MutableTokenSupplier(GithubServerPath serverPath, String token) {
            myServerPath = serverPath;
            myToken = token;
        }

        GithubServerPath getServerPath() {
            return myServerPath;
        }

        String getToken() {
            return myToken;
        }

        void setToken(String token) {
            myToken = token;
        }
    }
}
