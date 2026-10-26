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
package org.jetbrains.plugins.github.pullrequest.action;

import consulo.annotation.component.ActionImpl;
import consulo.github.localize.GithubLocalize;
import consulo.review.localize.ReviewLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;

@ActionImpl(id = "Github.PullRequest.Timeline.Show")
public class GHPROpenPullRequestTimelineAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    public GHPROpenPullRequestTimelineAction() {
        super(ReviewLocalize.reviewDetailsViewTimelineAction(), GithubLocalize.pullRequestViewConversationsActionDescription());
    }

    @Override
    public void update(AnActionEvent e) {
        GHPRConnectedProjectViewModel vm = e.getData(GHPRActionKeys.PULL_REQUESTS_CONNECTED_PROJECT_VM);
        GHPRIdentifier id = e.getData(GHPRActionKeys.PULL_REQUEST_ID);
        e.getPresentation().setEnabled(vm != null && id != null);
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        GHPRConnectedProjectViewModel vm = e.getData(GHPRActionKeys.PULL_REQUESTS_CONNECTED_PROJECT_VM);
        GHPRIdentifier id = e.getData(GHPRActionKeys.PULL_REQUEST_ID);
        if (vm == null || id == null) {
            return;
        }
        vm.openPullRequestTimeline(id, true);
    }
}
