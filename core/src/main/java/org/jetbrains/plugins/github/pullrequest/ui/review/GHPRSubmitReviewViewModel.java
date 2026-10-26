// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.review;

import consulo.review.submit.CodeReviewSubmitViewModel;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent;

public interface GHPRSubmitReviewViewModel extends CodeReviewSubmitViewModel {
    /**
     * If the current user is the author of the pull request
     */
    boolean getViewerIsAuthor();

    /**
     * If there's a currently pending review
     */
    boolean getHasPendingReview();

    /**
     * Whether the review is being done in a dedicated Git worktree that can be removed after submitting
     * (controls visibility of the "delete worktree after submit" option).
     */
    boolean getCanDeleteWorktree();

    /**
     * Whether the review worktree should be removed after a successful submit. Meaningful only when {@link #getCanDeleteWorktree()}.
     */
    ObservableValue<Boolean> getDeleteWorktreeAfterSubmit();

    /**
     * Update {@link #getDeleteWorktreeAfterSubmit()}.
     */
    void setDeleteWorktreeAfterSubmit(boolean value);

    /**
     * Discard the current pending review
     */
    void discard();

    /**
     * Submit the review with a specified event
     */
    void submit(GHPullRequestReviewEvent event);
}
