// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util;

import consulo.application.progress.ProgressIndicator;
import consulo.http.graphql.GraphQLRequestPagination;
import consulo.http.graphql.dto.GraphQLCursorPageInfoDTO;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public abstract class GHGQLPagesLoader<T, R> {
    private final GithubApiRequestExecutor myExecutor;
    private final Function<GraphQLRequestPagination, GithubApiRequest.Post<T>> myRequestProducer;
    private final boolean mySupportsTimestampUpdates;
    private final int myPageSize;

    private final AtomicReference<IterationData> myIterationDataRef = new AtomicReference<>(new IterationData(true));

    protected GHGQLPagesLoader(GithubApiRequestExecutor executor, Function<GraphQLRequestPagination, GithubApiRequest.Post<T>> requestProducer) {
        this(executor, requestProducer, false);
    }

    protected GHGQLPagesLoader(GithubApiRequestExecutor executor,
                               Function<GraphQLRequestPagination, GithubApiRequest.Post<T>> requestProducer,
                               boolean supportsTimestampUpdates) {
        this(executor, requestProducer, supportsTimestampUpdates, GithubRequestPagination.DEFAULT_PAGE_SIZE);
    }

    protected GHGQLPagesLoader(GithubApiRequestExecutor executor,
                               Function<GraphQLRequestPagination, GithubApiRequest.Post<T>> requestProducer,
                               boolean supportsTimestampUpdates,
                               int pageSize) {
        myExecutor = executor;
        myRequestProducer = requestProducer;
        mySupportsTimestampUpdates = supportsTimestampUpdates;
        myPageSize = pageSize;
    }

    public boolean getHasNext() {
        return myIterationDataRef.get().myHasNext;
    }

    public @Nullable R loadNext(ProgressIndicator progressIndicator) throws IOException {
        return loadNext(progressIndicator, false);
    }

    public synchronized @Nullable R loadNext(ProgressIndicator progressIndicator, boolean update) throws IOException {
        IterationData iterationData = myIterationDataRef.get();

        GraphQLRequestPagination pagination;
        if (update) {
            if (getHasNext() || !mySupportsTimestampUpdates) {
                return null;
            }
            pagination = new GraphQLRequestPagination(iterationData.myTimestamp, myPageSize);
        }
        else {
            if (!getHasNext()) {
                return null;
            }
            pagination = new GraphQLRequestPagination(iterationData.myCursor, myPageSize);
        }

        Date executionDate = new Date();
        T response = myExecutor.execute(progressIndicator, myRequestProducer.apply(pagination));
        GraphQLCursorPageInfoDTO page = extractPageInfo(response);
        IterationData nextIteration = page != null
            ? new IterationData(page.getHasNextPage(), executionDate, page.getEndCursor())
            : new IterationData(false, null, null);
        myIterationDataRef.compareAndSet(iterationData, nextIteration);

        return extractResult(response);
    }

    public void reset() {
        myIterationDataRef.set(new IterationData(true));
    }

    protected abstract @Nullable GraphQLCursorPageInfoDTO extractPageInfo(T result);

    protected abstract R extractResult(T result);

    private static final class IterationData {
        private final boolean myHasNext;
        private final @Nullable Date myTimestamp;
        private final @Nullable String myCursor;

        private IterationData(boolean hasNext) {
            this(hasNext, null, null);
        }

        private IterationData(boolean hasNext, @Nullable Date timestamp, @Nullable String cursor) {
            myHasNext = hasNext;
            myTimestamp = timestamp;
            myCursor = cursor;
        }
    }
}
