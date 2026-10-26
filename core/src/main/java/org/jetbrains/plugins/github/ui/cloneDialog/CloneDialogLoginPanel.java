// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import consulo.accountManagement.AccountAuthorizeConfig;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.review.localize.ReviewLocalize;
import consulo.ui.Button;
import consulo.ui.Component;
import consulo.ui.Hyperlink;
import consulo.ui.Label;
import consulo.ui.PseudoComponent;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.Presentation;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.style.StandardColors;
import org.jetbrains.plugins.github.authentication.AuthorizationType;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.util.GithubUtil;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class CloneDialogLoginPanel implements PseudoComponent, Disposable {
    private final Project myProject;
    private final AccountManager myAccountManager;
    private final @Nullable Account myAccount;
    private final UIAccess myUiAccess;

    private final VerticalLayout myLayout;
    private final Label myErrorLabel;
    private final Button myLoginButton;
    private final Hyperlink myBackLink;

    private @Nullable AccountAuthorizeConfig myAuthorizeConfig;
    private @Nullable Runnable myCancelHandler;
    private @Nullable CompletableFuture<?> myLoginJob;

    @RequiredUIAccess
    public CloneDialogLoginPanel(Project project, AccountManager accountManager, @Nullable Account account) {
        myProject = project;
        myAccountManager = accountManager;
        myAccount = account;
        myUiAccess = UIAccess.current();

        myLoginButton = Button.create(ReviewLocalize.cloneDialogButtonLogin().map(Presentation.NO_MNEMONIC), event -> login());
        myBackLink = Hyperlink.create(CommonLocalize.buttonBack(), event -> {
            cancelLogin();
            Runnable cancelHandler = myCancelHandler;
            if (cancelHandler != null) {
                cancelHandler.run();
            }
        });

        myErrorLabel = Label.create();
        myErrorLabel.setForegroundColor(StandardColors.RED);

        HorizontalLayout buttonPanel = HorizontalLayout.create();
        buttonPanel.add(myLoginButton);
        buttonPanel.add(myBackLink);

        myLayout = VerticalLayout.create();
        myLayout.add(buttonPanel);
        myLayout.add(myErrorLabel);
    }

    @Override
    public Component getComponent() {
        return myLayout;
    }

    public boolean isCancelVisible() {
        return myBackLink.isVisible();
    }

    @RequiredUIAccess
    public void setCancelVisible(boolean value) {
        myBackLink.setVisible(value);
    }

    public void setCancelHandler(@RequiredUIAccess Runnable listener) {
        myCancelHandler = listener;
    }

    @RequiredUIAccess
    public void setTokenUi() {
        setupNewUi(false);
        myAuthorizeConfig = findConfig(AuthorizationType.TOKEN);
    }

    @RequiredUIAccess
    public void setOAuthUi() {
        setupNewUi(true);
        myAuthorizeConfig = findConfig(AuthorizationType.OAUTH);

        login();
    }

    @Override
    public void dispose() {
        cancelLogin();
    }

    @RequiredUIAccess
    private void setupNewUi(boolean isOAuth) {
        myLoginButton.setVisible(!isOAuth);
        myBackLink.setText(isOAuth ? CommonLocalize.buttonCancel() : CommonLocalize.buttonBack());

        clearErrors();
    }

    public void cancelLogin() {
        CompletableFuture<?> loginJob = myLoginJob;
        if (loginJob != null) {
            loginJob.cancel(false);
        }
        myLoginJob = null;
    }

    @RequiredUIAccess
    private void login() {
        cancelLogin();

        clearErrors();

        AccountType<?> type = myAccountManager.findAccountType(GithubAccountType.ID);
        if (type == null) {
            return;
        }

        Account account = myAccount;
        CompletableFuture<@Nullable Account> loginJob = account != null
            ? myAccountManager.requestAuthorization(account, null, myProject, null)
            : myAccountManager.requestLogin(type, myAuthorizeConfig, myProject, null);
        myLoginJob = loginJob;

        loginJob.whenCompleteAsync((result, error) -> {
            if (myLoginJob != loginJob || loginJob.isCancelled()) {
                return;
            }
            myLoginJob = null;

            clearErrors();
            if (error != null) {
                setError(LocalizeValue.of(GithubUtil.getErrorTextFromException(unwrap(error))));
            }
        }, myUiAccess);
    }

    private @Nullable AccountAuthorizeConfig findConfig(AuthorizationType authType) {
        AccountType<?> type = myAccountManager.findAccountType(GithubAccountType.ID);
        if (type == null) {
            return null;
        }
        return GithubAccountType.findAuthorizeConfig(myAccountManager.getAuthorizeConfigs(type), authType);
    }

    @RequiredUIAccess
    private void clearErrors() {
        myErrorLabel.setText(LocalizeValue.empty());
    }

    @RequiredUIAccess
    private void setError(LocalizeValue error) {
        myErrorLabel.setText(error);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable result = error;
        while ((result instanceof CompletionException || result instanceof ExecutionException) && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }
}
