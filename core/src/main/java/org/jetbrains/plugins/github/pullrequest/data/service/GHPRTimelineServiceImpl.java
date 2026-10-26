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
package org.jetbrains.plugins.github.pullrequest.data.service;

import consulo.http.graphql.GraphQLRequestPagination;
import consulo.http.graphql.dto.GraphQLPagedResponseDataDTO;
import consulo.logging.Logger;
import consulo.review.util.ComputableSequence;
import consulo.review.util.SequenceItem;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static consulo.review.util.ResultUtil.processErrorAndGet;
import static consulo.review.util.ResultUtil.runCatchingUser;

public class GHPRTimelineServiceImpl implements GHPRTimelineService {
    private static final Logger LOG = Logger.getInstance(GHPRTimelineServiceImpl.class);

    private final GithubApiRequestExecutor myRequestExecutor;
    private final GHRepositoryCoordinates myRepository;

    public GHPRTimelineServiceImpl(GithubApiRequestExecutor requestExecutor, GHRepositoryCoordinates repository) {
        myRequestExecutor = requestExecutor;
        myRepository = repository;
    }

    @Override
    public ComputableSequence<SequenceItem<List<GHPRTimelineItem>>> getTimelineItems(GHPRIdentifier id) {
        return ComputableSequence.<String, SequenceItem<List<GHPRTimelineItem>>>byCursor(null, lastCursor -> {
            GraphQLRequestPagination pagination = new GraphQLRequestPagination(lastCursor);
            return loadPage(id, pagination).thenApply(page -> {
                if (page.getNodes().isEmpty()) {
                    return null;
                }

                String endCursor = page.getPageInfo().getEndCursor();
                if (endCursor == null) {
                    LOG.warn("Non-empty list of items " + page.getNodes() + ", but the last cursor is null. Won't load any more items.");
                    return null;
                }
                boolean hasMorePages = page.getPageInfo().getHasNextPage();

                return Pair.create(SequenceItem.of(page.getNodes(), !hasMorePages), endCursor);
            });
        });
    }

    private CompletableFuture<GraphQLPagedResponseDataDTO<GHPRTimelineItem>> loadPage(GHPRIdentifier id, GraphQLRequestPagination pagination) {
        GithubApiRequest<GraphQLPagedResponseDataDTO<GHPRTimelineItem>> request = GHGQLRequests.PullRequest.Timeline.items(myRepository.getServerPath(),
            myRepository.getRepositoryPath().getOwner(),
            myRepository.getRepositoryPath().getRepository(),
            id.number(), pagination);
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(request)),
            e -> LOG.info("Error occurred while loading PR " + id + " timeline items page " + pagination, e));
    }
}
