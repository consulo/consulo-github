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
package org.jetbrains.plugins.github.extensions;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.progress.ProgressIndicator;
import consulo.component.ProcessCanceledException;
import consulo.github.localize.GithubLocalize;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.PatternUtil;
import git4idea.fetch.GitFetchHandler;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import jakarta.inject.Inject;
import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GHBranchProtectionRule;
import org.jetbrains.plugins.github.api.util.SimpleGHGQLPagesLoader;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jetbrains.plugins.github.util.GithubProjectSettings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ExtensionImpl
public class GHProtectedBranchRulesLoader implements GitFetchHandler {
    private static final Logger LOG = Logger.getInstance(GHProtectedBranchRulesLoader.class);

    private final AccountManager myAccountManager;

    @Inject
    public GHProtectedBranchRulesLoader(AccountManager accountManager) {
        myAccountManager = accountManager;
    }

    @Override
    public void doAfterSuccessfulFetch(Project project, Map<GitRepository, List<GitRemote>> fetches, ProgressIndicator indicator) {
        try {
            loadProtectionRules(indicator, fetches, project);
        }
        catch (Exception e) {
            if (e instanceof ProcessCanceledException processCanceledException) {
                throw processCanceledException;
            }
            if (LOG.isDebugEnabled()) {
                LOG.info("Error occurred while trying to load branch protection rules", e);
            }
            else {
                LOG.info("Error occurred while trying to load branch protection rules: " + e.getMessage());
            }
        }
    }

    private void loadProtectionRules(ProgressIndicator indicator,
                                     Map<GitRepository, List<GitRemote>> fetches,
                                     Project project) throws Exception {
        AccountType<?> accountType = myAccountManager.findAccountType(GithubAccountType.ID);
        List<Account> accounts = myAccountManager.getAccounts(accountType, project);
        if (accounts.isEmpty()) {
            project.getUIAccess().give(() -> GithubProjectSettings.getInstance(project).setBranchProtectionPatterns(new ArrayList<>()));
            return;
        }

        indicator.setText(GithubLocalize.progressTextLoadingProtectedBranches());

        Set<String> branchProtectionPatterns = new LinkedHashSet<>();
        for (Map.Entry<GitRepository, List<GitRemote>> entry : fetches.entrySet()) {
            indicator.checkCanceled();

            for (GitRemote remote : entry.getValue()) {
                indicator.checkCanceled();

                GHGitRepositoryMapping repositoryMapping = null;
                for (GHGitRepositoryMapping mapping : GHHostedRepositoriesManager.getInstance(project).findKnownRepositories(entry.getKey())) {
                    if (mapping.getRemote().remote().equals(remote)) {
                        repositoryMapping = mapping;
                        break;
                    }
                }
                if (repositoryMapping == null) {
                    continue;
                }

                GithubServerPath serverPath = repositoryMapping.getRepository().getServerPath();
                Account defaultAccount = myAccountManager.getActiveAccount(accountType, project);

                Account account;
                if (defaultAccount != null && serverPath(defaultAccount).equals(serverPath, true)) {
                    account = defaultAccount;
                }
                else {
                    account = findAccount(accounts, serverPath);
                }
                if (account == null) {
                    continue;
                }

                GithubApiRequestExecutor requestExecutor = GithubApiRequestExecutorFactory.getInstance().create(account);

                GHGitRepositoryMapping mapping = repositoryMapping;
                List<GHBranchProtectionRule> rules = new SimpleGHGQLPagesLoader<GHBranchProtectionRule>(requestExecutor,
                    pagination -> GHGQLRequests.Repo.getProtectionRules(mapping.getRepository(), pagination))
                    .loadAll(indicator);
                for (GHBranchProtectionRule rule : rules) {
                    branchProtectionPatterns.add(PatternUtil.convertToRegex(rule.getPattern()));
                }
            }
        }

        project.getUIAccess().give(() -> GithubProjectSettings.getInstance(project).setBranchProtectionPatterns(new ArrayList<>(branchProtectionPatterns)));
    }

    private @Nullable Account findAccount(List<Account> accounts, GithubServerPath serverPath) {
        for (Account account : accounts) {
            if (serverPath(account).equals(serverPath, true)) {
                return account;
            }
        }
        return null;
    }

    private GithubServerPath serverPath(Account account) {
        return GithubAccountType.toServerPath(myAccountManager.getServerUrl(account));
    }
}
