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
package org.jetbrains.plugins.github.pullrequest.data.service;

import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.history.ShortVcsRevisionNumber;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.versionControlSystem.log.Hash;
import consulo.versionControlSystem.log.VcsCommitMetadata;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitRevisionNumber;
import git4idea.changes.GitChangeUtils;
import git4idea.history.GitCommitRequirements;
import git4idea.history.GitHistoryUtils;
import git4idea.history.GitLogUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.data.GithubIssueState;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestBranchMatch;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRestIdOnly;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort;
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination;
import org.jetbrains.plugins.github.exceptions.GithubConfusingException;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class GHPRCreationServiceImpl implements GHPRCreationService {
    private static final Logger LOG = Logger.getInstance(GHPRCreationServiceImpl.class);

    private final GithubApiRequestExecutor myRequestExecutor;
    private final GHGitRepositoryMapping myBaseRepo;
    private final String myRepositoryId;

    public GHPRCreationServiceImpl(GithubApiRequestExecutor requestExecutor, GHPRRepositoryDataService repositoryDataService) {
        myRequestExecutor = requestExecutor;
        myBaseRepo = repositoryDataService.getRepositoryMapping();
        myRepositoryId = repositoryDataService.getRepositoryId();
    }

    @Override
    public CompletableFuture<GHPullRequestShort> createPullRequest(GitRemoteBranch baseBranch,
                                                                   GHGitRepositoryMapping headRepo,
                                                                   GitRemoteBranch headBranch,
                                                                   String title,
                                                                   String description,
                                                                   boolean draft) {
        String headRepositoryPrefix = getHeadRepoPrefix(headRepo);

        String actualTitle = !StringUtil.isEmptyOrSpaces(title) ? title : headBranch.getNameForRemoteOperations();
        String body = StringUtil.nullize(description, true);

        return myRequestExecutor.execute(GHGQLRequests.PullRequest.create(myBaseRepo.getRepository(), myRepositoryId,
            baseBranch.getNameForRemoteOperations(),
            headRepositoryPrefix + headBranch.getNameForRemoteOperations(),
            actualTitle, body, draft
        ));
    }

    @Override
    public CompletableFuture<@Nullable GHPRIdentifier> findOpenPullRequest(@Nullable GitRemoteBranch baseBranch,
                                                                           GHRepositoryPath headRepo,
                                                                           GitRemoteBranch headBranch) {
        return findOpenPullRequestDetails(baseBranch, headRepo, headBranch)
            .thenApply(details -> details == null ? null : details.toPRIdentifier());
    }

    @Override
    public CompletableFuture<@Nullable GHPullRequestRestIdOnly> findOpenPullRequestDetails(@Nullable GitRemoteBranch baseBranch,
                                                                                           GHRepositoryPath headRepo,
                                                                                           GitRemoteBranch headBranch) {
        return myRequestExecutor.execute(
            GithubApiRequests.Repos.PullRequests.find(myBaseRepo.getRepository(),
                GithubIssueState.open,
                baseBranch == null ? null : baseBranch.getNameForRemoteOperations(),
                headRepo.getOwner() + ":" + headBranch.getNameForRemoteOperations(),
                // only the first match is used, so fetch a single result
                new GithubRequestPagination(1, 1)
            )).thenApply(page -> page.getItems().isEmpty() ? null : page.getItems().get(0));
    }

    // The advanced search syntax this query needs works starting from GitHub Enterprise Server 3.17.
    @Override
    public CompletableFuture<List<GHPullRequestBranchMatch>> findOpenPullRequestsByHeadBranches(Collection<GitRemoteBranch> headBranches) {
        if (headBranches.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        // The "head" search qualifier takes a bare branch name, unlike the REST "head" parameter, which takes "owner:branch-name".
        List<String> headBranchRefs = headBranches.stream().map(GitRemoteBranch::getNameForRemoteOperations).toList();
        return myRequestExecutor.execute(GHGQLRequests.PullRequest.searchByHeadBranches(myBaseRepo.getRepository(), headBranchRefs))
            .thenApply(response -> response.getNodes())
            .exceptionally(error -> {
                Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                if (cause instanceof GithubConfusingException) {
                    // The server doesn't support the advanced search syntax the query needs (e.g. an older GHE version). Show no reviews.
                    LOG.info("Batched pull request search is not supported by the server", cause);
                    return List.of();
                }
                throw error instanceof CompletionException ce ? ce : new CompletionException(error);
            });
    }

    private String getHeadRepoPrefix(GHGitRepositoryMapping headRepo) {
        return myBaseRepo.getRepository().equals(headRepo.getRepository()) ? "" : headRepo.getRepository().getRepositoryPath().getOwner() + ":";
    }

    @Override
    public CompletableFuture<Collection<RefComparisonChange>> getDiff(VcsCommitMetadata commit) {
        return supplyAsync(() -> {
            List<RefComparisonChange> result = new ArrayList<>();
            GitRepository gitRepository = myBaseRepo.getGitRepository();
            GitLogUtil.readFullDetailsForHashes(gitRepository.getProject(), gitRepository.getRoot(),
                List.of(commit.getId().asString()),
                GitCommitRequirements.DEFAULT, gitCommit -> {
                    for (Change change : gitCommit.getChanges()) {
                        result.add(toComparisonChange(change, commit.getParents().get(0).asString(), commit.getId().asString()));
                    }
                });
            return result;
        });
    }

    @Override
    public CompletableFuture<Collection<RefComparisonChange>> getDiff(GitRemoteBranch baseBranch, GitBranch headBranch) {
        return supplyAsync(() -> {
            GitRepository gitRepository = myBaseRepo.getGitRepository();
            GitRevisionNumber mergeBase = GitHistoryUtils.getMergeBase(gitRepository.getProject(), gitRepository.getRoot(),
                baseBranch.getName(), headBranch.getName());
            if (mergeBase == null) {
                throw new IllegalStateException("Unrelated branches " + baseBranch.getName() + " " + headBranch.getName());
            }
            Hash headRef = gitRepository.getBranches().getHash(headBranch);
            if (headRef == null) {
                throw new IllegalStateException("Branch " + headBranch.getName() + " has no revision");
            }
            return GitChangeUtils.getThreeDotDiffOrThrow(gitRepository, baseBranch.getName(), headBranch.getName()).stream()
                .map(it -> toComparisonChange(it, mergeBase.getRev(), headRef.asString()))
                .toList();
        });
    }

    private static RefComparisonChange toComparisonChange(Change change, String commitBefore, String commitAfter) {
        ContentRevision beforeRevision = change.getBeforeRevision();
        ContentRevision afterRevision = change.getAfterRevision();
        ShortVcsRevisionNumber beforeRef = asShort(beforeRevision, commitBefore);
        ShortVcsRevisionNumber afterRef = asShort(afterRevision, commitAfter);
        return new RefComparisonChange(
            beforeRef, beforeRevision == null ? null : beforeRevision.getFile(),
            afterRef, afterRevision == null ? null : afterRevision.getFile()
        );
    }

    private static ShortVcsRevisionNumber asShort(@Nullable ContentRevision revision, String fallback) {
        VcsRevisionNumber number = revision == null ? null : revision.getRevisionNumber();
        return number instanceof ShortVcsRevisionNumber shortNumber ? shortNumber : new GitRevisionNumber(fallback);
    }

    private static <T> CompletableFuture<T> supplyAsync(ThrowingSupplier<T> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            }
            catch (VcsException e) {
                throw new CompletionException(e);
            }
        }, AppExecutorUtil.getAppExecutorService());
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws VcsException;
    }
}
