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
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent;
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

public interface GHPRReviewService {
    boolean canComment();

    CompletableFuture<@Nullable GHPullRequestPendingReviewDTO> loadPendingReview(GHPRIdentifier pullRequestId);

    CompletableFuture<List<GHPullRequestReviewThread>> loadReviewThreads(GHPRIdentifier pullRequestId);

    default CompletableFuture<GHPullRequestPendingReviewDTO> createReview(GHPRIdentifier pullRequestId) {
        return createReview(pullRequestId, null, null, null, null);
    }

    CompletableFuture<GHPullRequestPendingReviewDTO> createReview(GHPRIdentifier pullRequestId,
                                                                  @Nullable GHPullRequestReviewEvent event,
                                                                  @Nullable String body,
                                                                  @Nullable String commitSha,
                                                                  @Nullable List<GHPullRequestDraftReviewThread> threads);

    CompletableFuture<?> submitReview(GHPRIdentifier pullRequestId, String reviewId, GHPullRequestReviewEvent event, @Nullable String body);

    CompletableFuture<GHPullRequestReview> updateReviewBody(String reviewId, String newText);

    CompletableFuture<?> deleteReview(GHPRIdentifier pullRequestId, String reviewId);

    CompletableFuture<GHPullRequestReviewComment> addComment(GHPRIdentifier pullRequestId, String reviewId, String replyToCommentId, String body);

    CompletableFuture<GHPullRequestReviewComment> addComment(String reviewId, String body, String commitSha, String fileName, int diffLine);

    CompletableFuture<GHPullRequestReviewThread> addThread(String reviewId,
                                                           String body,
                                                           int line,
                                                           Side side,
                                                           int startLine,
                                                           Side startSide,
                                                           String fileName);

    CompletableFuture<GHPullRequestPendingReviewDTO> deleteComment(GHPRIdentifier pullRequestId, String commentId);

    CompletableFuture<GHPullRequestReviewComment> updateComment(GHPRIdentifier pullRequestId, String commentId, String newText);

    CompletableFuture<GHPullRequestReviewThread> resolveThread(GHPRIdentifier pullRequestId, String id);

    CompletableFuture<GHPullRequestReviewThread> unresolveThread(GHPRIdentifier pullRequestId, String id);

    CompletableFuture<?> getReviewParticipantsBatchesFlow(GHPRIdentifier pullRequestId, Consumer<List<GHUser>> consumer);
}
