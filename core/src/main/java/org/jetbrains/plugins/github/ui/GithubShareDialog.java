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
import consulo.component.ProcessCanceledException;
import consulo.disposer.Disposable;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.HasValidator.ValidationInfo;
import consulo.ui.Label;
import consulo.ui.TextArea;
import consulo.ui.TextBox;
import consulo.ui.UIAccess;
import consulo.ui.WidthAndHeight;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.ex.dialog.DialogValue;
import consulo.ui.ex.dialog.action.DialogOkAction;
import consulo.ui.layout.DockLayout;
import consulo.ui.style.ComponentColors;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.ui.util.DialogValidationUtils.RecordUniqueValidator;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import static org.jetbrains.plugins.github.ui.util.DialogValidationUtils.notBlank;

public class GithubShareDialog extends DialogDescriptor {
    private static final Pattern GITHUB_REPO_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]+");

    private final Project myProject;
    private final Set<String> myExistingRemotes;
    private final BiFunction<Account, @Nullable Component, CompletableFuture<Pair<Boolean, Set<String>>>> myAccountInformationSupplier;
    private final String myProjectName;
    private final String myRemoteName;

    private final List<Account> myAccounts;
    private @Nullable Account mySelectedAccount;

    private @Nullable TextBox myRepositoryTextField;
    private @Nullable CheckBox myPrivateCheckBox;
    private @Nullable TextBox myRemoteTextField;
    private @Nullable TextArea myDescriptionTextArea;
    private @Nullable Label myAccountInformationLoadingErrorLabel;
    private @Nullable RecordUniqueValidator myExistingRepoValidator;
    private @Nullable ValidationInfo myAccountInformationLoadingError;

    public GithubShareDialog(Project project,
                             Set<String> existingRemotes,
                             BiFunction<Account, @Nullable Component, CompletableFuture<Pair<Boolean, Set<String>>>> accountInformationSupplier,
                             String projectName) {
        super(GithubLocalize.shareOnGithub());
        myProject = project;
        myExistingRemotes = existingRemotes;
        myAccountInformationSupplier = accountInformationSupplier;
        myProjectName = projectName;
        myRemoteName = existingRemotes.isEmpty() ? "origin" : "github";

        AccountManager accountManager = accountManager();
        AccountType<?> accountType = accountManager.findAccountType(GithubAccountType.ID);
        myAccounts = accountManager.getAccounts(accountType, project);
        Account defaultAccount = accountManager.getActiveAccount(accountType, project);
        mySelectedAccount = defaultAccount != null ? defaultAccount : myAccounts.isEmpty() ? null : myAccounts.get(0);
    }

    @RequiredUIAccess
    private void switchAccount(@Nullable Account account) {
        if (account == null) {
            return;
        }

        UIAccess uiAccess = UIAccess.current();
        myAccountInformationLoadingError = null;
        updateLoadingError();
        myAccountInformationSupplier.apply(account, myRepositoryTextField).whenCompleteAsync((info, error) -> {
            CheckBox privateCheckBox = Objects.requireNonNull(myPrivateCheckBox);
            RecordUniqueValidator existingRepoValidator = Objects.requireNonNull(myExistingRepoValidator);
            if (error == null) {
                boolean canCreatePrivate = info.getFirst();
                privateCheckBox.setEnabled(canCreatePrivate);

                if (!canCreatePrivate) {
                    privateCheckBox.setToolTipText(GithubLocalize.shareErrorPrivateReposNotSupported());
                }
                else {
                    privateCheckBox.setToolTipText(LocalizeValue.empty());
                }

                existingRepoValidator.setRecords(info.getSecond());
            }
            else {
                Throwable e = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                String errorText = GithubLocalize.shareDialogAccountInfoLoadErrorPrefix(account.getName()).get() +
                    (e instanceof ProcessCanceledException
                        ? GithubLocalize.shareDialogAccountInfoLoadProcessCanceled().get()
                        : e.getMessage());
                myAccountInformationLoadingError = new ValidationInfo(errorText);
                privateCheckBox.setEnabled(false);
                privateCheckBox.setToolTipText(LocalizeValue.empty());
                existingRepoValidator.setRecords(Set.of());
                updateLoadingError();
            }
        }, uiAccess);
    }

    @RequiredUIAccess
    private void updateLoadingError() {
        Label label = myAccountInformationLoadingErrorLabel;
        if (label == null) {
            return;
        }
        ValidationInfo loadingError = myAccountInformationLoadingError;
        label.setText(loadingError == null ? LocalizeValue.empty() : LocalizeValue.of(loadingError.getMessage()));
        label.setVisible(loadingError != null);
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        TextBox repositoryTextField = TextBox.create(myProjectName);
        myRepositoryTextField = repositoryTextField;
        CheckBox privateCheckBox = CheckBox.create(GithubLocalize.shareDialogPrivate(), true);
        myPrivateCheckBox = privateCheckBox;
        TextBox remoteTextField = TextBox.create(myRemoteName);
        myRemoteTextField = remoteTextField;
        TextArea descriptionTextArea = TextArea.create();
        myDescriptionTextArea = descriptionTextArea;

        RecordUniqueValidator existingRepoValidator =
            new RecordUniqueValidator(repositoryTextField, GithubLocalize.shareErrorRepoWithSelectedNameExists());
        myExistingRepoValidator = existingRepoValidator;
        RecordUniqueValidator existingRemoteValidator =
            new RecordUniqueValidator(remoteTextField, GithubLocalize.shareErrorRemoteWithSelectedNameExists());
        existingRemoteValidator.setRecords(myExistingRemotes);

        repositoryTextField.addValidator(value -> validateRepository(repositoryTextField, existingRepoValidator));
        remoteTextField.addValidator(value -> validateRemote(remoteTextField, existingRemoteValidator));

        FormBuilder form = FormBuilder.create();
        form.addLabeled(GithubLocalize.shareDialogRepoName(), DockLayout.create().center(repositoryTextField).right(privateCheckBox));
        form.addLabeled(GithubLocalize.shareDialogRemote(), remoteTextField);
        form.addLabeled(GithubLocalize.shareDialogDescription(), descriptionTextArea);

        if (myAccounts.size() != 1) {
            AccountManager accountManager = accountManager();
            AccountChooser accountChooser = myProject.getApplication()
                .getInstance(AccountComponentFactory.class)
                .createChooser(accountManager.findAccountType(GithubAccountType.ID), myProject, uiDisposable);
            accountChooser.setValue(mySelectedAccount);
            accountChooser.addValueListener(account -> {
                mySelectedAccount = account;
                updateOkButtonState();
                switchAccount(account);
            });
            form.addLabeled(GithubLocalize.shareDialogShareBy(), accountChooser.getComponent());
        }

        Label loadingErrorLabel = Label.create();
        loadingErrorLabel.setForegroundColor(ComponentColors.ERROR_FOREGROUND);
        loadingErrorLabel.setVisible(false);
        myAccountInformationLoadingErrorLabel = loadingErrorLabel;
        form.addBottom(loadingErrorLabel);

        switchAccount(mySelectedAccount);
        return form.build();
    }

    @Override
    public boolean doUpdateOkButtonState() {
        return mySelectedAccount != null;
    }

    @RequiredUIAccess
    @Override
    public boolean canHandle(AnAction action, @Nullable DialogValue value) {
        if (!(action instanceof DialogOkAction)) {
            return true;
        }
        TextBox repositoryTextField = Objects.requireNonNull(myRepositoryTextField);
        TextBox remoteTextField = Objects.requireNonNull(myRemoteTextField);
        boolean repositoryValid = repositoryTextField.validate();
        boolean remoteValid = remoteTextField.validate();
        return repositoryValid && remoteValid && myAccountInformationLoadingError == null && mySelectedAccount != null;
    }

    @Override
    protected DialogOkAction createOkAction() {
        return new DialogOkAction(GithubLocalize.shareButton());
    }

    private static @Nullable ValidationInfo validateRepository(TextBox repositoryTextField, RecordUniqueValidator existingRepoValidator) {
        ValidationInfo notBlank = notBlank(repositoryTextField, GithubLocalize.shareValidationNoRepoName());
        if (notBlank != null) {
            return notBlank;
        }
        ValidationInfo repositoryName = validateRepositoryName(repositoryTextField);
        if (repositoryName != null) {
            return repositoryName;
        }
        return existingRepoValidator.invoke();
    }

    private static @Nullable ValidationInfo validateRepositoryName(TextBox repositoryTextField) {
        String text = Objects.requireNonNullElse(repositoryTextField.getValue(), "");
        return GITHUB_REPO_PATTERN.matcher(text).matches() ? null : new ValidationInfo(GithubLocalize.shareValidationInvalidRepoName().get());
    }

    private static @Nullable ValidationInfo validateRemote(TextBox remoteTextField, RecordUniqueValidator existingRemoteValidator) {
        ValidationInfo notBlank = notBlank(remoteTextField, GithubLocalize.shareValidationNoRemoteName());
        if (notBlank != null) {
            return notBlank;
        }
        return existingRemoteValidator.invoke();
    }

    @Override
    public @Nullable String getHelpId() {
        return "github.share";
    }

    @Override
    public @Nullable String getDimensionServiceKey() {
        return "Github.ShareDialog";
    }

    @Override
    public @Nullable WidthAndHeight getInitialSize() {
        return WidthAndHeight.ofPixel(500, 250);
    }

    @RequiredUIAccess
    @Override
    public @Nullable Component getPreferredFocusedComponent() {
        return myRepositoryTextField;
    }

    public Result getResult() {
        return new Result(Objects.requireNonNullElse(Objects.requireNonNull(myRepositoryTextField).getValue(), ""),
            Objects.requireNonNullElse(Objects.requireNonNull(myRemoteTextField).getValue(), ""),
            Objects.requireNonNull(myPrivateCheckBox).getValueOrError(),
            Objects.requireNonNullElse(Objects.requireNonNull(myDescriptionTextArea).getValue(), ""),
            mySelectedAccount);
    }

    private AccountManager accountManager() {
        return myProject.getApplication().getInstance(AccountManager.class);
    }

    public static final class Result {
        private final String myRepositoryName;
        private final String myRemoteName;
        private final boolean myIsPrivate;
        private final String myDescription;
        private final @Nullable Account myAccount;

        public Result(String repositoryName, String remoteName, boolean isPrivate, String description, @Nullable Account account) {
            myRepositoryName = repositoryName;
            myRemoteName = remoteName;
            myIsPrivate = isPrivate;
            myDescription = description;
            myAccount = account;
        }

        public String getRepositoryName() {
            return myRepositoryName;
        }

        public String getRemoteName() {
            return myRemoteName;
        }

        public boolean isPrivate() {
            return myIsPrivate;
        }

        public String getDescription() {
            return myDescription;
        }

        public @Nullable Account getAccount() {
            return myAccount;
        }
    }
}
