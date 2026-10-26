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
import consulo.github.icon.GitHubIconGroup;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import org.jspecify.annotations.Nullable;

@ActionImpl(id = "Github.Pull.Request.Show.In.Toolwindow")
public class GHPROnCurrentBranchShowAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    public GHPROnCurrentBranchShowAction() {
        super(GithubLocalize.actionGithubPullRequestShowInToolwindowText(), LocalizeValue.empty(), GitHubIconGroup.github());
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(getCurrentVm(e.getData(Project.KEY)) != null);
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        GHPRBranchWidgetViewModel vm = getCurrentVm(e.getData(Project.KEY));
        if (vm != null) {
            vm.showPullRequest();
        }
    }

    static @Nullable GHPRBranchWidgetViewModel getCurrentVm(@Nullable Project project) {
        return project == null ? null : GHPROnCurrentBranchService.getInstance(project).getVmState().get();
    }
}
