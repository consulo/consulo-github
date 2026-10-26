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

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.http.page.ApiPageUtil;
import consulo.review.async.BatchesLoader;
import consulo.util.lang.StringUtil;
import consulo.util.lang.lazy.LazyValue;
import git4idea.GitRemoteBranch;
import git4idea.branch.GitBranchesCollection;
import git4idea.remote.GitRemoteUrlCoordinates;
import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHRepositoryOwnerName;
import org.jetbrains.plugins.github.api.data.GHRepositoryPullRequestTemplate;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.GithubIssueLabel;
import org.jetbrains.plugins.github.api.data.GithubUser;
import org.jetbrains.plugins.github.api.data.GithubUserWithPermissions;
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

public class GHPRRepositoryDataServiceImpl implements GHPRRepositoryDataService {
    private final Disposable myDisposable;
    private final GithubApiRequestExecutor myRequestExecutor;
    private final GitRemoteUrlCoordinates myRemoteCoordinates;
    private final GHRepositoryCoordinates myRepositoryCoordinates;
    private final GHRepositoryOwnerName myRepoOwner;
    private final String myRepositoryId;
    private final @Nullable String myDefaultBranchName;
    private final boolean myIsFork;

    private final GithubServerPath myServerPath;
    private final GHRepositoryPath myRepoPath;

    private final List<Runnable> myDataReloadListeners = new CopyOnWriteArrayList<>();
    private volatile boolean myDataReloadSignalled;

    private final LazyValue<BatchesLoader<GithubUserWithPermissions>> myCollaboratorsLoader;
    private final LazyValue<BatchesLoader<GHUser>> myContributorsLoader;
    private final LazyValue<BatchesLoader<GHUser>> myAssigneesLoader;
    private final LazyValue<BatchesLoader<GHLabel>> myLabelsLoader;
    private final LazyValue<BatchesLoader<GHTeam>> myTeamsLoader;
    private final LazyValue<CompletableFuture<List<GHRepositoryPullRequestTemplate>>> myTemplatesRequest;

    public GHPRRepositoryDataServiceImpl(Disposable parentDisposable,
                                         GithubApiRequestExecutor requestExecutor,
                                         GitRemoteUrlCoordinates remoteCoordinates,
                                         GHRepositoryCoordinates repositoryCoordinates,
                                         GHRepositoryOwnerName repoOwner,
                                         String repositoryId,
                                         @Nullable String defaultBranchName,
                                         boolean isFork) {
        myDisposable = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentDisposable, myDisposable);
        myRequestExecutor = requestExecutor;
        myRemoteCoordinates = remoteCoordinates;
        myRepositoryCoordinates = repositoryCoordinates;
        myRepoOwner = repoOwner;
        myRepositoryId = repositoryId;
        myDefaultBranchName = defaultBranchName;
        myIsFork = isFork;

        myServerPath = repositoryCoordinates.getServerPath();
        myRepoPath = repositoryCoordinates.getRepositoryPath();

        myCollaboratorsLoader = LazyValue.atomicNotNull(() -> new BatchesLoader<>(myDisposable,
            GithubApiPagesLoader.batchesFlow(myRequestExecutor,
                GithubApiRequests.Repos.Collaborators.pages(myServerPath, myRepoPath.getOwner(), myRepoPath.getRepository()))));

        myContributorsLoader = LazyValue.atomicNotNull(() -> {
            Function<Consumer<List<GithubUser>>, CompletableFuture<?>> batchesFlow = GithubApiPagesLoader.batchesFlow(myRequestExecutor,
                GithubApiRequests.Repos.Contributors.pages(myServerPath, myRepoPath.getOwner(), myRepoPath.getRepository()));
            return new BatchesLoader<>(myDisposable, mapBatchItems(batchesFlow, GHPRRepositoryDataServiceImpl::toGHUser));
        });

        myAssigneesLoader = LazyValue.atomicNotNull(() -> {
            Function<Consumer<List<GithubUser>>, CompletableFuture<?>> batchesFlow = GithubApiPagesLoader.batchesFlow(myRequestExecutor,
                GithubApiRequests.Repos.Assignees.pages(myServerPath, myRepoPath.getOwner(), myRepoPath.getRepository()));
            return new BatchesLoader<>(myDisposable, mapBatchItems(batchesFlow, GHPRRepositoryDataServiceImpl::toGHUser));
        });

        myLabelsLoader = LazyValue.atomicNotNull(() -> {
            Function<Consumer<List<GithubIssueLabel>>, CompletableFuture<?>> batchesFlow =
                GithubApiPagesLoader.batchesFlow(myRequestExecutor,
                    GithubApiRequests.Repos.Labels.pages(myServerPath, myRepoPath.getOwner(), myRepoPath.getRepository()));
            return new BatchesLoader<>(myDisposable,
                mapBatchItems(batchesFlow, it -> new GHLabel(it.getNodeId(), it.getUrl(), it.getName(), it.getColor())));
        });

