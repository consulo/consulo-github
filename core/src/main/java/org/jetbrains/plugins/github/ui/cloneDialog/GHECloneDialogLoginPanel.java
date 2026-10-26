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
package org.jetbrains.plugins.github.ui.cloneDialog;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.github.localize.GithubLocalize;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.LabelStyle;
import consulo.ui.PseudoComponent;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;
import org.jspecify.annotations.Nullable;

class GHECloneDialogLoginPanel implements PseudoComponent, Disposable {
    private final DockLayout myLayout;
    private final CloneDialogLoginPanel myLoginPanel;

    @RequiredUIAccess
    GHECloneDialogLoginPanel(Project project, AccountManager accountManager, @Nullable Account account) {
        Label title = Label.create(GithubLocalize.loginToGithubEnterprise());
        title.addStyle(LabelStyle.FONT_XX_LARGE);

        myLoginPanel = new CloneDialogLoginPanel(project, accountManager, account);
        Disposer.register(this, myLoginPanel);
        myLoginPanel.setTokenUi();

        myLayout = DockLayout.create();
        myLayout.top(title);
        myLayout.center(myLoginPanel.getComponent());
    }

    @Override
    public Component getComponent() {
        return myLayout;
    }

    public CloneDialogLoginPanel getLoginPanel() {
        return myLoginPanel;
    }

    @Override
    public void dispose() {
        myLoginPanel.cancelLogin();
    }
}
