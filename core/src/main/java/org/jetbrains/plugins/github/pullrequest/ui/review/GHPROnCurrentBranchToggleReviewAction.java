// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import consulo.annotation.component.ActionImpl;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareToggleAction;

@ActionImpl(id = "Github.Pull.Request.Review.In.Editor.Toggle")
public class GHPROnCurrentBranchToggleReviewAction extends DumbAwareToggleAction {
    public GHPROnCurrentBranchToggleReviewAction() {
        super(GithubLocalize.actionGithubPullRequestReviewInEditorToggleText(), LocalizeValue.empty(), null);
    }

    @Override
    public void update(AnActionEvent e) {
        GHPRBranchWidgetViewModel vm = GHPROnCurrentBranchShowAction.getCurrentVm(e.getData(Project.KEY));
        boolean enabledAndVisible = vm != null && !vm.getUpdateRequired().get();
        e.getPresentation().setEnabledAndVisible(enabledAndVisible);
        if (enabledAndVisible) {
            super.update(e);
        }
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
        GHPRBranchWidgetViewModel vm = GHPROnCurrentBranchShowAction.getCurrentVm(e.getData(Project.KEY));
        return vm != null && vm.getEditorReviewEnabled().get();
    }

    @RequiredUIAccess
    @Override
    public void setSelected(AnActionEvent e, boolean state) {
        GHPRBranchWidgetViewModel vm = GHPROnCurrentBranchShowAction.getCurrentVm(e.getData(Project.KEY));
        if (vm != null) {
            vm.toggleEditorReview(state);
        }
    }
}
