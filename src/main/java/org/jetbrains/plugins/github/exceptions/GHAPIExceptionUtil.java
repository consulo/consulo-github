// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.exceptions;

import consulo.github.localize.GithubLocalize;
import consulo.review.ExceptionUtil;
import consulo.util.lang.xml.XmlStringUtil;
import org.jetbrains.plugins.github.api.data.GithubErrorMessage;

import org.jspecify.annotations.Nullable;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GHAPIExceptionUtil {
    public static final String OAUTH_ERROR_SUPPORT_LINK = "https://youtrack.jetbrains.com/articles/SUPPORT-A-2404";
    private static final String OAUTH_ERROR_MESSAGE_PREFIX = "Could not resolve to a Repository";

    private GHAPIExceptionUtil() {
    }

    /**
     * Returns a user-presentable error message.
     *
     * @return An error description that may contain HTML for rich formatting
     * or plain text depending on the exception type. Callers should handle both cases.
     */
    public static String getPresentableMessage(Throwable e) {
        if (e instanceof GithubStatusCodeException statusCodeException) {
            return getStatusCodeErrorMessage(statusCodeException);
        }
        if (e instanceof GithubConfusingException confusingException) {
            return getConfusingErrorMessage(confusingException);
        }
        if (e instanceof GithubAuthenticationException) {
            return GithubLocalize.authorizationFailed().get();
        }
        // Is encountered sometimes after regaining connection, but otherwise only 'api.github.com' is shown as a description.
        if (e instanceof UnknownHostException) {
            return GithubLocalize.unknownHostError(Objects.toString(e.getMessage(), "")).get();
        }
        return ExceptionUtil.getPresentableMessage(e).get();
    }

    private static String getStatusCodeErrorMessage(GithubStatusCodeException error) {
        GithubErrorMessage githubError = error.getError();
        if (githubError != null && githubError.getMessage() != null) {
            String message = githubError.getMessage();
            if (message.startsWith("[")) {
                message = message.substring(1);
            }
            if (message.endsWith("]")) {
                message = message.substring(0, message.length() - 1);
            }
            StringBuilder builder = new StringBuilder(XmlStringUtil.escapeString(message));
            if (message.regionMatches(true, 0, OAUTH_ERROR_MESSAGE_PREFIX, 0, OAUTH_ERROR_MESSAGE_PREFIX.length())) {
                appendOAuthErrorMessage(builder, null);
            }
            List<GithubErrorMessage.Error> errors = githubError.getErrors();
            if (errors != null && !errors.isEmpty()) {
                List<String> texts = new ArrayList<>();
                for (GithubErrorMessage.Error e : errors) {
                    String text = e.getMessage() != null
                        ? e.getMessage()
                        : GithubLocalize.gqlErrorInField(Objects.toString(e.getCode(), ""), Objects.toString(e.getResource(), ""),
                        Objects.toString(e.getField(), "")).get();
                    texts.add(XmlStringUtil.escapeString(text));
                }
                builder.append(": ").append("<br/>").append(String.join("<br/>", texts));
            }
            return builder.toString();
        }

        String message = error.getMessage();
        return message != null ? message : GithubLocalize.unknownLoadingError().get();
    }

    private static String getConfusingErrorMessage(GithubConfusingException error) {
        String message = error.getMessage();
        if (message != null) {
            String[] messageParts = message.split("\n\n");
            if (messageParts.length >= 2) {
                String title = messageParts[0];
                String description = messageParts[1];
                if (description.regionMatches(true, 0, OAUTH_ERROR_MESSAGE_PREFIX, 0, OAUTH_ERROR_MESSAGE_PREFIX.length())) {
                    StringBuilder builder = new StringBuilder();
                    appendOAuthErrorMessage(builder, title);
                    return builder.toString();
                }
            }
        }
        return message != null ? message : GithubLocalize.unknownLoadingError().get();
    }

    private static void appendOAuthErrorMessage(StringBuilder builder, @Nullable String title) {
        if (title != null) {
            builder.append(XmlStringUtil.escapeString(title));
        }
        builder.append("<br/>");
        builder.append(XmlStringUtil.escapeString(GithubLocalize.oauthRestrictionsError().get())).append(" ");
        builder.append("<a href=\"").append(OAUTH_ERROR_SUPPORT_LINK).append("\">")
            .append(XmlStringUtil.escapeString(GithubLocalize.errorSupportLinkLabel().get())).append("</a>");
    }
}
