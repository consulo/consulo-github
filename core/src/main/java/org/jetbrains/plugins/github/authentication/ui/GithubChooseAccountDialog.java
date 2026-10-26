// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.authentication.ui;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.ListBox;
import consulo.ui.TextAttribute;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.ScrollableLayout;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

public class GithubChooseAccountDialog extends DialogDescriptor {
    private final Collection<Account> myAccounts;
    private final LocalizeValue myDescriptionText;
    private final boolean myShowHosts;
    private final boolean myAllowDefault;
    private final LocalizeValue myOkText;

    private @Nullable ListBox<Account> myAccountsList;
    private @Nullable CheckBox mySetDefaultCheckBox;

    public GithubChooseAccountDialog(Collection<Account> accounts,
                                     LocalizeValue descriptionText,
                                     boolean showHosts,
                                     boolean allowDefault) {
        this(accounts, descriptionText, showHosts, allowDefault, GithubLocalize.accountChooseTitle(), GithubLocalize.accountChooseButton());
    }

    public GithubChooseAccountDialog(Collection<Account> accounts,
                                     LocalizeValue descriptionText,
                                     boolean showHosts,
                                     boolean allowDefault,
                                     LocalizeValue title,
                                     LocalizeValue okText) {
        super(title);
        myAccounts = accounts;
        myDescriptionText = descriptionText;
        myShowHosts = showHosts;
        myAllowDefault = allowDefault;
        myOkText = okText;
    }

    @Override
    public @Nullable String getDimensionServiceKey() {
        return "Github.Dialog.Accounts.Choose";
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        AccountManager accountManager = Application.get().getInstance(AccountManager.class);

        ListBox<Account> accountsList = ListBox.create(myAccounts);
        accountsList.setRenderer((presentation, item) -> {
            Account value = item.getValue();
            if (value == null) {
                return;
            }
            presentation.append(value.getName());
            if (myShowHosts) {
                presentation.append(" ");
                String serverUrl = accountManager.getServerUrl(value);
                presentation.append(serverUrl == null ? "" : serverUrl, TextAttribute.GRAYED);
            }
        });
        accountsList.addValueListener(event -> updateOkButtonState());
        myAccountsList = accountsList;
        if (!myAccounts.isEmpty()) {
            accountsList.setValueByIndex(0);
        }

        DockLayout root = DockLayout.create();
        if (myDescriptionText.isNotEmpty()) {
            root.top(Label.create(myDescriptionText));
        }
        root.center(ScrollableLayout.create(accountsList));
        if (myAllowDefault) {
            CheckBox setDefaultCheckBox = CheckBox.create(GithubLocalize.accountChooseAsDefault());
            mySetDefaultCheckBox = setDefaultCheckBox;
            root.bottom(setDefaultCheckBox);
        }
        return root;
    }

    @Override
    public boolean doUpdateOkButtonState() {
        ListBox<Account> accountsList = myAccountsList;
        return accountsList != null && accountsList.getValue() != null;
    }

    @Override
    protected DialogOkAction createOkAction() {
        return new DialogOkAction(myOkText);
    }

    public @Nullable Account getAccount() {
        ListBox<Account> accountsList = myAccountsList;
        return accountsList == null ? null : accountsList.getValue();
    }

    public boolean isSetDefault() {
        CheckBox setDefaultCheckBox = mySetDefaultCheckBox;
        return setDefaultCheckBox != null && setDefaultCheckBox.getValueOrError();
    }
}
