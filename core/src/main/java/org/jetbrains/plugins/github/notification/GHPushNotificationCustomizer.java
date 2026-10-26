// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code
// is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.notification;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.annotation.component.ExtensionImpl;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.util.FutureUtil;
import consulo.ui.ex.action.AnAction;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.distributed.push.VcsPushOptionValue;
import git4idea.GitRemoteBranch;
import git4idea.push.GitPushNotificationCustomizer;
import git4idea.push.GitPushRepoResult;
import git4idea.push.GitPushRepoResultUtil;
import git4idea.remote.hosting.GitPushNotificationUtil;
import git4idea.repo.GitRepository;
import jakarta.inject.Inject;
import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GHRepositoryConnection;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.data.GHRepository;
import org.jetbrains.plugins.github.api.data.GithubIssueState;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRestIdOnly;
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager;
import org.jetbrains.plugins.github.pullrequest.action.GHOpenPullRequestExistingTabNotificationAction;
import org.jetbrains.plugins.github.pullrequest.action.GHPRCreatePullRequestNotificationAction;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@ExtensionImpl
public class GHPushNotificationCustomizer implements GitPushNotificationCustomizer {
    private static final Logger LOG = Logger.getInstance(GHPushNotificationCustomizer.class);

    private final Project myProject;

    @Inject
    public GHPushNotificationCustomizer(Project project) {
        myProject = project;
    }

    @Override
    public CompletableFuture<List<AnAction>> getActions(GitRepository repository,
                                                        GitPushRepoResult pushResult,
                                                        Map<String, VcsPushOptionValue> customParams) {
        if (!pushResult.isSuccessful()) {
            return CompletableFuture.completedFuture(List.of());
        }
        GitRemoteBranch remoteBranch = GitPushRepoResultUtil.findRemoteBranch(pushResult, repository);
        if (remoteBranch == null) {
            return CompletableFuture.completedFuture(List.of());
        }

        // If we already have a GH connection open, make sure it matches
        GHRepositoryConnection connection = GHRepositoryConnectionManager.getInstance(myProject).getConnectionState().get();
        if (connection != null && (connection.getRepo().getGitRepository() != repository ||
            !connection.getRepo().getGitRemote().equals(remoteBranch.getRemote()))) {
            return CompletableFuture.completedFuture(List.of());
        }

        Pair<GHGitRepositoryMapping, Account> mappingAndAccount;
        if (connection != null) {
            mappingAndAccount = Pair.create(connection.getRepo(), connection.getAccount());
        }
        else {
            AccountManager accountManager = myProject.getApplication().getInstance(AccountManager.class);
            Pair<String, Account> savedUrlAndAccount = GithubPullRequestsProjectUISettings.getInstance(myProject).getSelectedUrlAndAccount();
            mappingAndAccount = GitPushNotificationUtil.findRepositoryAndAccount(
                accountManager,
                GHHostedRepositoriesManager.getInstance(myProject).getKnownRepositories(),
                repository,
                remoteBranch.getRemote(),
                accountManager.getAccounts(Objects.requireNonNull(accountManager.findAccountType(GithubAccountType.ID)), myProject),
                savedUrlAndAccount == null ? null : savedUrlAndAccount.getSecond(),
                null
            );
        }
        if (mappingAndAccount == null) {
            return CompletableFuture.completedFuture(List.of());
        }
        GHGitRepositoryMapping projectMapping = mappingAndAccount.getFirst();
        Account account = mappingAndAccount.getSecond();

        return canCreateReview(projectMapping, account, remoteBranch).thenCompose(canCreate -> {
            if (!canCreate) {
                return CompletableFuture.completedFuture(List.<AnAction>of());
            }
            return findExistingPullRequests(projectMapping, account, remoteBranch).thenApply(existingPrs -> switch (existingPrs.size()) {
                case 0 -> List.<AnAction>of(new GHPRCreatePullRequestNotificationAction(myProject, projectMapping, account));
                case 1 -> {
                    GHPullRequestRestIdOnly singlePr = existingPrs.get(0);
                    yield List.<AnAction>of(new GHOpenPullRequestExistingTabNotificationAction(myProject, projectMapping, account,
                        singlePr.toPRIdentifier()));
                }
                default -> List.<AnAction>of();
            });
        });
    }

    /**
     * Checks if it's even valid to create a PR.
     * For instance:
     * - The repository must exist
     * - The branch cannot be the default branch (we don't allow creating a PR from default -> default)
     */
    private CompletableFuture<Boolean> canCreateReview(GHGitRepositoryMapping repositoryMapping,
                                                       Account account,
                                                       GitRemoteBranch branch) {
        GithubApiRequestExecutor executor = GithubApiRequestExecutorFactory.getInstance().create(account);

        GHRepositoryCoordinates repository = repositoryMapping.getRepository();
        return getRepositoryInfo(executor, repository).thenApply(repositoryInfo -> {
            if (repositoryInfo == null) {
                LOG.warn("Repository not found: " + repository);
                return false;
            }

            // Don't allow creating PRs targeting the default branch
            String remoteBranchName = branch.getNameForRemoteOperations();
            return !Objects.equals(repositoryInfo.getDefaultBranch(), remoteBranchName);
        });
    }

    private CompletableFuture<@Nullable GHRepository> getRepositoryInfo(GithubApiRequestExecutor executor,
                                                                        GHRepositoryCoordinates repository) {
        return executor.execute(GHGQLRequests.Repo.find(repository)).handle((info, error) -> {
            if (error != null) {
                if (FutureUtil.isCancellation(error)) {
                    throw new CompletionException(FutureUtil.unwrap(error));
                }
                LOG.warn("Failed to lookup repository " + repository, FutureUtil.unwrap(error));
                return null;
            }
            return info;
        });
    }

    /**
     * Look up any existing open pull requests on the given remote branch.
     * <p>
     * Only the first two matches are fetched — {@link #getActions} only distinguishes between zero,
     * exactly one, and more-than-one existing pull request.
     */
    private CompletableFuture<List<GHPullRequestRestIdOnly>> findExistingPullRequests(GHGitRepositoryMapping repositoryMapping,
                                                                                      Account account,
                                                                                      GitRemoteBranch branch) {
        GithubApiRequestExecutor executor = GithubApiRequestExecutorFactory.getInstance().create(account);

        GHRepositoryCoordinates repository = repositoryMapping.getRepository();
        String remoteBranchName = branch.getNameForRemoteOperations();

        return executor.execute(GithubApiRequests.Repos.PullRequests.find(repository,
            GithubIssueState.open,
            null,
            repository.getRepositoryPath().getOwner() + ":" + remoteBranchName,
            // only the 0/1/>1 distinction matters, so two results are enough
            new GithubRequestPagination(1, 2)
        )).handle((page, error) -> {
            if (error != null) {
                if (FutureUtil.isCancellation(error)) {
                    throw new CompletionException(FutureUtil.unwrap(error));
                }
                LOG.warn("Failed to lookup existing pull requests for branch " + remoteBranchName + " in " + repository, FutureUtil.unwrap(error));
                return List.of();
            }
            return page.getItems();
        });
    }
}
