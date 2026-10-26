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
import consulo.review.async.ComputationState;
import consulo.review.util.CollectionDelta;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface GHPRDetailsDataProvider {
    @Nullable
    GHPullRequest getLoadedDetails();

    void addStateChangeListener(Disposable parentDisposable, Runnable listener);

    void addDetailsNeedReloadListener(Disposable parentDisposable, Runnable listener);

    void addMergeabilityNeedsReloadListener(Disposable parentDisposable, Runnable listener);

    CompletableFuture<GHPullRequest> loadDetails();

    CompletableFuture<GHPRMergeabilityState> loadMergeabilityState();

    default CompletableFuture<GHPullRequest> updateDetails() {
        return updateDetails(null, null);
    }

    CompletableFuture<GHPullRequest> updateDetails(@Nullable String title, @Nullable String description);

    CompletableFuture<?> adjustReviewers(CollectionDelta<GHPullRequestRequestedReviewer> delta);

    CompletableFuture<?> close();

    CompletableFuture<?> reopen();

    CompletableFuture<?> markReadyForReview();

    CompletableFuture<?> merge(Pair<String, String> commitMessage, String currentHeadRef);

    CompletableFuture<?> rebaseMerge(String currentHeadRef);

    CompletableFuture<?> squashMerge(Pair<String, String> commitMessage, String currentHeadRef);

    CompletableFuture<?> deleteMergedBranch(String refId);

    void signalDetailsNeedReload();

    void signalMergeabilityNeedsReload();

    default ComputationState<GHPullRequest> detailsComputationFlow(Disposable parentDisposable) {
        return ComputationState.create(parentDisposable, this::addDetailsNeedReloadListener, this::loadDetails);
    }

    default ComputationState<GHPRMergeabilityState> mergeabilityStateComputationFlow(Disposable parentDisposable) {
        return ComputationState.create(parentDisposable, this::addMergeabilityNeedsReloadListener, this::loadMergeabilityState);
    }
}
