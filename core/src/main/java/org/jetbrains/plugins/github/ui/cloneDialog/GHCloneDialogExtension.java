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
import consulo.annotation.component.ExtensionImpl;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.versionControlSystem.checkout.CheckoutPage;
import jakarta.inject.Inject;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ExtensionImpl
public class GHCloneDialogExtension extends GHCloneDialogExtensionBase {
    @Inject
    public GHCloneDialogExtension(AccountManager accountManager) {
        super(accountManager);
    }

    @Override
    public LocalizeValue getName() {
        return LocalizeValue.of(GithubUtil.SERVICE_DISPLAY_NAME);
    }

    @Override
    protected Collection<Account> getAccounts() {
        List<Account> result = new ArrayList<>();
        for (Account account : getAllAccounts()) {
            if (isGHAccount(myAccountManager, account)) {
                result.add(account);
            }
        }
        return result;
    }

    @Override
    @RequiredUIAccess
    public CheckoutPage createPage(Project project, Disposable uiDisposable) {
        return new GHCloneDialogExtensionComponent(project, uiDisposable, myAccountManager);
    }
}
