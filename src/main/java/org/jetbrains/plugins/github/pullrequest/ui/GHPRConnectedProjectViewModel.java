// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui;

import consulo.disposer.Disposable;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort;
import org.jetbrains.plugins.github.pullrequest.GHPRListViewModel;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewInEditorViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRBranchWidgetViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRInfoViewModel;
import org.jspecify.annotations.Nullable;

public interface GHPRConnectedProjectViewModel {
    String getConnectionId();

    GHRepositoryCoordinates getRepository();

    GHPRDataContext getDataContext();

    GHPRListViewModel getListVm();

    ObservableValue<@Nullable ComputedResult<@Nullable GHPRIdentifier>> getPrOnCurrentBranch();

    @Nullable
    GHPRCreateViewModel getCreateVmOrNull();

    GHPRInfoViewModel acquireInfoViewModel(GHPRIdentifier id, Disposable hostCs);

    GHPRReviewInEditorViewModel acquireEditorReviewViewModel(GHPRIdentifier id, Disposable hostCs);

    GHPRBranchWidgetViewModel acquireBranchWidgetModel(GHPRIdentifier id, Disposable hostCs);

    GHPRDiffViewModel acquireDiffViewModel(GHPRIdentifier id, Disposable hostCs);

    GHPRTimelineViewModel acquireTimelineViewModel(GHPRIdentifier id, Disposable hostCs);

    @Nullable
    GHPullRequestShort findDetails(GHPRIdentifier id);

    /**
     * Whether the given pull request's branch is currently checked out in this project.
     */
    boolean isCheckedOut(GHPRIdentifier id);

    /**
     * Whether a pull request branch can be checked out into a new Git worktree for the current repository.
     */
    boolean getCanCheckoutInNewWorktree();

    /**
     * Fetch and checkout the head branch of the given pull request into the current worktree.
     */
    void checkoutPullRequest(GHPRIdentifier id);

    /**
     * Fetch and checkout the head branch of the given pull request into a new Git worktree.
     */
    void checkoutPullRequestInNewWorktree(GHPRIdentifier id);

    default void createPullRequest() {
        createPullRequest(true);
    }

    void createPullRequest(boolean requestFocus);

    default void viewList() {
        viewList(true);
    }

    void viewList(boolean requestFocus);

    default void viewPullRequest(GHPRIdentifier id) {
        viewPullRequest(id, true);
    }

    void viewPullRequest(GHPRIdentifier id, boolean requestFocus);

    void openPullRequestInfoAndTimeline(long number);

    void openPullRequestInfoAndDiff(GHPRIdentifier id);

    void openPullRequestTimeline(GHPRIdentifier id, boolean requestFocus);

    void openPullRequestDiff(@Nullable GHPRIdentifier id, boolean requestFocus);
}
