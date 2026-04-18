// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2025 consulo.io
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

import consulo.collaboration.api.ServerPath;
import org.jspecify.annotations.Nullable;
import org.jetbrains.plugins.github.exceptions.GithubParseException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Github server reference allowing to specify custom port and path to instance.
 */
public final class GithubServerPath implements ServerPath {
    public static final String DEFAULT_HOST = "github.com";
    public static final String DATA_RESIDENCY_HOST = ".ghe.com";
    public static final GithubServerPath DEFAULT_SERVER = new GithubServerPath(DEFAULT_HOST);
    private static final String API_PREFIX = "api.";
    private static final String API_SUFFIX = "/api";
    private static final String ENTERPRISE_API_V3_SUFFIX = "/v3";
    private static final String GRAPHQL_SUFFIX = "/graphql";

    private final @Nullable Boolean myUseHttp;
    private final String myHost;
    private final @Nullable Integer myPort;
    private final @Nullable String mySuffix;

    public GithubServerPath() {
        this(null, "", null, null);
    }

    public GithubServerPath(String host) {
        this(null, host, null, null);
    }

    public GithubServerPath(@Nullable Boolean useHttp, String host, @Nullable Integer port, @Nullable String suffix) {
        myUseHttp = useHttp;
        myHost = host.toLowerCase(Locale.ROOT);
        myPort = port;
        mySuffix = suffix;
    }

    public String getSchema() {
        return (myUseHttp == null || !myUseHttp) ? "https" : "http";
    }

    public String getHost() {
        return myHost;
    }

    public @Nullable Integer getPort() {
        return myPort;
    }

    public @Nullable String getSuffix() {
        return mySuffix;
    }

    // 1 - schema, 2 - host, 4 - port, 5 - path
    private static final Pattern URL_REGEX = Pattern.compile(
        "^(https?://)?([^/?:]+)(:(\\d+))?((/[^/?#]+)*)?/?",
        Pattern.CASE_INSENSITIVE
    );

    public static GithubServerPath from(String uri) throws GithubParseException {
        Matcher matcher = URL_REGEX.matcher(uri);

        if (!matcher.matches()) {
            throw new GithubParseException("Not a valid URL");
        }
        String schema = matcher.group(1);
        Boolean httpSchema = (schema == null || schema.isEmpty()) ? null : schema.equalsIgnoreCase("http://");
        String host = matcher.group(2);
        if (host == null) {
            throw new GithubParseException("Empty host");
        }

        Integer port;
        String portGroup = matcher.group(4);
        if (portGroup == null) {
            port = null;
        }
        else {
            try {
                port = Integer.parseInt(portGroup);
            }
            catch (NumberFormatException e) {
                throw new GithubParseException("Invalid port format");
            }
        }

        String path = matcher.group(5);
        if (path != null && path.isEmpty()) {
            path = null;
        }

        return new GithubServerPath(httpSchema, host, port, path);
    }

    public String toUrl() {
        return toUrl(true);
    }

    public String toUrl(boolean showSchema) {
        StringBuilder builder = new StringBuilder();
        if (showSchema) {
            builder.append(getSchemaUrlPart());
        }
        builder.append(myHost).append(getPortUrlPart());
        if (mySuffix != null) {
            builder.append(mySuffix);
        }
        return builder.toString();
    }

    public String getApiHost() {
        if (isGithubDotCom() || isGheDataResidency()) {
            return API_PREFIX + myHost;
        }
        else {
            return myHost;
        }
    }

    public String toApiUrl() {
        StringBuilder builder = new StringBuilder(getSchemaUrlPart());
        if (isGithubDotCom() || isGheDataResidency()) {
            builder.append(API_PREFIX).append(myHost).append(getPortUrlPart());
            if (mySuffix != null) {
                builder.append(mySuffix);
            }
        }
        else {
            builder.append(myHost).append(getPortUrlPart());
            if (mySuffix != null) {
                builder.append(mySuffix);
            }
            builder.append(API_SUFFIX).append(ENTERPRISE_API_V3_SUFFIX);
        }
        return builder.toString();
    }

    public String toGraphQLUrl() {
        StringBuilder builder = new StringBuilder(getSchemaUrlPart());
        if (isGithubDotCom() || isGheDataResidency()) {
            builder.append(API_PREFIX).append(myHost).append(getPortUrlPart());
            if (mySuffix != null) {
                builder.append(mySuffix);
            }
            builder.append(GRAPHQL_SUFFIX);
        }
        else {
            builder.append(myHost).append(getPortUrlPart());
            if (mySuffix != null) {
                builder.append(mySuffix);
            }
            builder.append(API_SUFFIX).append(GRAPHQL_SUFFIX);
        }
        return builder.toString();
    }

    @Override
    public URI toURI() {
        int port = myPort == null ? -1 : myPort;
        try {
            return new URI(getSchema(), null, myHost, port, mySuffix, null, null);
        }
        catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isGithubDotCom() {
        return myHost.equalsIgnoreCase(DEFAULT_HOST);
    }

    public boolean isGheDataResidency() {
        return myHost.toLowerCase(Locale.ROOT).endsWith(DATA_RESIDENCY_HOST);
    }

    @Override
    public String toString() {
        String schema = myUseHttp != null ? getSchemaUrlPart() : "";
        return schema + myHost + getPortUrlPart() + (mySuffix != null ? mySuffix : "");
    }

    private String getPortUrlPart() {
        return myPort != null ? (":" + myPort) : "";
    }

    private String getSchemaUrlPart() {
        return getSchema() + "://";
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return equals(o, false);
    }

    public boolean equals(Object o, boolean ignoreProtocol) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GithubServerPath path)) {
            return false;
        }
        return (ignoreProtocol || Objects.equals(myUseHttp, path.myUseHttp))
            && Objects.equals(myHost, path.myHost)
            && Objects.equals(myPort, path.myPort)
            && Objects.equals(mySuffix, path.mySuffix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myHost, myPort, mySuffix);
    }
}
