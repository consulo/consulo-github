// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import consulo.logging.Logger;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Executes API requests taking care of authentication, headers, proxies, timeouts, etc.
 * Simplified port from JB's Kotlin sealed class hierarchy.
 */
public class GithubApiRequestExecutor {
    private static final Logger LOG = Logger.getInstance(GithubApiRequestExecutor.class);
    private static final String USER_AGENT = "Consulo-GitHub-Plugin";
    private static final Gson GSON = new Gson();

    private final @Nullable String myToken;
    private final GithubServerPath myServerPath;
    private final HttpClient myHttpClient;

    private GithubApiRequestExecutor(@Nullable String token, GithubServerPath serverPath) {
        myToken = token;
        myServerPath = serverPath;
        myHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Execute a GET request and return the parsed JSON response.
     */
    public JsonElement executeGet(String url) throws IOException {
        HttpRequest request = buildRequest(url)
            .GET()
            .build();
        return executeAndParse(request);
    }

    /**
     * Execute a POST request with a JSON body and return the parsed JSON response.
     */
    public JsonElement executePost(String url, Object body) throws IOException {
        String jsonBody = GSON.toJson(body);
        HttpRequest request = buildRequest(url)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .header("Content-Type", "application/json")
            .build();
        return executeAndParse(request);
    }

    /**
     * Execute a PATCH request with a JSON body and return the parsed JSON response.
     */
    public JsonElement executePatch(String url, Object body) throws IOException {
        String jsonBody = GSON.toJson(body);
        HttpRequest request = buildRequest(url)
            .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
            .header("Content-Type", "application/json")
            .build();
        return executeAndParse(request);
    }

    /**
     * Execute a DELETE request.
     */
    public void executeDelete(String url) throws IOException {
        HttpRequest request = buildRequest(url)
            .DELETE()
            .build();
        executeAndCheckStatus(request);
    }

    /**
     * Execute a GraphQL query.
     */
    public JsonObject executeGraphQL(String query, @Nullable java.util.Map<String, Object> variables) throws IOException {
        String graphqlUrl = myServerPath.toGraphQLUrl();
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("query", query);
        if (variables != null && !variables.isEmpty()) {
            requestBody.add("variables", GSON.toJsonTree(variables));
        }

        HttpRequest request = buildRequest(graphqlUrl)
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
            .header("Content-Type", "application/json")
            .build();

        JsonElement response = executeAndParse(request);
        if (!response.isJsonObject()) {
            throw new IOException("Unexpected GraphQL response format");
        }

        JsonObject responseObj = response.getAsJsonObject();
        if (responseObj.has("errors")) {
            String errorMsg = responseObj.getAsJsonArray("errors").toString();
            throw new IOException("GraphQL error: " + errorMsg);
        }
        return responseObj;
    }

    private HttpRequest.Builder buildRequest(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .timeout(Duration.ofSeconds(30));

        if (myToken != null) {
            builder.header("Authorization", "Bearer " + myToken);
        }

        return builder;
    }

    private JsonElement executeAndParse(HttpRequest request) throws IOException {
        LOG.debug("Request: " + request.method() + " " + request.uri());
        try {
            HttpResponse<String> response = myHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            checkResponseCode(request, response);
            String body = response.body();
            LOG.debug("Response: " + response.statusCode() + " body length: " + (body != null ? body.length() : 0));
            return JsonParser.parseString(body);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    private void executeAndCheckStatus(HttpRequest request) throws IOException {
        LOG.debug("Request: " + request.method() + " " + request.uri());
        try {
            HttpResponse<String> response = myHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            checkResponseCode(request, response);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    private void checkResponseCode(HttpRequest request, HttpResponse<String> response) throws IOException {
        int statusCode = response.statusCode();
        if (statusCode < 400) {
            return;
        }

        String body = response.body();
        LOG.debug("Request: " + request.method() + " " + request.uri() + " : Error " + statusCode + " body:\n" + body);

        if (statusCode == 401 || statusCode == 403) {
            throw new GithubAuthenticationException("Request response: " + statusCode + " " + (body != null ? body : ""));
        }

        throw new GithubStatusCodeException(statusCode + " - " + (body != null ? body : ""), statusCode);
    }

    // --- Factory methods ---

    public static GithubApiRequestExecutor create(GithubServerPath serverPath, String token) {
        return new GithubApiRequestExecutor(token, serverPath);
    }

    public static GithubApiRequestExecutor createNoAuth(GithubServerPath serverPath) {
        return new GithubApiRequestExecutor(null, serverPath);
    }
}
