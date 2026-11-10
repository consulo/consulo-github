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
package org.jetbrains.plugins.github.api;

import consulo.application.progress.ProgressIndicator;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.application.util.registry.Registry;
import consulo.component.ProcessCanceledException;
import consulo.github.localize.GithubLocalize;
import consulo.http.HttpMethod;
import consulo.http.HttpRequest;
import consulo.http.HttpRequestBuilder;
import consulo.http.client.CompoundRequestConfigurer;
import consulo.http.client.HttpApiHelper;
import consulo.http.client.HttpClientUtil;
import consulo.http.client.HttpRequestConfigurer;
import consulo.http.client.HttpStatusErrorException;
import consulo.http.client.RequestTimeoutConfigurer;
import consulo.review.util.FutureUtil;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.github.api.data.graphql.GHGQLRateLimit;
import org.jetbrains.plugins.github.exceptions.GithubConfusingException;
import org.jetbrains.plugins.github.util.GithubSettings;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/**
 * {@link HttpApiHelper}-based executor
 */
class GithubApiHelperRequestExecutor extends GithubApiRequestExecutor {
    private static final String MAX_CONCURRENT_REQUESTS_REGISTRY_KEY = "github.api.max.concurrent.requests";
    /**
     * The default maximum number of concurrent requests.
     * GitHub allows no more than 100 concurrent requests:
     * https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api?apiVersion=2026-03-10#about-secondary-rate-limits.
     * The default stays below that limit.
     */
    static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 50;

    private final HttpApiHelper myHelper;

    /**
     * Limits the number of concurrent requests of this executor.
     * The registry key {@link #MAX_CONCURRENT_REQUESTS_REGISTRY_KEY} sets the limit.
     */
    private final Semaphore myRequestsSemaphore =
        new Semaphore(Math.max(1, Registry.intValue(MAX_CONCURRENT_REQUESTS_REGISTRY_KEY, DEFAULT_MAX_CONCURRENT_REQUESTS)));

    GithubApiHelperRequestExecutor(HttpApiHelper helper) {
        myHelper = helper;
    }

    GithubApiHelperRequestExecutor(boolean useProxy, @Nullable Function<URL, CompletableFuture<Map<String, String>>> authHeadersSupplier) {
        this(createHelper(useProxy, authHeadersSupplier));
    }

    @Override
    public <T> T execute(ProgressIndicator indicator, GithubApiRequest<T> request) throws IOException {
        indicator.checkCanceled();
        try {
            myRequestsSemaphore.acquire();
        }
        catch (InterruptedException e) {
            throw new ProcessCanceledException(e);
        }
        try {
            return doExecute(indicator, request);
        }
        finally {
            myRequestsSemaphore.release();
        }
    }

    @Override
    public <T> CompletableFuture<T> execute(GithubApiRequest<T> request) {
        CompletableFuture<T> result = new CompletableFuture<>();
        AppExecutorUtil.getAppExecutorService().execute(() -> {
            if (result.isDone()) {
                return;
            }
            try {
                myRequestsSemaphore.acquire();
            }
            catch (InterruptedException e) {
                result.cancel(false);
                return;
            }
            CompletableFuture<T> execution;
            try {
                execution = doExecuteAsync(request);
            }
            catch (Throwable e) {
                execution = CompletableFuture.failedFuture(e);
            }
            execution.whenComplete((value, error) -> {
                myRequestsSemaphore.release();
                if (error == null) {
                    result.complete(value);
                }
                else {
                    result.completeExceptionally(FutureUtil.unwrap(error));
                }
            });
            HttpClientUtil.cancelWith(result, execution);
        });
        return result;
    }

    private <T> CompletableFuture<T> doExecuteAsync(GithubApiRequest<T> request) {
        checkBreaker();

        CompletableFuture<T> result = new CompletableFuture<>();
        CompletableFuture<HttpRequestBuilder> builderFuture = createHttpRequest(request);
        HttpClientUtil.cancelWith(result, builderFuture);
        builderFuture.whenComplete((builder, buildError) -> {
            if (buildError != null) {
                result.completeExceptionally(FutureUtil.unwrap(buildError));
                return;
            }
            if (result.isDone()) {
                return;
            }
            String requestName = HttpApiHelper.logName(builder);
            try {
                logRequestBody(request, requestName);
            }
            catch (IOException e) {
                result.completeExceptionally(e);
                return;
            }

            CompletableFuture<T> sent = send(null, request, requestName, builder);
            HttpClientUtil.cancelWith(result, sent);
            sent.whenComplete((value, sendError) -> {
                if (sendError != null) {
                    Throwable cause = FutureUtil.unwrap(sendError);
                    result.completeExceptionally(cause instanceof IOException ioException ? mapSendError(requestName, ioException) : cause);
                }
                else {
                    result.complete(value);
                }
            });
        });
        return result;
    }

