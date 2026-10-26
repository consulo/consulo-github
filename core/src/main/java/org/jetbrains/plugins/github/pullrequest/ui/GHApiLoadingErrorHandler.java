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
package org.jetbrains.plugins.github.pullrequest.ui;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.github.localize.GithubLocalize;
import consulo.project.Project;
import consulo.review.util.FutureUtil;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DumbAwareAction;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;

public class GHApiLoadingErrorHandler extends GHRetryLoadingErrorHandler {
    private final Project myProject;
    private final Account myAccount;

    public GHApiLoadingErrorHandler(Project project, Account account, Runnable resetRunnable) {
        super(resetRunnable);
        myProject = project;
        myAccount = account;
    }

    @Override
    public AnAction getActionForError(Throwable error) {
        if (FutureUtil.unwrap(error) instanceof GithubAuthenticationException) {
            return DumbAwareAction.create(GithubLocalize.accountsRelogin(), e ->
                myProject.getApplication().getInstance(AccountManager.class)
                    .requestAuthorization(myAccount, null, myProject, null)
                    .whenComplete((account, ex) -> {
                        if (account != null) {
                            myResetRunnable.run();
                        }
                    }));
        }
        return super.getActionForError(error);
    }
}
