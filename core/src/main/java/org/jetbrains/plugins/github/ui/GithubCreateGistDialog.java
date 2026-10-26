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
package org.jetbrains.plugins.github.ui;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.accountManagement.ui.AccountChooser;
import consulo.accountManagement.ui.AccountComponentFactory;
import consulo.disposer.Disposable;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.review.localize.ReviewLocalize;
import consulo.review.snippet.PathHandlingMode;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.TextArea;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.util.FormBuilder;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public class GithubCreateGistDialog extends DialogDescriptor {
    private final Project myProject;

    private @Nullable String myFileName;
    private String myDescription = "";
    private boolean myIsSecret;
    private boolean myIsOpenInBrowser;
    private boolean myIsCopyURL;
    private @Nullable Account myAccount;
    private @Nullable PathHandlingMode myPathMode = PathHandlingMode.RelativePaths;

    private @Nullable TextArea myDescriptionField;

    public GithubCreateGistDialog(
        Project project,
        @Nullable String fileName,
        boolean secret,
        boolean openInBrowser,
        boolean copyLink
    ) {
        super(GithubLocalize.createGistDialogTitle());
        myProject = project;
        myFileName = fileName;
        myIsSecret = secret;
        myIsOpenInBrowser = openInBrowser;
        myIsCopyURL = copyLink;
    }

    public @Nullable String getFileName() {
        return myFileName;
    }

    public String getDescription() {
        return myDescription;
    }

    public boolean isSecret() {
        return myIsSecret;
    }

    public boolean isOpenInBrowser() {
        return myIsOpenInBrowser;
    }

    public boolean isCopyURL() {
        return myIsCopyURL;
    }

    public void setCopyURL(boolean copyURL) {
        myIsCopyURL = copyURL;
    }

    public @Nullable Account getAccount() {
        return myAccount;
    }

    public @Nullable PathHandlingMode getPathMode() {
        return myPathMode;
    }

    public void setPathMode(@Nullable PathHandlingMode pathMode) {
        myPathMode = pathMode;
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        FormBuilder form = FormBuilder.create();

        String fileName = myFileName;
        if (fileName != null) {
            TextBox fileNameField = TextBox.create(fileName);
            fileNameField.addValueListener(event -> myFileName = Objects.requireNonNullElse(event.getValue(), ""));
            form.addLabeled(GithubLocalize.createGistDialogFilenameField(), fileNameField);
        }

        TextArea descriptionField = TextArea.create(myDescription);
        descriptionField.setMinRows(4);
        descriptionField.addValueListener(event -> myDescription = Objects.requireNonNullElse(event.getValue(), ""));
        myDescriptionField = descriptionField;
        form.addLabeled(GithubLocalize.createGistDialogDescriptionField(), descriptionField);

        ComboBox<PathHandlingMode> pathModeComboBox = ComboBox.create(PathHandlingMode.values());
        pathModeComboBox.setTextRenderer(mode -> mode == null ? LocalizeValue.empty() : mode.getDisplayName());
        pathModeComboBox.setToolTipText(ReviewLocalize.reviewSnippetCreatePathModeTooltip());
        pathModeComboBox.setValue(myPathMode);
        pathModeComboBox.addValueListener(event -> myPathMode = event.getValue());
        form.addLabeled(ReviewLocalize.reviewSnippetCreatePathMode(), pathModeComboBox);

        AccountManager accountManager = myProject.getApplication().getInstance(AccountManager.class);
        AccountType<?> accountType = Objects.requireNonNull(accountManager.findAccountType(GithubAccountType.ID));
        List<Account> accounts = accountManager.getAccounts(accountType, myProject);
        if (accounts.size() != 1) {
            AccountChooser accountChooser = myProject.getApplication()
                .getInstance(AccountComponentFactory.class)
                .createChooser(accountType, myProject, uiDisposable);
            if (accountChooser.getValue() == null && !accounts.isEmpty()) {
                accountChooser.setValue(accounts.get(0));
            }
            myAccount = accountChooser.getValue();
            accountChooser.addValueListener(account -> {
                myAccount = account;
                updateOkButtonState();
            });
            form.addLabeled(GithubLocalize.createGistDialogCreateForField(), accountChooser.getComponent());
        }
        else {
            myAccount = accounts.get(0);
        }

        CheckBox secretCheckBox = CheckBox.create(GithubLocalize.createGistDialogSecret(), myIsSecret);
        secretCheckBox.addValueListener(event -> myIsSecret = Boolean.TRUE.equals(event.getValue()));
        CheckBox browserCheckBox = CheckBox.create(GithubLocalize.createGistDialogOpenBrowser(), myIsOpenInBrowser);
        browserCheckBox.addValueListener(event -> myIsOpenInBrowser = Boolean.TRUE.equals(event.getValue()));
        CheckBox copyUrlCheckBox = CheckBox.create(GithubLocalize.createGistDialogCopyUrl(), myIsCopyURL);
        copyUrlCheckBox.addValueListener(event -> myIsCopyURL = Boolean.TRUE.equals(event.getValue()));

        HorizontalLayout checkBoxes = HorizontalLayout.create();
        checkBoxes.add(secretCheckBox);
        checkBoxes.add(browserCheckBox);
        checkBoxes.add(copyUrlCheckBox);
        form.addBottom(checkBoxes);

        return form.build();
    }

    @Override
    public boolean doUpdateOkButtonState() {
        return myAccount != null;
    }

    @Override
    public @Nullable String getHelpId() {
        return "github.create.gist.dialog";
    }

    @Override
    public @Nullable String getDimensionServiceKey() {
        return "Github.CreateGistDialog";
    }

    @RequiredUIAccess
    @Override
    public @Nullable Component getPreferredFocusedComponent() {
        return myDescriptionField;
    }
}
