// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.details.model;

import consulo.review.details.data.ReviewRole;
import consulo.review.details.data.ReviewState;
import consulo.review.details.model.CodeReviewFlowViewModel;
import consulo.ui.RelativePoint2D;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRReviewerState;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface GHPRReviewFlowViewModel extends CodeReviewFlowViewModel<GHPullRequestRequestedReviewer>, GHPRReviewViewModel {
    ObservableValue<Boolean> isBusy();

    ObservableValue<List<GHPullRequestRequestedReviewer>> getRequestedReviewers();

    ObservableValue<Map<GHPullRequestRequestedReviewer, GHPRReviewerState>> getReviewerReviewStates();

    ObservableValue<@Nullable GHPRReviewerState> getCurrentUserReviewState();

    ObservableValue<ReviewState> getReviewState();

    ObservableValue<ReviewRole> getRole();

    ObservableValue<Integer> getPendingComments();

    /**
     * Represents the repository restrictions for pull requests
     */
    RepositoryRestrictions getRepositoryRestrictions();

    /**
     * Flag indicating whether the user can perform all actions related to manage the review
     */
    boolean getUserCanManageReview();

    /**
     * Flag indicating whether the user can perform all actions related to merge the review
     */
    boolean getUserCanMergeReview();

    /**
     * Determines that all the conditions for "Merge" action are done
     */
    ObservableValue<Boolean> isMergeEnabled();

    /**
     * Determines that all the conditions for "Squash and Merge" action are done
     */
    ObservableValue<Boolean> isSquashMergeEnabled();

    /**
     * Determines that all the conditions for "Rebase" action are done
     */
    ObservableValue<Boolean> isRebaseEnabled();

    void mergeReview();

    void rebaseReview();

    void squashAndMergeReview();

    void closeReview();

    void reopenReview();

    void postDraftedReview();

    void removeReviewer(GHPullRequestRequestedReviewer reviewer);

    void requestReview(RelativePoint2D point);

    void reRequestReview();

    void reRequestReview(GHPullRequestRequestedReviewer reviewer);

    void setMyselfAsReviewer();
}
