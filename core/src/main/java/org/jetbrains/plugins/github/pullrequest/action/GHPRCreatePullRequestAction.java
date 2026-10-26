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
package org.jetbrains.plugins.github.pullrequest.action;

import consulo.annotation.component.ActionImpl;
import consulo.github.icon.GitHubIconGroup;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.Presentation;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;

@ActionImpl(id = "Github.Create.Pull.Request")
public class GHPRCreatePullRequestAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    public GHPRCreatePullRequestAction() {
        super(GithubLocalize.actionGithubCreatePullRequestText(), LocalizeValue.empty(), GitHubIconGroup.github());
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        GHPRProjectViewModel vm = project == null ? null : GHPRProjectViewModel.getInstance(project);
        boolean vmAvailable = vm != null && vm.isAvailable().get();
        boolean vmProjectConnected = vm != null && vm.getConnectedProjectVm().get() != null;
        Presentation presentation = e.getPresentation();
        if (ActionPlaces.TOOLWINDOW_TITLE.equals(e.getPlace())) {
            presentation.setEnabledAndVisible(vmProjectConnected);
            presentation.setIcon(PlatformIconGroup.generalAdd());
        }
        else {
            presentation.setEnabledAndVisible(vmAvailable);
            presentation.setIcon(GitHubIconGroup.github());
        }
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        tryToCreatePullRequest(e);
    }

    static void tryToCreatePullRequest(AnActionEvent e) {
        Project project = e.getRequiredData(Project.KEY);
        GHPRProjectViewModel.getInstance(project).activateAndAwaitProject(GHPRConnectedProjectViewModel::createPullRequest);
    }
}