        myTeamsLoader = LazyValue.atomicNotNull(() -> new BatchesLoader<GHTeam>(myDisposable,
            consumer -> ApiPageUtil.collectGQLPages(
                pagination -> myRequestExecutor.execute(GHGQLRequests.Organization.Team.findAll(myServerPath, myRepoOwner.getLogin(), pagination)),
                page -> consumer.accept(page.getNodes()))));

        myTemplatesRequest = LazyValue.atomicNotNull(() ->
            myRequestExecutor.execute(GHGQLRequests.Repo.loadPullRequestTemplates(myRepositoryCoordinates))
                .thenApply(templates -> templates == null ? List.<GHRepositoryPullRequestTemplate>of() : templates));
    }

    @Override
    public GitRemoteUrlCoordinates getRemoteCoordinates() {
        return myRemoteCoordinates;
    }

    @Override
    public GHRepositoryCoordinates getRepositoryCoordinates() {
        return myRepositoryCoordinates;
    }

    @Override
    public String getRepositoryId() {
        return myRepositoryId;
    }

    @Override
    public @Nullable String getDefaultBranchName() {
        return myDefaultBranchName;
    }

    @Override
    public boolean isFork() {
        return myIsFork;
    }

    @Override
    public void addDataReloadListener(Disposable parentDisposable, Runnable listener) {
        myDataReloadListeners.add(listener);
        Disposer.register(parentDisposable, () -> myDataReloadListeners.remove(listener));
        if (myDataReloadSignalled) {
            listener.run();
        }
    }

    @Override
    public CompletableFuture<?> loadBatchedCollaborators(Consumer<List<GithubUserWithPermissions>> consumer) {
        return myCollaboratorsLoader.get().getBatches(consumer);
    }

    @Override
    public CompletableFuture<?> loadBatchedContributors(Consumer<List<GHUser>> consumer) {
        return myContributorsLoader.get().getBatches(consumer);
    }

    @Override
    public CompletableFuture<?> loadBatchedPotentialIssuesAssignees(Consumer<List<GHUser>> consumer) {
        return myAssigneesLoader.get().getBatches(consumer);
    }

    private static GHUser toGHUser(GithubUser user) {
        return new GHUser(user.getNodeId(), user.getLogin(), user.getHtmlUrl(), Objects.requireNonNullElse(user.getAvatarUrl(), ""), null);
    }

    @Override
    public CompletableFuture<?> loadBatchedLabels(Consumer<List<GHLabel>> consumer) {
        return myLabelsLoader.get().getBatches(consumer);
    }

    @Override
    public CompletableFuture<?> mentionableUsersBatchesFlow(Consumer<List<GHUser>> consumer) {
        return ApiPageUtil.collectGQLPages(
            pagination -> myRequestExecutor.execute(GHGQLRequests.Repo.findMentionableUsers(myRepositoryCoordinates, myServerPath, pagination)),
            page -> consumer.accept(page.getNodes()));
    }

    @Override
    public CompletableFuture<?> loadBatchedTeams(Consumer<List<GHTeam>> consumer) {
        if (!(myRepoOwner instanceof GHRepositoryOwnerName.Organization)) {
            consumer.accept(List.of());
            return CompletableFuture.completedFuture(null);
        }
        return myTeamsLoader.get().getBatches(consumer);
    }

    @Override
    public CompletableFuture<@Nullable String> loadTemplate() {
        return myTemplatesRequest.get().thenApply(templates -> {
            for (GHRepositoryPullRequestTemplate template : templates) {
                if (!StringUtil.isEmptyOrSpaces(template.getBody())) {
                    return template.getBody();
                }
            }
            return null;
        });
    }

    @Override
    public void resetData() {
        myCollaboratorsLoader.get().cancel();
        myContributorsLoader.get().cancel();
        myAssigneesLoader.get().cancel();
        myLabelsLoader.get().cancel();
        myTeamsLoader.get().cancel();
        myDataReloadSignalled = true;
        myDataReloadListeners.forEach(Runnable::run);
    }

    @Override
    public @Nullable GitRemoteBranch getDefaultRemoteBranch() {
        GitRemoteUrlCoordinates currentRemote = getRepositoryMapping().getRemote();
        GitBranchesCollection branches = currentRemote.repository().getBranches();
        if (myDefaultBranchName != null) {
            return branches.findRemoteBranch(currentRemote.remote().getName() + "/" + myDefaultBranchName);
        }

        GitRemoteBranch master = branches.findRemoteBranch(currentRemote.remote().getName() + "/master");
        return master != null ? master : branches.findRemoteBranch(currentRemote.remote().getName() + "/main");
    }

    private static <T, R> Function<Consumer<List<R>>, CompletableFuture<?>> mapBatchItems(Function<Consumer<List<T>>, CompletableFuture<?>> batches,
                                                                                         Function<T, R> mapper) {
        return consumer -> batches.apply(batch -> consumer.accept(batch.stream().map(mapper).toList()));
    }
}
