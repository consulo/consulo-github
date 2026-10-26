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
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.proxy.EventDispatcher;
import consulo.review.async.BatchesLoader;
import consulo.review.async.LoaderWithMutableCache;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.change.patch.PatchHunkUtil;
import consulo.versionControlSystem.change.patch.TextFilePatch;
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReviewDTO;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class GHPRReviewDataProviderImpl implements GHPRReviewDataProvider {
    private static final Logger LOG = Logger.getInstance(GHPRReviewDataProviderImpl.class);

    private final GHPRReviewService myReviewService;
    private final GHPRChangesDataProvider myChangesProvider;
    private final GHPRIdentifier myPullRequestId;
    private final EventDispatcher<GHPRDataOperationsListener> myMessageBus;

    private final MutableObservableValue<String> myPendingReviewComment = MutableObservableValue.of("");

    private final LoaderWithMutableCache<List<GHPullRequestReviewThread>> myThreadsLoader;
    private final LoaderWithMutableCache<@Nullable GHPullRequestPendingReview> myReviewLoader;
    private final List<Runnable> myParticipantsNeedReloadListeners = new CopyOnWriteArrayList<>();
    private final BatchesLoader<GHUser> myParticipantsBatchesLoader;

    public GHPRReviewDataProviderImpl(Disposable parentDisposable,
                                      GHPRReviewService reviewService,
                                      GHPRChangesDataProvider changesProvider,
                                      GHPRIdentifier pullRequestId,
                                      EventDispatcher<GHPRDataOperationsListener> messageBus) {
        myReviewService = reviewService;
        myChangesProvider = changesProvider;
        myPullRequestId = pullRequestId;
        myMessageBus = messageBus;
        myThreadsLoader = new LoaderWithMutableCache<>(parentDisposable, () -> myReviewService.loadReviewThreads(myPullRequestId));
        myReviewLoader = new LoaderWithMutableCache<>(parentDisposable, () -> myReviewService.loadPendingReview(myPullRequestId)
            .thenApply(review -> review == null ? null : toModel(review)));
        myParticipantsBatchesLoader = new BatchesLoader<>(parentDisposable,
            consumer -> myReviewService.getReviewParticipantsBatchesFlow(myPullRequestId, consumer));
    }

    @Override
    public MutableObservableValue<String> getPendingReviewComment() {
        return myPendingReviewComment;
    }

    @Override
    public boolean canComment() {
        return myReviewService.canComment();
    }

    @Override
    public void addThreadsNeedReloadListener(Disposable parentDisposable, Runnable listener) {
        myThreadsLoader.addUpdatedListener(parentDisposable, listener);
    }

    @Override
    public CompletableFuture<List<GHPullRequestReviewThread>> loadThreads() {
        return myThreadsLoader.load();
    }

    @Override
    public void signalThreadsNeedReload() {
        myThreadsLoader.clearCache();
        refreshParticipants();
    }

    @Override
    public void addPendingReviewNeedsReloadListener(Disposable parentDisposable, Runnable listener) {
        myReviewLoader.addUpdatedListener(parentDisposable, listener);
    }

    @Override
    public CompletableFuture<@Nullable GHPullRequestPendingReview> loadPendingReview() {
        return myReviewLoader.load();
    }

    @Override
    public void signalPendingReviewNeedsReload() {
        myReviewLoader.clearCache();
    }

    @Override
    public void addParticipantsNeedReloadListener(Disposable parentDisposable, Runnable listener) {
        myParticipantsNeedReloadListeners.add(listener);
        Disposer.register(parentDisposable, () -> myParticipantsNeedReloadListeners.remove(listener));
    }

    @Override
    public CompletableFuture<?> participantsBatches(Consumer<List<GHUser>> consumer) {
        return myParticipantsBatchesLoader.getBatches(consumer);
    }

    private void refreshParticipants() {
        myParticipantsBatchesLoader.cancel();
        myParticipantsNeedReloadListeners.forEach(Runnable::run);
    }

    // TODO: load created threads and add to the loaded
    @Override
    public CompletableFuture<GHPullRequestPendingReview> createReview(@Nullable GHPullRequestReviewEvent event,
                                                                      @Nullable String body,
                                                                      @Nullable String commitSha,
                                                                      @Nullable List<GHPullRequestDraftReviewThread> threads) {
        return myReviewService.createReview(myPullRequestId, event, body, commitSha, threads).thenApply(dto -> {
            GHPullRequestPendingReview review = toModel(dto);
            if (event == null) {
                myReviewLoader.overrideResult(review);
            }
            if (threads != null && !threads.isEmpty()) {
                signalThreadsNeedReload();
            }
            myMessageBus.getMulticaster().onReviewsChanged();
            return review;
        });
    }

    @Override
    public CompletableFuture<GHPullRequestPendingReview> createReview(GHPullRequestReviewEvent event, @Nullable String body) {
        return myReviewService.createReview(myPullRequestId, event, body, null, null).thenApply(dto -> {
            GHPullRequestPendingReview review = toModel(dto);
            myMessageBus.getMulticaster().onReviewsChanged();
            return review;
        });
    }

    // TODO: change loaded threads statuses
    @Override
    public CompletableFuture<?> submitReview(String reviewId, GHPullRequestReviewEvent event, @Nullable String body) {
        return myReviewService.submitReview(myPullRequestId, reviewId, event, body).thenApply(ignored -> {
            signalPendingReviewNeedsReload();
            signalThreadsNeedReload();
            myMessageBus.getMulticaster().onReviewsChanged();
            return null;
        });
    }

    // TODO: remove loaded threads
    @Override
    public CompletableFuture<?> deleteReview(String reviewId) {
        return myReviewService.deleteReview(myPullRequestId, reviewId).thenApply(ignored -> {
            updateReview(reviewId, it -> null);
            signalThreadsNeedReload();
            myMessageBus.getMulticaster().onReviewsChanged();
            return null;
        });
    }

    @Override
    public CompletableFuture<String> updateReviewBody(String reviewId, String newText) {
        return myReviewService.updateReviewBody(reviewId, newText).thenApply(review -> {
            myMessageBus.getMulticaster().onReviewUpdated(reviewId, newText);
            return review.getBody();
        });
    }

    @Override
    public CompletableFuture<GHPullRequestReviewComment> addComment(String reviewId,
                                                                    String body,
                                                                    String commitSha,
                                                                    String fileName,
                                                                    GHPRReviewCommentLocation location) {
        /*
        The GraphQL threads API doesn't allow specifying the commit
        (https://docs.github.com/en/graphql/reference/input-objects#addpullrequestreviewthreadinput)
        The REST commits API doesn't allow specifying the review ID and fails when there's a draft review.
        (https://docs.github.com/en/rest/pulls/comments?apiVersion=2022-11-28#create-a-review-comment-for-a-pull-request)

        So the only way to do that reliably is to use a super-old deprecated way of passing so-called "position" to GQL comments API
        (https://docs.github.com/en/graphql/reference/input-objects#addpullrequestreviewcommentinput)

        Position is the index of the line in the raw diff between the merge base and the commit.
        */
        return myChangesProvider.loadPatchFromMergeBase(commitSha, fileName).thenCompose(patch -> {
            if (!(patch instanceof TextFilePatch textPatch)) {
                throw new IllegalStateException("Cannot find diff between " + commitSha + " and merge base");
            }
            Integer position = PatchHunkUtil.findDiffFileLineIndex(textPatch, Pair.create(location.side(), location.lineIdx()));
            if (position == null) {
                throw new IllegalStateException("Can't map file line to diff");
            }
            return myReviewService.addComment(reviewId, body, commitSha, fileName, position);
        }).thenApply(comment -> {
            updateReview(reviewId, it -> new GHPullRequestPendingReview(it.id(), it.state(), it.commentsCount() + 1));
            signalThreadsNeedReload();
            myMessageBus.getMulticaster().onReviewsChanged();
            return comment;
        });
    }

    // TODO: add comment to a loaded thread
    @Override
    public CompletableFuture<GHPullRequestReviewComment> addComment(String replyToCommentId, String body) {
        return loadPendingReview().thenCompose(pendingReview -> {
            String reviewId = pendingReview == null ? null : pendingReview.id();
            if (reviewId == null) {
                // not having a review will produce a security error
                return myReviewService.createReview(myPullRequestId).thenCompose(review ->
                    myReviewService.addComment(myPullRequestId, review.getId(), replyToCommentId, body).thenCompose(comment ->
                        myReviewService.submitReview(myPullRequestId, review.getId(), GHPullRequestReviewEvent.COMMENT, null)
                            .thenApply(ignored -> comment)));
            }
            return myReviewService.addComment(myPullRequestId, reviewId, replyToCommentId, body).thenApply(comment -> {
                updateReview(reviewId, it -> new GHPullRequestPendingReview(it.id(), it.state(), it.commentsCount() + 1));
                return comment;
            });
        }).thenApply(comment -> {
            signalThreadsNeedReload();
            myMessageBus.getMulticaster().onReviewsChanged();
            return comment;
        });
    }

    @Override
    public CompletableFuture<?> deleteComment(String commentId) {
        return myReviewService.deleteComment(myPullRequestId, commentId).thenApply(commentReview -> {
            // if deleted from current pending review, potentially clear the review
            updateReview(commentReview.getId(), it -> commentReview.getComments().getTotalCount() > 0 ? toModel(commentReview) : null);
            updateThreads(it -> removeComment(it, commentId));
            signalPendingReviewNeedsReload();
            return null;
        });
    }

    @Override
    public CompletableFuture<GHPullRequestReviewComment> updateComment(String commentId, String newText) {
        return myReviewService.updateComment(myPullRequestId, commentId, newText).thenApply(comment -> {
            updateThreads(it -> updateCommentBody(it, commentId, comment.getBody()));
            return comment;
        });
    }

    @Override
    public CompletableFuture<GHPullRequestReviewThread> createThread(String reviewId,
                                                                     String body,
                                                                     int line,
                                                                     Side side,
                                                                     int startLine,
                                                                     Side startSide,
                                                                     String fileName) {
        return myReviewService.addThread(reviewId, body, line, side, startLine, startSide, fileName).thenApply(thread -> {
            updateThreads(it -> {
                List<GHPullRequestReviewThread> result = new ArrayList<>(it);
                result.add(thread);
                return result;
            });
            updateReview(reviewId, it -> new GHPullRequestPendingReview(it.id(), it.state(), it.commentsCount() + 1));
            return thread;
        }).whenComplete((thread, error) -> myMessageBus.getMulticaster().onReviewsChanged());
    }

    @Override
    public CompletableFuture<GHPullRequestReviewThread> resolveThread(String id) {
        return myReviewService.resolveThread(myPullRequestId, id).thenApply(thread -> {
            updateThreads(list -> list.stream().map(it -> it.getId().equals(thread.getId()) ? thread : it).toList());
            return thread;
        });
    }

    @Override
    public CompletableFuture<GHPullRequestReviewThread> unresolveThread(String id) {
        return myReviewService.unresolveThread(myPullRequestId, id).thenApply(thread -> {
            updateThreads(list -> list.stream().map(it -> it.getId().equals(thread.getId()) ? thread : it).toList());
            return thread;
        });
    }

    private void updateReview(UnaryOperator<@Nullable GHPullRequestPendingReview> updater) {
        myReviewLoader.updateLoaded(it -> {
            try {
                return updater.apply(it);
            }
            catch (Exception e) {
                LOG.warn("Failed to update pending review data after mutation", e);
                return it;
            }
        });
    }

    private void updateReview(String reviewId, Function<GHPullRequestPendingReview, @Nullable GHPullRequestPendingReview> updater) {
        updateReview(it -> it != null && it.id().equals(reviewId) ? updater.apply(it) : it);
    }

    private void updateThreads(UnaryOperator<List<GHPullRequestReviewThread>> updater) {
        myThreadsLoader.updateLoaded(it -> {
            try {
                return updater.apply(it);
            }
            catch (Exception e) {
                LOG.warn("Failed to update review threads data after mutation", e);
                return it;
            }
        });
    }

    static GHPullRequestPendingReview toModel(GHPullRequestPendingReviewDTO dto) {
        return new GHPullRequestPendingReview(dto.getId(), dto.getState(), dto.getComments().getTotalCount());
    }

    private static List<GHPullRequestReviewThread> updateCommentBody(List<GHPullRequestReviewThread> threads, String commentId, String newBody) {
        return threads.stream().map(it -> it.withComments(it.getComments().stream()
            .map(comment -> comment.getId().equals(commentId) ? comment.withBody(newBody) : comment)
            .toList())).toList();
    }

    private static List<GHPullRequestReviewThread> removeComment(List<GHPullRequestReviewThread> threads, String commentId) {
        List<GHPullRequestReviewThread> result = new ArrayList<>();
        for (GHPullRequestReviewThread thread : threads) {
            List<GHPullRequestReviewComment> comments = thread.getComments().stream().filter(comment -> !comment.getId().equals(commentId)).toList();
            if (!comments.isEmpty()) {
                result.add(thread.withComments(comments));
            }
        }
        return result;
    }
}
