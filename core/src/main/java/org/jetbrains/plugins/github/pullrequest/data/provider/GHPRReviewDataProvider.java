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
package org.jetbrains.plugins.github.pullrequest.data.provider;

import consulo.diff.util.Side;
import consulo.disposer.Disposable;
import consulo.review.async.ComputationState;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread;
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface GHPRReviewDataProvider {
    //TODO: move to a shared VM
    MutableObservableValue<String> getPendingReviewComment();

    void addPendingReviewNeedsReloadListener(Disposable parentDisposable, Runnable listener);

    void addThreadsNeedReloadListener(Disposable parentDisposable, Runnable listener);

    void addParticipantsNeedReloadListener(Disposable parentDisposable, Runnable listener);

    CompletableFuture<@Nullable GHPullRequestPendingReview> loadPendingReview();

    CompletableFuture<List<GHPullRequestReviewThread>> loadThreads();

    void signalPendingReviewNeedsReload();

    void signalThreadsNeedReload();

    CompletableFuture<GHPullRequestPendingReview> createReview(GHPullRequestReviewEvent event, @Nullable String body);

    CompletableFuture<?> submitReview(String reviewId, GHPullRequestReviewEvent event, @Nullable String body);

    CompletableFuture<GHPullRequestPendingReview> createReview(@Nullable GHPullRequestReviewEvent event,
                                                               @Nullable String body,
                                                               @Nullable String commitSha,
                                                               @Nullable List<GHPullRequestDraftReviewThread> threads);

    CompletableFuture<String> updateReviewBody(String reviewId, String newText);

    CompletableFuture<?> deleteReview(String reviewId);

    boolean canComment();

    /**
     * Only for cases when the comment is created on the diff of an individual commit and there's an active review.
     */
    CompletableFuture<GHPullRequestReviewComment> addComment(String reviewId,
                                                             String body,
                                                             String commitSha,
                                                             String fileName,
                                                             GHPRReviewCommentLocation location);

    CompletableFuture<GHPullRequestReviewComment> addComment(String replyToCommentId, String body);

    CompletableFuture<?> deleteComment(String commentId);

    CompletableFuture<GHPullRequestReviewComment> updateComment(String commentId, String newText);

    CompletableFuture<GHPullRequestReviewThread> createThread(String reviewId,
                                                              String body,
                                                              int line,
                                                              Side side,
                                                              int startLine,
                                                              Side startSide,
                                                              String fileName);

    CompletableFuture<GHPullRequestReviewThread> resolveThread(String id);

    CompletableFuture<GHPullRequestReviewThread> unresolveThread(String id);

    CompletableFuture<?> participantsBatches(Consumer<List<GHUser>> consumer);

    default ComputationState<@Nullable GHPullRequestPendingReview> pendingReviewComputationFlow(Disposable parentDisposable) {
        return ComputationState.create(parentDisposable, this::addPendingReviewNeedsReloadListener, this::loadPendingReview);
    }

    default ComputationState<List<GHPullRequestReviewThread>> threadsComputationFlow(Disposable parentDisposable) {
        return ComputationState.create(parentDisposable, this::addThreadsNeedReloadListener, this::loadThreads);
    }
}
