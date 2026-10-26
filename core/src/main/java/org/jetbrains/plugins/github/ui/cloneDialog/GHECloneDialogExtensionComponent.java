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
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DumbAwareAction;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

class GHECloneDialogExtensionComponent extends GHCloneDialogExtensionComponentBase {
    @RequiredUIAccess
    GHECloneDialogExtensionComponent(Project project, Disposable parentDisposable, AccountManager accountManager) {
        super(project, parentDisposable, accountManager);
    }

    @Override
    protected boolean isAccountHandled(Account account) {
        return GHECloneDialogExtension.isGHEAccount(myAccountManager, account);
    }

    @Override
    @RequiredUIAccess
    protected Component createLoginPanel(@Nullable Account account, @RequiredUIAccess Runnable cancelHandler) {
        GHECloneDialogLoginPanel loginPanel = new GHECloneDialogLoginPanel(myProject, myAccountManager, account);
        Disposer.register(myDisposable, loginPanel);

        loginPanel.getLoginPanel().setCancelVisible(!getAccounts().isEmpty());
        loginPanel.getLoginPanel().setCancelHandler(cancelHandler);
        return loginPanel.getComponent();
    }

    @Override
    @RequiredUIAccess
    protected Collection<AnAction> createAccountMenuLoginActions(@Nullable Account account) {
        return List.of(createLoginAction(account));
    }

    private AnAction createLoginAction(@Nullable Account account) {
        return DumbAwareAction.create(GithubLocalize.loginToGithubEnterpriseAction(), e -> switchToLogin(account));
    }
}