    private <T> T doExecute(@Nullable ProgressIndicator indicator, GithubApiRequest<T> request) throws IOException {
        checkBreaker();

        HttpRequestBuilder builder = await(createHttpRequest(request));
        String requestName = HttpApiHelper.logName(builder);
        logRequestBody(request, requestName);

        try {
            return await(send(indicator, request, requestName, builder));
        }
        catch (IOException e) {
            throw mapSendError(requestName, e);
        }
    }

    private static <T> CompletableFuture<T> send(@Nullable ProgressIndicator indicator,
                                                 GithubApiRequest<T> request,
                                                 String requestName,
                                                 HttpRequestBuilder builder) {
        LOG.debug(requestName);
        return builder.allowErrorCodes(true)
            .gzip(true)
            .connectAsync(response -> handleResponse(indicator, request, requestName, response));
    }

    private static void checkBreaker() {
        if (GHRequestExecutorBreaker.getInstance().isRequestsShouldFail()) {
            throw new IllegalStateException(
                "Request failure was triggered by user action. This a pretty long description of this failure that should resemble some long error which can go out of bounds.");
        }
    }

    private static void logRequestBody(GithubApiRequest<?> request, String requestName) throws IOException {
        if (LOG.isTraceEnabled() && request instanceof GithubApiRequest.WithBody<?> withBody) {
            LOG.trace(requestName + " : Request body: " + withBody.getBody());
        }
    }

    private static IOException mapSendError(String requestName, IOException e) {
        /*
         * GitHub server is not up to spec - it does not send WWW-Authenticate header with a 401 status code like it should.
         * If this header is missing, the JDK11 HTTP client throws an `IOException` from jdk.internal.net.http.AuthenticationFilter.response
         *
         * Generally this should be very rare - only for unauthorized requests which do need authorization. We don't expect such requests.
         */
        String message = e.getMessage();
        if (message != null && message.contains("response code 401") && isAuthenticationFilterError(e)) {
            return createApiException(requestName, HttpURLConnection.HTTP_UNAUTHORIZED, GithubLocalize.authorizationFailed().get(), null);
        }
        return e;
    }

    private static <T> T handleResponse(@Nullable ProgressIndicator indicator,
                                        GithubApiRequest<T> request,
                                        String requestName,
                                        HttpRequest response) throws IOException {
        try {
            Map<String, List<String>> headers = response.responseHeaders();
            return HttpClientUtil.readResponse(response, LOG, requestName, (stream, mimeType, charset) -> {
                if (indicator != null) {
                    indicator.checkCanceled();
                }
                checkServerVersion(headers);

                GithubApiResponse apiResponse = createResponse(headers, stream, charset);
                if (request instanceof GithubApiRequest.Post.GQLQuery<T> gqlQuery) {
                    Pair<T, @Nullable GHGQLRateLimit> result = gqlQuery.extractResultWithCost(apiResponse);
                    return result.getFirst();
                }
                return request.extractResult(apiResponse);
            }).body();
        }
        catch (HttpStatusErrorException e) {
            if (request instanceof GithubApiRequest.Get.Optional<?> && e.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }

            throw createApiException(requestName, e.getStatusCode(), e.getBody(), response.getContentType());
        }
        catch (GithubConfusingException e) {
            String operationName = request.getOperationName();
            if (operationName != null) {
                String errorText = "Can't " + operationName;
                e.setDetails(errorText);
                LOG.debug(errorText, e);
            }
            throw e;
        }
    }

    private CompletableFuture<HttpRequestBuilder> createHttpRequest(GithubApiRequest<?> request) {
        HttpMethod method;
        if (request instanceof GithubApiRequest.Get<?>) {
            method = HttpMethod.GET;
        }
        else if (request instanceof GithubApiRequest.Head<?>) {
            method = HttpMethod.HEAD;
        }
        else if (request instanceof GithubApiRequest.Patch<?>) {
            method = HttpMethod.PATCH;
        }
        else if (request instanceof GithubApiRequest.Post<?>) {
            method = HttpMethod.POST;
        }
        else if (request instanceof GithubApiRequest.Put<?>) {
            method = HttpMethod.PUT;
        }
        else if (request instanceof GithubApiRequest.Delete<?>) {
            method = HttpMethod.DELETE;
        }
        else {
            return CompletableFuture.failedFuture(new UnsupportedOperationException(request.getClass() + " is not supported"));
        }

        return myHelper.request(request.getUrl(), method).thenApply(builder -> {
            String acceptMimeType = request.getAcceptMimeType();
            if (acceptMimeType != null) {
                builder.header(HttpClientUtil.ACCEPT_HEADER, acceptMimeType);
            }
            for (Map.Entry<String, String> entry : request.getAdditionalHeaders().entrySet()) {
                // the client computes the length from the body
                if (entry.getKey().equalsIgnoreCase(HttpClientUtil.CONTENT_LENGTH_HEADER)) {
                    continue;
                }
                builder.header(entry.getKey(), entry.getValue());
            }
            if (request instanceof GithubApiRequest.WithBody<?> withBody) {
                try {
                    withBody(builder, method, withBody);
                }
                catch (IOException e) {
                    throw new CompletionException(e);
                }
            }
            return builder;
        });
    }

