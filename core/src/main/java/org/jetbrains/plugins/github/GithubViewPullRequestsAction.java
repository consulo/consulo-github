// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github;

import consulo.annotation.component.ActionImpl;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;

@ActionImpl(id = "Github.View.Pull.Request")
public class GithubViewPullRequestsAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    public GithubViewPullRequestsAction() {
        super(GithubLocalize.actionGithubViewPullRequestText(), LocalizeValue.empty());
    }

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(isEnabledAndVisible(e));
    }

    private boolean isEnabledAndVisible(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return false;
        }
        return GHPRProjectViewModel.getInstance(project).isAvailable().get();
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        GHPRProjectViewModel.getInstance(e.getRequiredData(Project.KEY)).activateAndAwaitProject(GHPRConnectedProjectViewModel::viewList);
    }
}
