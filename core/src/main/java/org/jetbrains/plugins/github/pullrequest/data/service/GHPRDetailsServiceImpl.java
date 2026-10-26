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
package org.jetbrains.plugins.github.pullrequest.data.service;

import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.util.CollectionDelta;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.GithubIssueState;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam;
import org.jetbrains.plugins.github.pullrequest.GHNotFoundException;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState;
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityStateBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static consulo.review.util.ResultUtil.processErrorAndGet;
import static consulo.review.util.ResultUtil.runCatchingUser;

public class GHPRDetailsServiceImpl implements GHPRDetailsService {
    private static final Logger LOG = Logger.getInstance(GHPRDetailsService.class);

    private final Project myProject;
    private final GHPRSecurityService mySecurityService;
    private final GithubApiRequestExecutor myRequestExecutor;
    private final GHRepositoryCoordinates myRepository;

    private final GithubServerPath myServerPath;
    private final GHRepositoryPath myRepoPath;

    public GHPRDetailsServiceImpl(Project project,
                                  GHPRSecurityService securityService,
                                  GithubApiRequestExecutor requestExecutor,
                                  GHRepositoryCoordinates repository) {
        myProject = project;
        mySecurityService = securityService;
        myRequestExecutor = requestExecutor;
        myRepository = repository;
        myServerPath = repository.getServerPath();
        myRepoPath = repository.getRepositoryPath();
    }

    @Override
    public CompletableFuture<@Nullable GHPRIdentifier> findPRId(long number) {
        return runCatchingUser(() -> myRequestExecutor.execute(GHGQLRequests.PullRequest.findOneId(myRepository, number)))
            .exceptionally(e -> null);
    }

