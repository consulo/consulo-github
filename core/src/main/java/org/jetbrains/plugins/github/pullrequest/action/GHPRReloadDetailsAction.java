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
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsLoadingViewModel;

@ActionImpl(id = "Github.PullRequest.Details.Reload")
public class GHPRReloadDetailsAction extends RefreshAction {
    public GHPRReloadDetailsAction() {
        super(GithubLocalize.pullRequestRefreshDetailsAction(), LocalizeValue.empty(), PlatformIconGroup.actionsRefresh());
    }

    @Override
    public void update(AnActionEvent e) {
        GHPRDetailsLoadingViewModel vm = e.getData(GHPRDetailsLoadingViewModel.DATA_KEY);
        e.getPresentation().setEnabledAndVisible(vm != null);
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        GHPRDetailsLoadingViewModel vm = e.getData(GHPRDetailsLoadingViewModel.DATA_KEY);
        if (vm == null) {
            return;
        }
        vm.requestReload();
    }
}
