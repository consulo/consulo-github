// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.RefreshAction;
import org.jetbrains.plugins.github.pullrequest.ui.list.GHPRListController;

@ActionImpl(id = "Github.PullRequest.List.Reload")
public class GHPRReloadListAction extends RefreshAction {
    public GHPRReloadListAction() {
        super(GithubLocalize.actionGithubPullrequestListReloadText(), LocalizeValue.empty(), PlatformIconGroup.actionsRefresh());
    }

    @Override
    public void update(AnActionEvent e) {
        GHPRListController controller = e.getData(GHPRActionKeys.PULL_REQUESTS_LIST_CONTROLLER);
        e.getPresentation().setEnabled(controller != null);
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        GHPRListController controller = e.getData(GHPRActionKeys.PULL_REQUESTS_LIST_CONTROLLER);
        if (controller == null) {
            return;
        }
        controller.reloadList();
    }
}
