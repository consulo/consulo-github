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
package org.jetbrains.plugins.github.ui.cloneDialog;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.github.localize.GithubLocalize;
import consulo.project.Project;
import consulo.review.localize.ReviewLocalize;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DumbAwareAction;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

class GHCloneDialogExtensionComponent extends GHCloneDialogExtensionComponentBase {
    private @Nullable GHCloneDialogLoginPanel myLoginPanel;

    @RequiredUIAccess
    GHCloneDialogExtensionComponent(Project project, Disposable parentDisposable, AccountManager accountManager) {
        super(project, parentDisposable, accountManager);
    }

    @Override
    protected boolean isAccountHandled(Account account) {
        return GHCloneDialogExtensionBase.isGHAccount(myAccountManager, account);
    }

    @Override
    @RequiredUIAccess
    protected Component createLoginPanel(@Nullable Account account, @RequiredUIAccess Runnable cancelHandler) {
        GHCloneDialogLoginPanel loginPanel = new GHCloneDialogLoginPanel(myProject, myAccountManager, account);
        Runnable chooseLoginUiHandler = loginPanel::setChooseLoginUi;
        loginPanel.getLoginPanel().setCancelHandler(getAccounts().isEmpty() ? chooseLoginUiHandler : cancelHandler);
        Disposer.register(myDisposable, loginPanel);
        myLoginPanel = loginPanel;
        return loginPanel.getComponent();
    }

    @Override
    @RequiredUIAccess
    protected Collection<AnAction> createAccountMenuLoginActions(@Nullable Account account) {
        return List.of(createLoginAction(account), createLoginWithTokenAction(account));
    }

    private AnAction createLoginAction(@Nullable Account account) {
        return DumbAwareAction.create(GithubLocalize.loginViaGithubAction(), e -> {
            switchToLogin(account);
            GHCloneDialogLoginPanel loginPanel = getLoginPanel();
            if (loginPanel != null) {
                loginPanel.setOAuthLoginUi();
            }
        });
    }

    private AnAction createLoginWithTokenAction(@Nullable Account account) {
        return DumbAwareAction.create(ReviewLocalize.cloneDialogLoginWithTokenAction(), e -> {
            switchToLogin(account);
            GHCloneDialogLoginPanel loginPanel = getLoginPanel();
            if (loginPanel != null) {
                loginPanel.setTokenUi();
            }
        });
    }

    private @Nullable GHCloneDialogLoginPanel getLoginPanel() {
        GHCloneDialogLoginPanel loginPanel = myLoginPanel;
        return loginPanel != null && getContent() == loginPanel.getComponent() ? loginPanel : null;
    }
}
