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
import consulo.http.client.CompoundRequestConfigurer;
import consulo.http.client.HttpApiHelper;
import consulo.http.client.HttpClientFactoryBase;
import consulo.http.client.HttpClientUtil;
import consulo.http.client.HttpRequestConfigurer;
import consulo.http.client.HttpStatusErrorException;
import consulo.http.client.RequestTimeoutConfigurer;
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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
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
        return CompletableFuture.supplyAsync(() -> {
            try {
                myRequestsSemaphore.acquire();
            }
            catch (InterruptedException e) {
                throw new CancellationException();
            }
            try {
                return doExecute(null, request);
            }
            catch (IOException e) {
                throw new CompletionException(e);
            }
            finally {
                myRequestsSemaphore.release();
            }
        }, AppExecutorUtil.getAppExecutorService());
    }

    private <T> T doExecute(@Nullable ProgressIndicator indicator, GithubApiRequest<T> request) throws IOException {
        if (GHRequestExecutorBreaker.getInstance().isRequestsShouldFail()) {
            throw new IllegalStateException(
                "Request failure was triggered by user action. This a pretty long description of this failure that should resemble some long error which can go out of bounds.");
        }

        HttpRequest httpRequest = HttpClientUtil.forceAcceptGzipEncoding(await(createHttpRequest(request)));
        String requestName = HttpApiHelper.logName(httpRequest);

        if (LOG.isTraceEnabled() && request instanceof GithubApiRequest.WithBody<?> withBody) {
            LOG.trace(requestName + " : Request body: " + withBody.getBody());
        }

        HttpResponse<InputStream> response;
        try {
            response = await(myHelper.sendAndAwait(httpRequest, requestName, HttpResponse.BodyHandlers.ofInputStream()));
        }
        catch (IOException e) {
            /*
             * GitHub server is not up to spec - it does not send WWW-Authenticate header with a 401 status code like it should.
             * If this header is missing, the JDK11 HTTP client throws an `IOException` from jdk.internal.net.http.AuthenticationFilter.response
             *
             * Generally this should be very rare - only for unauthorized requests which do need authorization. We don't expect such requests.
             */
            String message = e.getMessage();
            if (message != null && message.contains("response code 401") && isAuthenticationFilterError(e)) {
                throw createApiException(requestName, HttpURLConnection.HTTP_UNAUTHORIZED, GithubLocalize.authorizationFailed().get(), null);
            }
            throw e;
        }

        try {
            HttpClientUtil.checkStatusCodeWithLoggingBlocking(response, LOG, requestName);
            if (indicator != null) {
                indicator.checkCanceled();
            }
            checkServerVersion(response);

            return HttpClientUtil.readBodyWithLoggingBlocking(response, LOG, requestName, (stream, mimeType, charset) -> {
                GithubApiResponse apiResponse = createResponse(response, stream, charset);
                if (request instanceof GithubApiRequest.Post.GQLQuery<T> gqlQuery) {
                    Pair<T, @Nullable GHGQLRateLimit> result = gqlQuery.extractResultWithCost(apiResponse);
                    return result.getFirst();
                }
                return request.extractResult(apiResponse);
            });
        }
        catch (HttpStatusErrorException e) {
            if (request instanceof GithubApiRequest.Get.Optional<?> && e.getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                return null;
            }

            String responseContentType = response.headers().firstValue(HttpClientUtil.CONTENT_TYPE_HEADER).orElse(null);
            throw createApiException(requestName, e.getStatusCode(), e.getBody(), responseContentType);
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

    private CompletableFuture<HttpRequest> createHttpRequest(GithubApiRequest<?> request) {
        return myHelper.request(URI.create(request.getUrl())).thenApply(builder -> {
            String acceptMimeType = request.getAcceptMimeType();
            if (acceptMimeType != null) {
                builder.header(HttpClientUtil.ACCEPT_HEADER, acceptMimeType);
            }
            for (Map.Entry<String, String> entry : request.getAdditionalHeaders().entrySet()) {
                // the client computes the length from the body publisher
                if (entry.getKey().equalsIgnoreCase(HttpClientUtil.CONTENT_LENGTH_HEADER)) {
                    continue;
                }
                builder.header(entry.getKey(), entry.getValue());
            }
            try {
                if (request instanceof GithubApiRequest.Get<?>) {
                    return builder.GET().build();
                }
                else if (request instanceof GithubApiRequest.Head<?>) {
                    return builder.HEAD().build();
                }
                else if (request instanceof GithubApiRequest.Patch<?> patch) {
                    return withBody(builder, "PATCH", patch).build();
                }
                else if (request instanceof GithubApiRequest.Post<?> post) {
                    return withBody(builder, "POST", post).build();
                }
                else if (request instanceof GithubApiRequest.Put<?> put) {
                    return withBody(builder, "PUT", put).build();
                }
                else if (request instanceof GithubApiRequest.Delete<?> delete) {
                    return withBody(builder, "DELETE", delete).build();
                }
                throw new UnsupportedOperationException(request.getClass() + " is not supported");
            }
            catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    private static HttpRequest.Builder withBody(HttpRequest.Builder builder, String method, GithubApiRequest.WithBody<?> request)
        throws IOException {
        builder.header(HttpClientUtil.CONTENT_TYPE_HEADER, request.getBodyMimeType());
        String body = request.getBody();
        return builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
    }

    private static void checkServerVersion(HttpResponse<?> response) {
        // let's assume it's not ghe if header is missing or it's *.ghe.com
        String versionHeader = response.headers().firstValue(GHEServerVersionChecker.ENTERPRISE_VERSION_HEADER).orElse(null);
        if (versionHeader == null) {
            return;
        }
        // Kinda fragile...
        if (versionHeader.toLowerCase().contains("ghe.com")) {
            return;
        }

        GHEServerVersionChecker.checkVersionSupported(versionHeader);
    }

    private static GithubApiResponse createResponse(HttpResponse<?> response, InputStream body, @Nullable Charset charset) {
        return new GithubApiResponse() {
            @Override
            public @Nullable String findHeader(String headerName) {
                return response.headers().firstValue(headerName).orElse(null);
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
        return HttpApiHelper.create(LOG, new GHHttpClientFactory(useProxy), new CompoundRequestConfigurer(configurers));
    }

    private static class GHHttpClientFactory extends HttpClientFactoryBase {
        private final boolean myUseProxy;

        GHHttpClientFactory(boolean useProxy) {
            myUseProxy = useProxy;
        }

        @Override
        protected boolean useProxy() {
            return myUseProxy;
        }

        @Override
        protected long getConnectionTimeoutMillis() {
            return GithubSettings.getInstance().getConnectionTimeout();
        }
    }

    private static class GHHeadersConfigurer implements HttpRequestConfigurer {
        @Override
        public HttpRequest.Builder configure(HttpRequest.Builder builder) {
            builder.header(HttpClientUtil.ACCEPT_ENCODING_HEADER, HttpClientUtil.CONTENT_ENCODING_GZIP);
            builder.header(HttpClientUtil.USER_AGENT_HEADER, HttpClientUtil.getUserAgentValue(PLUGIN_USER_AGENT_NAME));
            return builder;
        }
    }

    private static class GHAuthorizationConfigurer implements HttpRequestConfigurer {
        private final Function<URL, CompletableFuture<Map<String, String>>> myAuthHeadersSupplier;

        GHAuthorizationConfigurer(Function<URL, CompletableFuture<Map<String, String>>> authHeadersSupplier) {
            myAuthHeadersSupplier = authHeadersSupplier;
        }

        @Override
        public CompletableFuture<HttpRequest.Builder> configureAsync(HttpRequest.Builder builder) {
            URL url;
            try {
                url = builder.copy().build().uri().toURL();
            }
            catch (MalformedURLException e) {
                return CompletableFuture.failedFuture(e);
            }
            return myAuthHeadersSupplier.apply(url).thenApply(headers -> {
                headers.forEach(builder::header);
                return builder;
            });
        }
    }
}
