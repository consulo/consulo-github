// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.selector;

import consulo.accountManagement.AccountManager;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.review.list.error.HtmlErrorStatusPresenter;
import consulo.review.localize.ReviewLocalize;
import consulo.review.util.FutureUtil;
import consulo.ui.ex.action.AnAction;
import consulo.util.lang.xml.XmlStringUtil;
import git4idea.remote.hosting.ui.RepositoryAndAccountSelectorViewModel;
import org.jspecify.annotations.Nullable;

public class GHSelectorErrorStatusPresenter implements HtmlErrorStatusPresenter<RepositoryAndAccountSelectorViewModel.Error> {
    private final Project myProject;
    private final Runnable myResetAction;

    public GHSelectorErrorStatusPresenter(Project project, Runnable resetAction) {
        myProject = project;
        myResetAction = resetAction;
    }

    @Override
    public LocalizeValue getErrorTitle(RepositoryAndAccountSelectorViewModel.Error error) {
        return LocalizeValue.empty();
    }

    @Override
    public String getHTMLBody(RepositoryAndAccountSelectorViewModel.Error error) {
        return switch (error) {
            case RepositoryAndAccountSelectorViewModel.SubmissionError submissionError -> {
                Throwable cause = FutureUtil.unwrap(submissionError.exception());
                String message = cause.getLocalizedMessage();
                yield XmlStringUtil.escapeString(ReviewLocalize.reviewListConnectionFailedRepositoryAccount(
                    submissionError.repo().getRepository(), submissionError.account().getName()).get())
                    + "<br><br>" + XmlStringUtil.escapeString(message == null ? cause.getClass().getSimpleName() : message);
            }
            case RepositoryAndAccountSelectorViewModel.MissingCredentials missingCredentials ->
                XmlStringUtil.escapeString(ReviewLocalize.reviewListConnectionFailed().get())
                    + "<br><br>" + XmlStringUtil.escapeString(ReviewLocalize.accountTokenMissing().get());
        };
    }

    @Override
    public @Nullable AnAction getErrorAction(RepositoryAndAccountSelectorViewModel.Error error) {
        if (error instanceof RepositoryAndAccountSelectorViewModel.SubmissionError submissionError) {
            return DumbAwareAction.create(ReviewLocalize.loginAgainActionText(), e -> {
                AccountManager accountManager = myProject.getApplication().getInstance(AccountManager.class);
                accountManager.requestAuthorization(submissionError.account(), null, myProject, null).whenComplete((account, ex) -> {
                    if (account != null) {
                        myResetAction.run();
                    }
                });
            });
        }
        return null;
    }
}
