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
package org.jetbrains.plugins.github.authentication.accounts;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Handles default Github account for project
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GithubProjectDefaultAccountHolder {
    private final Project myProject;
    private final AccountManager myAccountManager;

    @Inject
    public GithubProjectDefaultAccountHolder(Project project, AccountManager accountManager) {
        myProject = project;
        myAccountManager = accountManager;
    }

    public static GithubProjectDefaultAccountHolder getInstance(Project project) {
        return project.getInstance(GithubProjectDefaultAccountHolder.class);
    }

    public @Nullable Account getAccount() {
        AccountType<?> type = myAccountManager.findAccountType(GithubAccountType.ID);
        return type == null ? null : myAccountManager.getActiveAccount(type, myProject);
    }

    public void setAccount(@Nullable Account account) {
        AccountType<?> type = myAccountManager.findAccountType(GithubAccountType.ID);
        if (type != null) {
            myAccountManager.setActiveAccount(type, account, myProject);
        }
    }
}
