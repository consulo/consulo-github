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

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.review.async.LoaderWithMutableCache;
import consulo.review.util.CollectionDelta;
import consulo.review.util.FutureUtil;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRDetailsService;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class GHPRDetailsDataProviderImpl implements GHPRDetailsDataProvider {
    private final GHPRDetailsService myDetailsService;
    private final GHPRIdentifier myPullRequestId;
    private final GHPRDataOperationsMessageBus myMessageBus;

    private final MutableObservableValue<@Nullable GHPullRequest> myLoadedDetailsState = MutableObservableValue.of(null);
    private final List<Runnable> myStateChangeListeners = new CopyOnWriteArrayList<>();

    private final LoaderWithMutableCache<GHPullRequest> myDetailsLoader;
    private final LoaderWithMutableCache<GHPRMergeabilityState> myMergeabilityLoader;

    public GHPRDetailsDataProviderImpl(Disposable parentDisposable,
                                       GHPRDetailsService detailsService,
                                       GHPRIdentifier pullRequestId,
                                       GHPRDataOperationsMessageBus messageBus) {
        myDetailsService = detailsService;
        myPullRequestId = pullRequestId;
        myMessageBus = messageBus;
        myDetailsLoader = new LoaderWithMutableCache<>(parentDisposable, () -> myDetailsService.loadDetails(myPullRequestId));
        myMergeabilityLoader = new LoaderWithMutableCache<>(parentDisposable, () -> myDetailsService.loadMergeabilityState(myPullRequestId));
    }

    public ObservableValue<@Nullable GHPullRequest> getLoadedDetailsState() {
        return myLoadedDetailsState;
    }

    @Override
    public @Nullable GHPullRequest getLoadedDetails() {
        return myLoadedDetailsState.get();
    }

    @Override
    public void addStateChangeListener(Disposable parentDisposable, Runnable listener) {
        myStateChangeListeners.add(listener);
        Disposer.register(parentDisposable, () -> myStateChangeListeners.remove(listener));
    }

    @Override
    public void addDetailsNeedReloadListener(Disposable parentDisposable, Runnable listener) {
        myDetailsLoader.addUpdatedListener(parentDisposable, listener);
    }

    @Override
    public void addMergeabilityNeedsReloadListener(Disposable parentDisposable, Runnable listener) {
        myMergeabilityLoader.addUpdatedListener(parentDisposable, listener);
    }

    @Override
    public CompletableFuture<GHPullRequest> loadDetails() {
        return myDetailsLoader.load().whenComplete((details, error) -> {
            if (error == null) {
                myLoadedDetailsState.set(details);
            }
        });
    }

    @Override
    public CompletableFuture<GHPRMergeabilityState> loadMergeabilityState() {
        return myMergeabilityLoader.load();
    }

    @Override
    public CompletableFuture<GHPullRequest> updateDetails(@Nullable String title, @Nullable String description) {
        return myDetailsService.updateDetails(myPullRequestId, title, description).thenCompose(details -> {
            myDetailsLoader.overrideResult(details);
            return myMessageBus.syncPublisher(GHPRDataOperationsListener::onMetadataChanged).thenApply(ignored -> details);
        }).copy();
    }

    @Override
    public CompletableFuture<?> adjustReviewers(CollectionDelta<GHPullRequestRequestedReviewer> delta) {
        return withReload(() -> myDetailsService.adjustReviewers(myPullRequestId, delta), false);
    }

    @Override
    public CompletableFuture<?> close() {
        return withReload(() -> myDetailsService.close(myPullRequestId), true);
    }

    @Override
    public CompletableFuture<?> reopen() {
        return withReload(() -> myDetailsService.reopen(myPullRequestId), true);
    }

    @Override
    public CompletableFuture<?> markReadyForReview() {
        return withReload(() -> myDetailsService.markReadyForReview(myPullRequestId), true);
    }

    @Override
    public CompletableFuture<?> merge(Pair<String, String> commitMessage, String currentHeadRef) {
        return withReload(() -> myDetailsService.merge(myPullRequestId, commitMessage, currentHeadRef), true);
    }

    @Override
    public CompletableFuture<?> rebaseMerge(String currentHeadRef) {
        return withReload(() -> myDetailsService.rebaseMerge(myPullRequestId, currentHeadRef), true);
    }

    @Override
    public CompletableFuture<?> squashMerge(Pair<String, String> commitMessage, String currentHeadRef) {
        return withReload(() -> myDetailsService.squashMerge(myPullRequestId, commitMessage, currentHeadRef), true);
    }

    @Override
    public CompletableFuture<?> deleteMergedBranch(String refId) {
        return withReload(() -> myDetailsService.deleteMergedBranch(myPullRequestId, refId), true);
    }

    private CompletableFuture<?> withReload(Supplier<CompletableFuture<?>> operation, boolean state) {
        CompletableFuture<?> result;
        try {
            result = operation.get();
        }
        catch (Throwable e) {
            result = CompletableFuture.failedFuture(e);
        }
        return result.handle((value, error) -> error)
            .thenCompose(error -> notifyNeedsReload(state).thenCompose(ignored -> error == null
                ? CompletableFuture.<@Nullable Object>completedFuture(null)
                : CompletableFuture.<@Nullable Object>failedFuture(FutureUtil.unwrap(error))))
            .copy();
    }

    private CompletableFuture<?> notifyNeedsReload(boolean state) {
        signalDetailsNeedReload();
        signalMergeabilityNeedsReload();
        return myMessageBus.syncPublisher(GHPRDataOperationsListener::onMetadataChanged).thenRun(() -> {
            if (state) {
                myStateChangeListeners.forEach(Runnable::run);
            }
        });
    }

    @Override
    public void signalDetailsNeedReload() {
        myDetailsLoader.clearCache();
    }

    @Override
    public void signalMergeabilityNeedsReload() {
        myMergeabilityLoader.clearCache();
    }
}
