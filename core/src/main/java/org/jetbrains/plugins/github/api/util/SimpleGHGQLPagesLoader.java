// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util;

import consulo.application.progress.ProgressIndicator;
import consulo.http.graphql.GraphQLRequestPagination;
import consulo.http.graphql.dto.GraphQLCursorPageInfoDTO;
import consulo.http.graphql.dto.GraphQLPagedResponseDataDTO;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class SimpleGHGQLPagesLoader<T> extends GHGQLPagesLoader<@Nullable GraphQLPagedResponseDataDTO<T>, @Nullable List<T>> {
    public SimpleGHGQLPagesLoader(GithubApiRequestExecutor executor,
                                  Function<GraphQLRequestPagination, GithubApiRequest.Post<@Nullable GraphQLPagedResponseDataDTO<T>>> requestProducer) {
        this(executor, requestProducer, false);
    }

    public SimpleGHGQLPagesLoader(GithubApiRequestExecutor executor,
                                  Function<GraphQLRequestPagination, GithubApiRequest.Post<@Nullable GraphQLPagedResponseDataDTO<T>>> requestProducer,
                                  boolean supportsTimestampUpdates) {
        this(executor, requestProducer, supportsTimestampUpdates, GithubRequestPagination.DEFAULT_PAGE_SIZE);
    }

    public SimpleGHGQLPagesLoader(GithubApiRequestExecutor executor,
                                  Function<GraphQLRequestPagination, GithubApiRequest.Post<@Nullable GraphQLPagedResponseDataDTO<T>>> requestProducer,
                                  boolean supportsTimestampUpdates,
                                  int pageSize) {
        super(executor, requestProducer, supportsTimestampUpdates, pageSize);
    }

    public List<T> loadAll(ProgressIndicator progressIndicator) throws IOException {
        List<T> list = new ArrayList<>();
        while (getHasNext()) {
            List<T> next = loadNext(progressIndicator);
            if (next != null) {
                list.addAll(next);
            }
        }
        return list;
    }

    @Override
    protected @Nullable GraphQLCursorPageInfoDTO extractPageInfo(@Nullable GraphQLPagedResponseDataDTO<T> result) {
        return result == null ? null : result.getPageInfo();
    }

    @Override
    protected @Nullable List<T> extractResult(@Nullable GraphQLPagedResponseDataDTO<T> result) {
        return result == null ? null : result.getNodes();
    }
}
