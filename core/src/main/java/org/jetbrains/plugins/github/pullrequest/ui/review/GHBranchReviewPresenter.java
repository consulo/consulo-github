// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.review;

import consulo.accountManagement.Account;
import consulo.annotation.component.ExtensionImpl;
import consulo.logging.Logger;
import consulo.util.lang.Pair;
import git4idea.GitRemoteBranch;
import git4idea.GitStandardLocalBranch;
import git4idea.remote.hosting.GitAsyncExtensions;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitBranchReview;
import git4idea.ui.branch.GitBranchReviewPresenter;
import org.jetbrains.plugins.github.api.GHRepositoryConnection;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestBranchMatch;
import org.jetbrains.plugins.github.pullrequest.GHRepositoryConnectionManager;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@ExtensionImpl
public class GHBranchReviewPresenter implements GitBranchReviewPresenter {
    private static final Logger LOG = Logger.getInstance(GHBranchReviewPresenter.class);

    @Override
    public @Nullable CompletableFuture<Map<GitStandardLocalBranch, @Nullable GitBranchReview>> getReviews(GitRepository repository,
                                                                                                        Set<GitStandardLocalBranch> branches) {
        GHHostedRepositoriesManager repositoriesManager = GHHostedRepositoriesManager.getInstance(repository.getProject());
        if (repositoriesManager.findKnownRepositories(repository).isEmpty()) {
            LOG.debug("No known GitHub repository for " + repository.getRoot().getName() + ", review status is disabled");
            return null;
        }

        GHRepositoryConnection connection = GHRepositoryConnectionManager.getInstance(repository.getProject()).getConnectionState().get();
        if (connection == null) {
            return CompletableFuture.completedFuture(noReviews(branches));
        }

        Map<GitStandardLocalBranch, GitRemoteBranch> remoteBranchByBranch = new LinkedHashMap<>();
        for (GitStandardLocalBranch branch : branches) {
            Pair<GHGitRepositoryMapping, GitRemoteBranch> mappingAndBranch = findHostedRemoteBranchTrackedBy(repositoriesManager, repository, branch);
            if (mappingAndBranch == null) {
                continue;
            }
            if (!connection.getRepo().equals(mappingAndBranch.getFirst())) {
                continue;
            }
            remoteBranchByBranch.put(branch, mappingAndBranch.getSecond());
        }
        if (remoteBranchByBranch.isEmpty()) {
            return CompletableFuture.completedFuture(noReviews(branches));
        }

        Pair<GHRepositoryCoordinates, Account> repoAndAccount = Pair.create(connection.getRepo().getRepository(), connection.getAccount());
        return connection.getDataContext().getCreationService()
            .findOpenPullRequestsByHeadBranches(remoteBranchByBranch.values())
            .thenApply(pullRequests -> {
                Map<String, List<GHPullRequestBranchMatch>> pullRequestsByHeadRef = new HashMap<>();
                for (GHPullRequestBranchMatch pullRequest : pullRequests) {
                    pullRequestsByHeadRef.computeIfAbsent(pullRequest.getHeadRefName(), it -> new ArrayList<>()).add(pullRequest);
                }
                Map<GitStandardLocalBranch, @Nullable GitBranchReview> result = new HashMap<>();
                for (GitStandardLocalBranch branch : branches) {
                    GitRemoteBranch remoteBranch = remoteBranchByBranch.get(branch);
                    List<GHPullRequestBranchMatch> matches =
                        remoteBranch == null ? null : pullRequestsByHeadRef.get(remoteBranch.getNameForRemoteOperations());
                    if (matches == null || matches.isEmpty()) {
                        result.put(branch, null);
                        continue;
                    }
                    result.put(branch, toReview(repository, matches.getFirst(), repoAndAccount, branch));
                }
                return result;
            });
    }

    private static @Nullable Pair<GHGitRepositoryMapping, GitRemoteBranch> findHostedRemoteBranchTrackedBy(GHHostedRepositoriesManager repositoriesManager,
                                                                                                         GitRepository repository,
                                                                                                         GitStandardLocalBranch branch) {
        GitRemoteBranch remoteBranch = GitAsyncExtensions.findFirstRemoteBranchTrackedBy(repository.getInfo(), branch);
        if (remoteBranch == null) {
            return null;
        }
        for (GHGitRepositoryMapping mapping : repositoriesManager.getKnownRepositories()) {
            if (mapping.getRemote().repository() == repository && mapping.getRemote().remote().equals(remoteBranch.getRemote())) {
                return Pair.create(mapping, remoteBranch);
            }
        }
        return null;
    }

    private static Map<GitStandardLocalBranch, @Nullable GitBranchReview> noReviews(Set<GitStandardLocalBranch> branches) {
        Map<GitStandardLocalBranch, @Nullable GitBranchReview> result = new HashMap<>();
        for (GitStandardLocalBranch branch : branches) {
            result.put(branch, null);
        }
        return result;
    }

    private static GitBranchReview toReview(GitRepository repository,
                                            GHPullRequestBranchMatch pullRequest,
                                            Pair<GHRepositoryCoordinates, Account> repoAndAccount,
                                            GitStandardLocalBranch branch) {
        GHPRIdentifier prId = pullRequest.toPRIdentifier();
        String title = pullRequest.getTitle();
        return new GitBranchReview(title != null ? title : branch.getName(), () ->
            GHPRProjectViewModel.getInstance(repository.getProject()).activateAndAwaitProject(repoAndAccount, vm -> vm.viewPullRequest(prId)));
    }
}
