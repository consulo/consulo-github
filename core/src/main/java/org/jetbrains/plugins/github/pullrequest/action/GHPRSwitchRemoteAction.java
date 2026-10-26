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

import consulo.github.localize.GithubLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;

public class GHPRSwitchRemoteAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    public GHPRSwitchRemoteAction() {
        super(GithubLocalize.pullRequestChangeRemoteOrAccount());
    }

    @Override
    public void update(AnActionEvent e) {
        GHPRProjectViewModel vm = e.getData(GHPRActionKeys.PULL_REQUESTS_PROJECT_VM);
        e.getPresentation().setEnabledAndVisible(vm != null && vm.canResetRemoteOrAccount());
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        GHPRProjectViewModel vm = e.getData(GHPRActionKeys.PULL_REQUESTS_PROJECT_VM);
        if (vm != null) {
            vm.resetRemoteAndAccount();
        }
    }
}
