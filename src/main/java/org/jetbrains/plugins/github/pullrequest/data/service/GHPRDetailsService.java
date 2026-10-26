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

import consulo.review.util.CollectionDelta;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public interface GHPRDetailsService {
    CompletableFuture<@Nullable GHPRIdentifier> findPRId(long number);

    CompletableFuture<GHPullRequest> loadDetails(GHPRIdentifier pullRequestId);

    CompletableFuture<GHPullRequest> updateDetails(GHPRIdentifier pullRequestId, @Nullable String title, @Nullable String description);

    CompletableFuture<?> adjustReviewers(GHPRIdentifier pullRequestId, CollectionDelta<GHPullRequestRequestedReviewer> delta);

    CompletableFuture<?> adjustAssignees(GHPRIdentifier pullRequestId, CollectionDelta<GHUser> delta);

    CompletableFuture<?> adjustLabels(GHPRIdentifier pullRequestId, CollectionDelta<GHLabel> delta);

    CompletableFuture<GHPRMergeabilityState> loadMergeabilityState(GHPRIdentifier pullRequestId);

    CompletableFuture<?> close(GHPRIdentifier pullRequestId);

    CompletableFuture<?> reopen(GHPRIdentifier pullRequestId);

    CompletableFuture<?> markReadyForReview(GHPRIdentifier pullRequestId);

    CompletableFuture<?> merge(GHPRIdentifier pullRequestId, Pair<String, String> commitMessage, String currentHeadRef);

    CompletableFuture<?> rebaseMerge(GHPRIdentifier pullRequestId, String currentHeadRef);

    CompletableFuture<?> squashMerge(GHPRIdentifier pullRequestId, Pair<String, String> commitMessage, String currentHeadRef);

    CompletableFuture<?> deleteMergedBranch(GHPRIdentifier pullRequestId, String refId);
}
