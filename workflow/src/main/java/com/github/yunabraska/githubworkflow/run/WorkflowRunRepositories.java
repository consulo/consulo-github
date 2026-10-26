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
package com.github.yunabraska.githubworkflow.run;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.exceptions.GithubParseException;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * @author VISTALL
 * @since 2026-10-26
 */
public final class WorkflowRunRepositories {
    private static final String ORIGIN_REMOTE = "origin";

    private WorkflowRunRepositories() {
    }

    public static Optional<Target> resolve(Project project, @Nullable VirtualFile file) {
        if (project.isDisposed()) {
            return Optional.empty();
        }
        GitRepository repository = file == null ? null : GitUtil.getRepositoryManager(project).getRepositoryForFileQuick(file);
        GHHostedRepositoriesManager manager = GHHostedRepositoriesManager.getInstance(project);
        List<GHGitRepositoryMapping> mappings = repository == null
            ? new ArrayList<>(manager.getKnownRepositories())
            : manager.findKnownRepositories(repository);
        return mappings.stream()
            .min(Comparator.comparingInt(mapping -> ORIGIN_REMOTE.equals(mapping.getGitRemote().getName()) ? 0 : 1))
            .map(mapping -> {
                GHRepositoryPath path = mapping.getRepository().getRepositoryPath();
                String branch = mapping.getGitRepository().getCurrentBranchName();
                return new Target(mapping.getRepository().getServerPath(), path.getOwner(), path.getRepository(), branch);
            });
    }

    public static Optional<GithubServerPath> server(@Nullable String serverUrl) {
        if (serverUrl == null || serverUrl.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(GithubServerPath.from(serverUrl.trim()));
        }
        catch (GithubParseException e) {
            return Optional.empty();
        }
    }

    public static Optional<Account> findAccount(Project project, GithubServerPath server) {
        AccountManager accountManager = project.getApplication().getInstance(AccountManager.class);
        AccountType<?> accountType = accountManager.findAccountType(GithubAccountType.ID);
        if (accountType == null) {
            return Optional.empty();
        }
        Account active = accountManager.getActiveAccount(accountType, project);
        if (active != null && !active.isAuthFailed() && matches(accountManager, active, server)) {
            return Optional.of(active);
        }
        return accountManager.getAccounts(accountType, project).stream()
            .filter(account -> matches(accountManager, account, server))
            .min(Comparator.comparingInt(account -> account.isAuthFailed() ? 1 : 0));
    }

    private static boolean matches(AccountManager accountManager, Account account, GithubServerPath server) {
        return server.equals(GithubAccountType.toServerPath(accountManager.getServerUrl(account)), true);
    }

    public record Target(GithubServerPath server, String owner, String repo, @Nullable String branch) {
    }
}
