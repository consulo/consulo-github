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
import consulo.accountManagement.AccountType;
import consulo.application.Application;
import consulo.github.icon.GitHubIconGroup;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import consulo.versionControlSystem.checkout.CheckoutProvider;
import consulo.versionControlSystem.distributed.localize.DistributedVcsLocalize;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;

import java.util.Collection;
import java.util.List;

public abstract class GHCloneDialogExtensionBase implements CheckoutProvider {
    protected final AccountManager myAccountManager;

    protected GHCloneDialogExtensionBase(AccountManager accountManager) {
        myAccountManager = accountManager;
    }

    @Override
    public Image getIcon() {
        return GitHubIconGroup.github();
    }

    @Override
    public LocalizeValue getActionName() {
        return DistributedVcsLocalize.cloneButton();
    }

    protected abstract Collection<Account> getAccounts();

    protected List<Account> getAllAccounts() {
        AccountType<?> type = myAccountManager.findAccountType(GithubAccountType.ID);
        if (type == null) {
            return List.of();
        }
        return myAccountManager.getAccounts(type, null);
    }

    static boolean isGHAccount(AccountManager accountManager, Account account) {
        return GithubAccountType.toServerPath(accountManager.getServerUrl(account)).isGithubDotCom();
    }

    static String getNameWithServer(Account account) {
        AccountManager accountManager = Application.get().getInstance(AccountManager.class);
        GithubServerPath server = GithubAccountType.toServerPath(accountManager.getServerUrl(account));
        String login = account.getLogin();
        String name = login == null || login.isEmpty() ? account.getName() : login;
        String serverPrefix = server.isGithubDotCom() ? "" : server.getHost() + "/";
        return serverPrefix + name;
    }
}