    @Override
    public CompletableFuture<GHPullRequest> loadDetails(GHPRIdentifier pullRequestId) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(GHGQLRequests.PullRequest.findOne(myRepository, pullRequestId.number()))
            .thenApply(details -> {
                if (details == null) {
                    throw new CompletionException(new GHNotFoundException("Pull request " + pullRequestId.number() + " does not exist"));
                }
                return details;
            })), e -> LOG.info("Error occurred while loading PR details", e));
    }

    @Override
    public CompletableFuture<GHPullRequest> updateDetails(GHPRIdentifier pullRequestId, @Nullable String title, @Nullable String description) {
        return processErrorAndGet(runCatchingUser(() ->
                myRequestExecutor.execute(GHGQLRequests.PullRequest.update(myRepository, pullRequestId.id(), title, description))),
            e -> LOG.info("Error occurred while updating PR details", e));
    }

    @Override
    public CompletableFuture<?> adjustReviewers(GHPRIdentifier pullRequestId, CollectionDelta<GHPullRequestRequestedReviewer> delta) {
        return processErrorAndGet(runCatchingUser(() -> {
            Collection<GHPullRequestRequestedReviewer> removedItems = delta.getRemovedItems();
            CompletableFuture<?> removed = removedItems.isEmpty()
                ? CompletableFuture.completedFuture(null)
                : myRequestExecutor.execute(GithubApiRequests.Repos.PullRequests.Reviewers
                .remove(myServerPath, myRepoPath.getOwner(), myRepoPath.getRepository(), pullRequestId.number(),
                    logins(removedItems), slugs(removedItems)));
            return removed.thenCompose(ignored -> {
                Collection<GHPullRequestRequestedReviewer> newItems = delta.getNewItems();
                if (newItems.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                return myRequestExecutor.execute(GithubApiRequests.Repos.PullRequests.Reviewers
                    .add(myServerPath, myRepoPath.getOwner(), myRepoPath.getRepository(), pullRequestId.number(),
                        logins(newItems), slugs(newItems)));
            });
        }), e -> LOG.info("Error occurred while adjusting the list of reviewers", e));
    }

    private static List<String> logins(Collection<GHPullRequestRequestedReviewer> items) {
        return items.stream().filter(GHUser.class::isInstance).map(it -> ((GHUser) it).getLogin()).toList();
    }

    private static List<String> slugs(Collection<GHPullRequestRequestedReviewer> items) {
        return items.stream().filter(GHTeam.class::isInstance).map(it -> ((GHTeam) it).getSlug()).toList();
    }

    @Override
    public CompletableFuture<?> adjustAssignees(GHPRIdentifier pullRequestId, CollectionDelta<GHUser> delta) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(GithubApiRequests.Repos.Issues
                .updateAssignees(myServerPath, myRepoPath.getOwner(), myRepoPath.getRepository(),
                    String.valueOf(pullRequestId.number()),
                    delta.getNewCollection().stream().map(GHUser::getLogin).toList()))),
            e -> LOG.error("Error occurred while adjusting the list of assignees", e));
    }

    @Override
    public CompletableFuture<?> adjustLabels(GHPRIdentifier pullRequestId, CollectionDelta<GHLabel> delta) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(GithubApiRequests.Repos.Issues.Labels
                .replace(myServerPath, myRepoPath.getOwner(), myRepoPath.getRepository(), String.valueOf(pullRequestId.number()),
                    delta.getNewCollection().stream().map(GHLabel::getName).toList()))),
            e -> LOG.error("Error occurred while adjusting the list of labels", e));
    }

    @Override
    public CompletableFuture<GHPRMergeabilityState> loadMergeabilityState(GHPRIdentifier pullRequestId) {
        return processErrorAndGet(runCatchingUser(() ->
                myRequestExecutor.execute(GHGQLRequests.PullRequest.mergeabilityData(myRepository, pullRequestId.number()))
                    .thenApply(mergeabilityData -> {
                        if (mergeabilityData == null) {
                            throw new IllegalStateException("Could not find pull request " + pullRequestId.number());
                        }
                        boolean currentUserIsAdmin = mySecurityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.ADMIN);
                        return new GHPRMergeabilityStateBuilder(mergeabilityData, currentUserIsAdmin).build();
                    })),
            e -> LOG.info("Error occurred while loading mergeability state data for PR " + pullRequestId.number(), e));
    }

    @Override
    public CompletableFuture<?> close(GHPRIdentifier pullRequestId) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GithubApiRequests.Repos.PullRequests.update(myServerPath, myRepoPath.getOwner(), myRepoPath.getRepository(),
                    pullRequestId.number(), null, null, GithubIssueState.closed, null, null))),
            e -> LOG.info("Error occurred while closing PR " + pullRequestId.number(), e));
    }

    @Override
    public CompletableFuture<?> reopen(GHPRIdentifier pullRequestId) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GithubApiRequests.Repos.PullRequests.update(myServerPath, myRepoPath.getOwner(), myRepoPath.getRepository(),
                    pullRequestId.number(), null, null, GithubIssueState.open, null, null))),
            e -> LOG.info("Error occurred while reopening PR " + pullRequestId.number(), e));
    }

    @Override
    public CompletableFuture<?> markReadyForReview(GHPRIdentifier pullRequestId) {
        return processErrorAndGet(runCatchingUser(() ->
                myRequestExecutor.execute(GHGQLRequests.PullRequest.markReadyForReview(myRepository, pullRequestId.id()))),
            e -> LOG.info("Error occurred while marking PR " + pullRequestId.number() + " ready for review", e));
    }

    @Override
    public CompletableFuture<?> merge(GHPRIdentifier pullRequestId, Pair<String, String> commitMessage, String currentHeadRef) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GithubApiRequests.Repos.PullRequests.merge(myServerPath, myRepoPath, pullRequestId.number(),
                    commitMessage.getFirst(), commitMessage.getSecond(),
                    currentHeadRef))),
            e -> LOG.info("Error occurred while merging PR " + pullRequestId.number(), e));
    }

    @Override
    public CompletableFuture<?> rebaseMerge(GHPRIdentifier pullRequestId, String currentHeadRef) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GithubApiRequests.Repos.PullRequests.rebaseMerge(myServerPath, myRepoPath, pullRequestId.number(),
                    currentHeadRef))),
            e -> LOG.info("Error occurred while rebasing PR " + pullRequestId.number(), e));
    }

    @Override
    public CompletableFuture<?> squashMerge(GHPRIdentifier pullRequestId, Pair<String, String> commitMessage, String currentHeadRef) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GithubApiRequests.Repos.PullRequests.squashMerge(myServerPath, myRepoPath, pullRequestId.number(),
                    commitMessage.getFirst(), commitMessage.getSecond(),
                    currentHeadRef))),
            e -> LOG.info("Error occurred while squash-merging PR " + pullRequestId.number(), e));
    }

    @Override
    public CompletableFuture<?> deleteMergedBranch(GHPRIdentifier pullRequestId, String refId) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GHGQLRequests.Ref.delete(myRepository.getServerPath(), refId))),
            e -> LOG.info("Error occurred while deleting branch for PR " + pullRequestId.number(), e));
    }
}
