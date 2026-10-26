// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util;

import consulo.application.progress.ProgressIndicator;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.data.GithubResponsePage;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GithubApiPagesLoader {
    private GithubApiPagesLoader() {
    }

    public static <T> List<T> loadAll(GithubApiRequestExecutor executor, ProgressIndicator indicator, Request<T> pagesRequest)
        throws IOException {
        List<T> result = new ArrayList<>();
        loadAll(executor, indicator, pagesRequest, result::addAll);
        return result;
    }

    public static <T> CompletableFuture<List<T>> loadAll(GithubApiRequestExecutor executor, Request<T> pagesRequest) {
        List<T> result = new ArrayList<>();
        return batchesFlow(executor, pagesRequest).apply(result::addAll).thenApply(it -> result);
    }

    private static <T> void loadAll(GithubApiRequestExecutor executor,
                                    ProgressIndicator indicator,
                                    Request<T> pagesRequest,
                                    Consumer<List<T>> pageItemsConsumer) throws IOException {
        GithubApiRequest<GithubResponsePage<T>> request = pagesRequest.getInitialRequest();
        while (request != null) {
            GithubResponsePage<T> page = executor.execute(indicator, request);
            pageItemsConsumer.accept(page.getItems());
            String nextLink = page.getNextLink();
            request = nextLink == null ? null : pagesRequest.getUrlRequestProvider().apply(nextLink);
        }
    }

    /**
     * Loads the pages one after another and passes the items of each page to the consumer
     */
    public static <T> Function<Consumer<List<T>>, CompletableFuture<?>> batchesFlow(GithubApiRequestExecutor executor, Request<T> pagesRequest) {
        return consumer -> loadPage(executor, pagesRequest, pagesRequest.getInitialRequest(), consumer);
    }

    private static <T> CompletableFuture<?> loadPage(GithubApiRequestExecutor executor,
                                                     Request<T> pagesRequest,
                                                     @Nullable GithubApiRequest<GithubResponsePage<T>> request,
                                                     Consumer<List<T>> consumer) {
        if (request == null) {
            return CompletableFuture.completedFuture(null);
        }
        return executor.execute(request).thenCompose(page -> {
            consumer.accept(page.getItems());
            String nextLink = page.getNextLink();
            return loadPage(executor, pagesRequest, nextLink == null ? null : pagesRequest.getUrlRequestProvider().apply(nextLink), consumer);
        });
    }

    public static <T> List<T> load(GithubApiRequestExecutor executor, ProgressIndicator indicator, Request<T> pagesRequest, int maximum)
        throws IOException {
        List<T> result = new ArrayList<>();
        GithubApiRequest<GithubResponsePage<T>> request = pagesRequest.getInitialRequest();
        while (request != null) {
            GithubResponsePage<T> page = executor.execute(indicator, request);
            for (T item : page.getItems()) {
                result.add(item);
                if (result.size() == maximum) {
                    return result;
                }
            }
            String nextLink = page.getNextLink();
            request = nextLink == null ? null : pagesRequest.getUrlRequestProvider().apply(nextLink);
        }
        return result;
    }

    public static class Request<T> {
        private final GithubApiRequest<GithubResponsePage<T>> myInitialRequest;
        private final Function<String, GithubApiRequest<GithubResponsePage<T>>> myUrlRequestProvider;

        public Request(GithubApiRequest<GithubResponsePage<T>> initialRequest,
                       Function<String, GithubApiRequest<GithubResponsePage<T>>> urlRequestProvider) {
            myInitialRequest = initialRequest;
            myUrlRequestProvider = urlRequestProvider;
        }

        public GithubApiRequest<GithubResponsePage<T>> getInitialRequest() {
            return myInitialRequest;
        }

        public Function<String, GithubApiRequest<GithubResponsePage<T>>> getUrlRequestProvider() {
            return myUrlRequestProvider;
        }
    }
}
