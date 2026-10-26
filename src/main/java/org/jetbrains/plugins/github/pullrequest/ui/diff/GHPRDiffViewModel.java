// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.diff;

import consulo.review.diff.model.CodeReviewDiscussionsViewModel;
import consulo.review.icon.IconsProvider;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.dataholder.Key;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.diff.UnifiedCodeReviewItemPosition;
import consulo.versionControlSystem.review.diff.model.CodeReviewDiffProcessorViewModel;
import consulo.versionControlSystem.review.util.ChangesSelection;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel;
import org.jspecify.annotations.Nullable;

public interface GHPRDiffViewModel extends CodeReviewDiffProcessorViewModel<GHPRDiffChangeViewModel>, CodeReviewDiscussionsViewModel {
    Key<GHPRDiffViewModel> KEY = Key.create("GitHub.PullRequest.Diff.ViewModel");
    Key<GHPRDiffViewModel> DATA_KEY = Key.create(KEY.toString());

    GHPRReviewViewModel getReviewVm();

    ObservableValue<Boolean> isLoadingReviewData();

    IconsProvider<String> getIconProvider();

    GHUser getCurrentUser();

    ObservableValue<@Nullable GHPRDiffReviewViewModel> getViewModelFor(RefComparisonChange change);

    void reloadReview();

    /**
     * Tries to find the next comment, given that a comment is currently focused.
     *
     * @return The ID of the next comment to move to, or `null` if no next comment could be found.
     */
    @Nullable
    String nextComment(String focused);

    /**
     * Tries to find the next comment, given that no comment is currently focused.
     *
     * @return The ID of the next comment to move to, or `null` if no next comment could be found.
     */
    @Nullable
    String nextComment(UnifiedCodeReviewItemPosition cursorLocation);

    /**
     * Tries to find the previous comment, given that a comment is currently focused.
     *
     * @return The ID of the previous comment to move to, or `null` if no previous comment could be found.
     */
    @Nullable
    String previousComment(String focused);

    /**
     * Tries to find the previous comment, given that no comment is currently focused.
     *
     * @return The ID of the previous comment to move to, or `null` if no previous comment could be found.
     */
    @Nullable
    String previousComment(UnifiedCodeReviewItemPosition cursorLocation);

    void showDiffFor(ChangesSelection changes);

    void showDiffAtComment(String commentId);
}