    private static void withBody(HttpRequestBuilder builder, HttpMethod method, GithubApiRequest.WithBody<?> request) throws IOException {
        builder.header(HttpClientUtil.CONTENT_TYPE_HEADER, request.getBodyMimeType());
        String body = request.getBody();
        if (body != null) {
            builder.body(body.getBytes(StandardCharsets.UTF_8));
        }
        else if (method != HttpMethod.DELETE) {
            builder.body(new byte[0]);
        }
    }

    private static void checkServerVersion(Map<String, List<String>> headers) {
        // let's assume it's not ghe if header is missing or it's *.ghe.com
        String versionHeader = findHeader(headers, GHEServerVersionChecker.ENTERPRISE_VERSION_HEADER);
        if (versionHeader == null) {
            return;
        }
        // Kinda fragile...
        if (versionHeader.toLowerCase().contains("ghe.com")) {
            return;
        }

        GHEServerVersionChecker.checkVersionSupported(versionHeader);
    }

    private static @Nullable String findHeader(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey()) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return null;
    }

    private static GithubApiResponse createResponse(Map<String, List<String>> headers, InputStream body, @Nullable Charset charset) {
        return new GithubApiResponse() {
            @Override
            public @Nullable String findHeader(String headerName) {
                return GithubApiHelperRequestExecutor.findHeader(headers, headerName);
            }

            @Override
            public <T> T readBody(consulo.util.lang.function.ThrowableFunction<Reader, T, IOException> converter) throws IOException {
                return converter.apply(new InputStreamReader(body, charset == null ? StandardCharsets.UTF_8 : charset));
            }

            @Override
            public <T> T handleBody(consulo.util.lang.function.ThrowableFunction<InputStream, T, IOException> converter) throws IOException {
                return converter.apply(body);
            }
        };
    }

    private static boolean isAuthenticationFilterError(IOException e) {
        for (StackTraceElement element : e.getStackTrace()) {
            if (element.getClassName().equals("jdk.internal.net.http.AuthenticationFilter")) {
                return true;
            }
        }
        return false;
    }

    private static <T> T await(CompletableFuture<T> future) throws IOException {
        try {
            return future.get();
        }
        catch (InterruptedException e) {
            future.cancel(true);
            throw new ProcessCanceledException(e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException(cause);
        }
    }

    private static HttpApiHelper createHelper(boolean useProxy,
                                              @Nullable Function<URL, CompletableFuture<Map<String, String>>> authHeadersSupplier) {
        List<HttpRequestConfigurer> configurers = new ArrayList<>();
        configurers.add(new RequestTimeoutConfigurer());
        configurers.add(new GHHeadersConfigurer());
        if (authHeadersSupplier != null) {
            configurers.add(new GHAuthorizationConfigurer(authHeadersSupplier));
        }
        configurers.add(new GHConnectionConfigurer(useProxy));
        return HttpApiHelper.create(LOG, new CompoundRequestConfigurer(configurers));
    }

    private static class GHConnectionConfigurer implements HttpRequestConfigurer {
        private final boolean myUseProxy;

        GHConnectionConfigurer(boolean useProxy) {
            myUseProxy = useProxy;
        }

        @Override
        public CompletableFuture<HttpRequestBuilder> configureAsync(HttpRequestBuilder builder) {
            return CompletableFuture.completedFuture(
                builder.useProxy(myUseProxy).connectTimeout(GithubSettings.getInstance().getConnectionTimeout())
            );
        }
    }

    private static class GHHeadersConfigurer implements HttpRequestConfigurer {
        @Override
        public CompletableFuture<HttpRequestBuilder> configureAsync(HttpRequestBuilder builder) {
            builder.header(HttpClientUtil.USER_AGENT_HEADER, HttpClientUtil.getUserAgentValue(PLUGIN_USER_AGENT_NAME));
            return CompletableFuture.completedFuture(builder);
        }
    }

    private static class GHAuthorizationConfigurer implements HttpRequestConfigurer {
        private final Function<URL, CompletableFuture<Map<String, String>>> myAuthHeadersSupplier;

        GHAuthorizationConfigurer(Function<URL, CompletableFuture<Map<String, String>>> authHeadersSupplier) {
            myAuthHeadersSupplier = authHeadersSupplier;
        }

        @Override
        public CompletableFuture<HttpRequestBuilder> configureAsync(HttpRequestBuilder builder) {
            URL url;
            try {
                url = URI.create(builder.getUrl()).toURL();
            }
            catch (MalformedURLException | IllegalArgumentException e) {
                return CompletableFuture.failedFuture(e);
            }
            return myAuthHeadersSupplier.apply(url).thenApply(headers -> {
                headers.forEach(builder::header);
                return builder;
            });
        }
    }
}
