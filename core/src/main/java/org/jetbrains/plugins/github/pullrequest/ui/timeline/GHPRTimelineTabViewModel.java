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
package org.jetbrains.plugins.github.pullrequest.ui.timeline;

import consulo.dataContext.DataSink;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.review.details.model.CodeReviewDetailsTabViewModel;
import consulo.review.details.model.CodeReviewDetailsViewModel;
import consulo.review.timeline.CodeReviewTimelineViewModel;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.image.Image;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineUIUtil;
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jspecify.annotations.Nullable;

/**
 * The editor tab of the timeline of a pull request: named and decorated by the pull request as it is listed now.
 */
public final class GHPRTimelineTabViewModel implements CodeReviewDetailsTabViewModel {
    public static final String POPUP_GROUP_ID = "Github.PullRequest.Timeline.Popup";

    private final Project myProject;
    private final GHRepositoryCoordinates myRepository;
    private final GHPRIdentifier myPullRequest;
    private final CodeReviewDetailsViewModel myDetailsVm;
    private final GHPRTimelineViewModel myTimelineVm;

    public GHPRTimelineTabViewModel(Project project,
                                    GHRepositoryCoordinates repository,
                                    GHPRIdentifier pullRequest,
                                    CodeReviewDetailsViewModel detailsVm,
                                    GHPRTimelineViewModel timelineVm) {
        myProject = project;
        myRepository = repository;
        myPullRequest = pullRequest;
        myDetailsVm = detailsVm;
        myTimelineVm = timelineVm;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.of(GHPRTimelineUIUtil.getPresentableName(myProject, myRepository, myPullRequest));
    }

    @Override
    public LocalizeValue getDescription() {
        return LocalizeValue.of(GHPRTimelineUIUtil.getPresentablePath(myProject, myRepository, myPullRequest));
    }

    @Override
    public @Nullable Image getIcon() {
        return GHPRTimelineUIUtil.getIcon(myProject, myRepository, myPullRequest);
    }

    @Override
    public CodeReviewDetailsViewModel getDetailsVm() {
        return myDetailsVm;
    }

    @Override
    public CodeReviewTimelineViewModel getTimelineVm() {
        return myTimelineVm;
    }

    @RequiredUIAccess
    @Override
    public void uiDataSnapshot(DataSink sink) {
        sink.set(GHPRActionKeys.PULL_REQUEST_ID, myPullRequest);
        sink.set(GHPRActionKeys.PULL_REQUEST_URL, myDetailsVm.getUrl());
        sink.set(GHPRTimelineViewModel.DATA_KEY, myTimelineVm);
    }

    @Override
    public @Nullable ActionGroup getPopupActionGroup() {
        return ActionManager.getInstance().getAction(POPUP_GROUP_ID) instanceof ActionGroup group ? group : null;
    }
}
