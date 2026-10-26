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
package org.jetbrains.plugins.github.pullrequest.ui.timeline.item;

import consulo.diff.util.LineRange;
import consulo.project.Project;
import consulo.review.timeline.CollapsibleTimelineItemViewModel;
import consulo.review.timeline.thread.CodeReviewFoldableThreadViewModel;
import consulo.review.timeline.thread.CodeReviewResolvableItemViewModel;
import consulo.review.timeline.thread.CodeReviewThreadViewModel;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.messageList.AsyncHtmlImageLoader;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.change.patch.PatchHunk;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewThreadCommentViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewThreadViewModel;
import org.jetbrains.plugins.github.ui.util.GHHtmlPaneUtil;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.List;

public interface GHPRTimelineThreadViewModel extends GHPRReviewThreadViewModel,
    CollapsibleTimelineItemViewModel,
    CodeReviewFoldableThreadViewModel,
    CodeReviewResolvableItemViewModel,
    CodeReviewThreadViewModel {
    Project getProject();

    @Override
    AsyncHtmlImageLoader getHtmlImageLoader();

    GHActor getActor();

    @Nullable
    Date getCreatedAt();

    ObservableValue<Boolean> isOutdated();

    ObservableValue<Boolean> isPending();

    String getFilePath();

    boolean isFileComment();

    ObservableValue<@Nullable Pair<PatchHunk, @Nullable LineRange>> getPatchHunkWithAnchor();

    ObservableValue<@Nullable GHPRReviewThreadCommentViewModel> getMainCommentVm();

    ObservableValue<List<GHPRReviewThreadCommentViewModel>> getReplies();

    /**
     * Show diff for a file this thread belongs to
     */
    void showDiff();

    @RequiredUIAccess
    @Override
    default boolean openLink(String href) {
        return GHHtmlPaneUtil.handleGithubHyperlink(href, number -> {
            GHPRConnectedProjectViewModel vm = GHPRProjectViewModel.getInstance(getProject()).getConnectedProjectVm().get();
            if (vm != null) {
                vm.openPullRequestInfoAndTimeline(number);
            }
        });
    }
}
