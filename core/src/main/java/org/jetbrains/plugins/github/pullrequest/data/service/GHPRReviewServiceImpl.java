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
package org.jetbrains.plugins.github.pullrequest.data.service;

import consulo.diff.util.Side;
import consulo.http.page.ApiPageUtil;
import consulo.logging.Logger;
import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent;
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReviewDTO;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static consulo.review.util.ResultUtil.processErrorAndGet;
import static consulo.review.util.ResultUtil.runCatchingUser;

public class GHPRReviewServiceImpl implements GHPRReviewService {
    private static final Logger LOG = Logger.getInstance(GHPRReviewService.class);

    private final GHPRSecurityService mySecurityService;
    private final GithubApiRequestExecutor myRequestExecutor;
    private final GHRepositoryCoordinates myRepository;

    public GHPRReviewServiceImpl(GHPRSecurityService securityService, GithubApiRequestExecutor requestExecutor, GHRepositoryCoordinates repository) {
        mySecurityService = securityService;
        myRequestExecutor = requestExecutor;
        myRepository = repository;
    }

    @Override
    public boolean canComment() {
        return mySecurityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ);
    }

    @Override
    public CompletableFuture<@Nullable GHPullRequestPendingReviewDTO> loadPendingReview(GHPRIdentifier pullRequestId) {
        return processErrorAndGet(runCatchingUser(() ->
                myRequestExecutor.execute(GHGQLRequests.PullRequest.Review.pendingReviews(myRepository.getServerPath(), pullRequestId.id()))
                    .thenApply(reviews -> {
                        List<GHPullRequestPendingReviewDTO> nodes = reviews.getNodes();
                        return nodes.size() == 1 ? nodes.get(0) : null;
                    })),
            e -> LOG.info("Error occurred while loading pending review", e));
    }

    @Override
    public CompletableFuture<List<GHPullRequestReviewThread>> loadReviewThreads(GHPRIdentifier pullRequestId) {
        return processErrorAndGet(runCatchingUser(() -> ApiPageUtil.foldGQLPagesToList(
                pagination -> myRequestExecutor.execute(GHGQLRequests.PullRequest.reviewThreads(myRepository, pullRequestId.number(), pagination)))),
            e -> LOG.info("Error occurred while loading review threads", e));
    }

    @Override
    public CompletableFuture<?> getReviewParticipantsBatchesFlow(GHPRIdentifier pullRequestId, Consumer<List<GHUser>> consumer) {
        return ApiPageUtil.collectGQLPages(
            pagination -> myRequestExecutor.execute(GHGQLRequests.PullRequest.findParticipants(myRepository, pullRequestId.number(), pagination)),
            page -> consumer.accept(page.getNodes()));
    }

    @Override
    public CompletableFuture<GHPullRequestPendingReviewDTO> createReview(GHPRIdentifier pullRequestId,
                                                                         @Nullable GHPullRequestReviewEvent event,
                                                                         @Nullable String body,
                                                                         @Nullable String commitSha,
                                                                         @Nullable List<GHPullRequestDraftReviewThread> threads) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GHGQLRequests.PullRequest.Review.create(myRepository.getServerPath(), pullRequestId.id(), event, body, commitSha, threads))),
            e -> LOG.info("Error occurred while creating review", e));
    }

    @Override
    public CompletableFuture<?> submitReview(GHPRIdentifier pullRequestId, String reviewId, GHPullRequestReviewEvent event, @Nullable String body) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GHGQLRequests.PullRequest.Review.submit(myRepository.getServerPath(), reviewId, event, body))),
            e -> LOG.info("Error occurred while submitting review", e));
    }

    @Override
    public CompletableFuture<GHPullRequestReview> updateReviewBody(String reviewId, String newText) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GHGQLRequests.PullRequest.Review.updateBody(myRepository.getServerPath(), reviewId, newText))),
            e -> LOG.info("Error occurred while updating review", e));
    }

    @Override
    public CompletableFuture<?> deleteReview(GHPRIdentifier pullRequestId, String reviewId) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GHGQLRequests.PullRequest.Review.delete(myRepository.getServerPath(), reviewId))),
            e -> LOG.info("Error occurred while deleting review", e));
    }

    @Override
    public CompletableFuture<GHPullRequestReviewComment> addComment(GHPRIdentifier pullRequestId,
                                                                    String reviewId,
                                                                    String replyToCommentId,
                                                                    String body) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GHGQLRequests.PullRequest.Review.addComment(myRepository.getServerPath(), reviewId, replyToCommentId, body))),
            e -> LOG.info("Error occurred while adding review thread reply", e));
    }

    @Override
    public CompletableFuture<GHPullRequestReviewComment> addComment(String reviewId, String body, String commitSha, String fileName, int diffLine) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GHGQLRequests.PullRequest.Review.addComment(myRepository.getServerPath(), reviewId, body, commitSha, fileName, diffLine))),
            e -> LOG.info("Error occurred while adding review comment", e));
    }

    @Override
    public CompletableFuture<GHPullRequestPendingReviewDTO> deleteComment(GHPRIdentifier pullRequestId, String commentId) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GHGQLRequests.PullRequest.Review.deleteComment(myRepository.getServerPath(), commentId))),
            e -> LOG.info("Error occurred while deleting review comment", e));
    }

    @Override
    public CompletableFuture<GHPullRequestReviewComment> updateComment(GHPRIdentifier pullRequestId, String commentId, String newText) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GHGQLRequests.PullRequest.Review.updateComment(myRepository.getServerPath(), commentId, newText))),
            e -> LOG.info("Error occurred while updating review comment", e));
    }

    @Override
    public CompletableFuture<GHPullRequestReviewThread> addThread(String reviewId,
                                                                  String body,
                                                                  int line,
                                                                  Side side,
                                                                  int startLine,
                                                                  Side startSide,
                                                                  String fileName) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(GHGQLRequests.PullRequest.Review
                .addThread(myRepository.getServerPath(), reviewId, body, line, side, startLine, startSide, fileName))),
            e -> LOG.info("Error occurred while adding review thread", e));
    }

    @Override
    public CompletableFuture<GHPullRequestReviewThread> resolveThread(GHPRIdentifier pullRequestId, String id) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GHGQLRequests.PullRequest.Review.resolveThread(myRepository.getServerPath(), id))),
            e -> LOG.info("Error occurred while resolving review thread", e));
    }

    @Override
    public CompletableFuture<GHPullRequestReviewThread> unresolveThread(GHPRIdentifier pullRequestId, String id) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GHGQLRequests.PullRequest.Review.unresolveThread(myRepository.getServerPath(), id))),
            e -> LOG.info("Error occurred while unresolving review thread", e));
    }
}
