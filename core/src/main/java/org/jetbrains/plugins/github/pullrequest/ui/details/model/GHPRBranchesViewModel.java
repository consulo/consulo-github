// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.details.model;

import consulo.accountManagement.Account;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.review.details.model.CodeReviewBranches;
import consulo.versionControlSystem.review.details.model.CodeReviewBranchesViewModel;
import git4idea.GitRemoteBranch;
import git4idea.GitStandardRemoteBranch;
import git4idea.remote.GitRemoteUrlCoordinates;
import git4idea.remote.hosting.GitAsyncExtensions;
import git4idea.remote.hosting.GitRemoteBranchesUtil;
import git4idea.remote.hosting.HostedGitRepositoryRemote;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.workingTrees.GitWorkingTreesService;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.data.GHRepository;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GHPRBranchesViewModel implements CodeReviewBranchesViewModel {
    private static final Logger LOG = Logger.getInstance(GHPRBranchesViewModel.class);

    private final Disposable myCs;
    private final Project myProject;
    private final GHGitRepositoryMapping myMapping;
    private final Account myAccount;
    private final ObservableValue<GHPullRequest> myDetailsState;
    private final GitRepository myGitRepository;

    private final ObservableValue<String> myTargetBranch;
    private final ObservableValue<String> mySourceBranch;
    private final MutableObservableValue<Boolean> myIsCheckedOut = MutableObservableValue.of(false);
    private final List<Consumer<CodeReviewBranches>> myShowBranchesRequests = new CopyOnWriteArrayList<>();

    public GHPRBranchesViewModel(Disposable parentCs,
                                 Project project,
                                 GHGitRepositoryMapping mapping,
                                 Account account,
                                 ObservableValue<GHPullRequest> detailsState) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myProject = project;
        myMapping = mapping;
        myAccount = account;
        myDetailsState = detailsState;
        myGitRepository = mapping.getRemote().repository();

        myTargetBranch = detailsState.map(GHPullRequest::getBaseRefName);
        mySourceBranch = detailsState.map(it -> {
            GHRepository headRepository = it.getHeadRepository();
            if (headRepository != null && (headRepository.isFork() || it.getBaseRefName().equals(it.getHeadRefName()))) {
                return headRepository.getOwner().getLogin() + ":" + it.getHeadRefName();
            }
            else {
                return it.getHeadRefName();
            }
        });

        GitAsyncExtensions.changesSignalFlow(myGitRepository, myCs, this::updateCheckedOut);
        Disposer.register(myCs, detailsState.addListener(it -> updateCheckedOut())::run);
        updateCheckedOut();
    }

    private void updateCheckedOut() {
        GHPullRequest details = myDetailsState.get();
        HostedGitRepositoryRemote remote = getHeadRemoteDescriptor(details, myMapping.getRemote());
        if (remote == null) {
            myIsCheckedOut.set(false);
            return;
        }
        GitRemoteBranchesUtil.testRemoteBranchCheckedOut(myGitRepository, remote, details.getHeadRefName())
            .whenComplete((checkedOut, error) -> myIsCheckedOut.set(error == null && Boolean.TRUE.equals(checkedOut)));
    }

    @Override
    public ObservableValue<String> getSourceBranch() {
        return mySourceBranch;
    }

    @Override
    public ObservableValue<Boolean> isCheckedOut() {
        return myIsCheckedOut;
    }

    @Override
    public void addShowBranchesRequestListener(Disposable parentDisposable, Consumer<CodeReviewBranches> listener) {
        myShowBranchesRequests.add(listener);
        Disposer.register(parentDisposable, () -> myShowBranchesRequests.remove(listener));
    }

    @Override
    public void fetchAndCheckoutRemoteBranch() {
        fetchAndCheckoutBranch(myMapping.getRemote(), myDetailsState.get());
    }

    @Override
    public boolean canCheckoutInNewWorktree() {
        // A new worktree can't be created for a branch that is already checked out in the current one.
        return GitWorkingTreesService.isWorktreeCreationSupported(myGitRepository) && !myIsCheckedOut.get();
    }

    @Override
    public void checkoutInNewWorktree() {
        fetchAndCheckoutBranchInNewWorktree(myMapping.getRemote(), myDetailsState.get(), Pair.create(myMapping.getRepository(), myAccount));
    }

    @Override
    public boolean canShowInLog() {
        return true;
    }

    @Override
    public void fetchAndShowInLog() {
        GHPullRequest details = myDetailsState.get();

        HostedGitRepositoryRemote headDescriptor = getHeadRemoteDescriptor(details, myMapping.getRemote());
        GitRemote headRemote = headDescriptor == null ? null : GitRemoteBranchesUtil.findRemote(myGitRepository, headDescriptor);
        if (headRemote == null) {
            return;
        }
        HostedGitRepositoryRemote baseDescriptor = getBaseRemoteDescriptor(details, myMapping.getRemote());
        GitRemote baseRemote = baseDescriptor == null ? null : GitRemoteBranchesUtil.findRemote(myGitRepository, baseDescriptor);
        if (baseRemote == null) {
            return;
        }

        GitRemoteBranch headBranch = new GitStandardRemoteBranch(headRemote, details.getHeadRefName());
        GitRemoteBranch baseBranch = new GitStandardRemoteBranch(baseRemote, details.getBaseRefName());

        GitRemoteBranchesUtil.fetchAndShowRemoteBranchInLog(myGitRepository, headBranch, baseBranch);
    }

    @Override
    public void showBranches() {
        CodeReviewBranches branches = new CodeReviewBranches(mySourceBranch.get(), myTargetBranch.get());
        myShowBranchesRequests.forEach(it -> it.accept(branches));
    }

    /**
     * Creates the descriptor with the host of {@code defaultCoordinates} and the project paths from the API.
     */
    private static HostedGitRepositoryRemote getRemoteDescriptor(GHRepository repository, GitRemoteUrlCoordinates defaultCoordinates) {
        return HostedGitRepositoryRemote.createHostedGitRepositoryRemote(repository.getOwner().getLogin(), repository.getNameWithOwner(),
            repository.getUrl(), repository.getSshUrl(), defaultCoordinates);
    }

    public static @Nullable HostedGitRepositoryRemote getHeadRemoteDescriptor(GHPullRequest details, GitRemoteUrlCoordinates remoteUrlCoordinates) {
        GHRepository headRepository = details.getHeadRepository();
        return headRepository == null ? null : getRemoteDescriptor(headRepository, remoteUrlCoordinates);
    }

    public static @Nullable HostedGitRepositoryRemote getBaseRemoteDescriptor(GHPullRequest details, GitRemoteUrlCoordinates remoteUrlCoordinates) {
        GHRepository baseRepository = details.getBaseRepository();
        return baseRepository == null ? null : getRemoteDescriptor(baseRepository, remoteUrlCoordinates);
    }

    public static CompletableFuture<?> fetchAndCheckoutBranch(GitRemoteUrlCoordinates remoteUrlCoordinates, GHPullRequest details) {
        GHRepository baseRepository = details.getBaseRepository();
        if (baseRepository == null) {
            LOG.warn("Can't checkout remote branch for PR " + details.getNumber() + " because base repository is missing");
            return CompletableFuture.completedFuture(null);
        }
        GHRepository headRepository = details.getHeadRepository();
        if (headRepository == null) {
            LOG.warn("Can't checkout remote branch for PR " + details.getNumber() + " because head repository is missing");
            return CompletableFuture.completedFuture(null);
        }
        boolean isFork = !headRepository.equals(baseRepository) && headRepository.isFork();
        String localPrefix = isFork ? "fork/" + headRepository.getOwner().getLogin() : null;
        HostedGitRepositoryRemote remoteDescriptor = getRemoteDescriptor(headRepository, remoteUrlCoordinates);
        return GitRemoteBranchesUtil.fetchAndCheckoutRemoteBranch(remoteUrlCoordinates.repository(),
            remoteDescriptor,
            details.getHeadRefName(),
            localPrefix);
    }

    public static CompletableFuture<?> fetchAndCheckoutBranchInNewWorktree(GitRemoteUrlCoordinates remoteUrlCoordinates,
                                                                           GHPullRequest details,
                                                                           Pair<GHRepositoryCoordinates, Account> preferredRepoAndAccount) {
        GHRepository baseRepository = details.getBaseRepository();
        if (baseRepository == null) {
            LOG.warn("Can't checkout remote branch in a new worktree for PR " + details.getNumber() + " because base repository is missing");
            return CompletableFuture.completedFuture(null);
        }
        GHRepository headRepository = details.getHeadRepository();
        if (headRepository == null) {
            LOG.warn("Can't checkout remote branch in a new worktree for PR " + details.getNumber() + " because head repository is missing");
            return CompletableFuture.completedFuture(null);
        }
        boolean isFork = !headRepository.equals(baseRepository) && headRepository.isFork();
        String localPrefix = isFork ? "fork/" + headRepository.getOwner().getLogin() : null;
        HostedGitRepositoryRemote remoteDescriptor = getRemoteDescriptor(headRepository, remoteUrlCoordinates);
        GHPRIdentifier prId = details.getPrId();
        String worktreeName = remoteUrlCoordinates.repository().getRoot().getName() + "_PR_" + details.getNumber();
        return CompletableFuture.supplyAsync(() -> GitRemoteBranchesUtil.getReviewWorktreesParentDir(remoteUrlCoordinates.repository().getProject()),
                AppExecutorUtil.getAppExecutorService())
            .thenCompose((Path parentDir) -> GitRemoteBranchesUtil.fetchAndCheckoutInNewWorktree(remoteUrlCoordinates.repository(),
                remoteDescriptor,
                details.getHeadRefName(),
                parentDir,
                worktreeName,
                localPrefix,
                worktreeProject -> GHPRProjectViewModel.getInstance(worktreeProject).activateAndAwaitProject(preferredRepoAndAccount,
                    vm -> vm.openPullRequestInfoAndDiff(prId))));
    }
}
