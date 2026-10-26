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
package org.jetbrains.plugins.github.extensions;

import consulo.accountManagement.Account;
import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import git4idea.remote.GitRepositoryHostingService;
import git4idea.remote.InteractiveGitHttpAuthDataProvider;
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser;
import org.jetbrains.plugins.github.util.GithubUtil;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

@ExtensionImpl
public class GHRepositoryHostingService extends GitRepositoryHostingService {
    @Override
    public LocalizeValue getServiceDisplayName() {
        return LocalizeValue.of(GithubUtil.SERVICE_DISPLAY_NAME);
    }

    @Override
    public @Nullable InteractiveGitHttpAuthDataProvider getInteractiveAuthDataProvider(Project project, String url) {
        Map<Account, @Nullable String> accounts = GHSilentHttpAuthDataProvider.getAccountsWithTokens(project, url).join();
        if (accounts.isEmpty()) {
            return null;
        }
        return new GHSelectAccountHttpAuthDataProvider(project, accounts);
    }

    @Override
    public @Nullable InteractiveGitHttpAuthDataProvider getInteractiveAuthDataProvider(Project project, String url, String login) {
        Map<Account, @Nullable String> accounts = new LinkedHashMap<>();
        GHSilentHttpAuthDataProvider.getAccountsWithTokens(project, url).join().forEach((acc, token) -> {
            if (token == null) {
                return;
            }
            GithubAuthenticatedUser details = GHSilentHttpAuthDataProvider.getAccountDetails(acc).join();
            if (details == null || !details.getLogin().equals(login)) {
                return;
            }
            accounts.put(acc, token);
        });
        if (accounts.isEmpty()) {
            return null;
        }
        return new GHSelectAccountHttpAuthDataProvider(project, accounts);
    }
}
