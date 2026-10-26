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
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create;

import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.ListSelection;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.git.localize.GitLocalize;
import consulo.project.Project;
import consulo.review.icon.IconsProvider;
import consulo.review.util.CollectionDelta;
import consulo.review.util.ComputedResult;
import consulo.review.util.FutureUtil;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.distributed.DvcsUtil;
import consulo.versionControlSystem.distributed.push.PushSpec;
import consulo.versionControlSystem.log.Hash;
import consulo.versionControlSystem.log.VcsCommitMetadata;
import consulo.versionControlSystem.review.details.model.CodeReviewChangeListViewModelBase;
import git4idea.GitBranch;
import git4idea.GitLocalBranch;
import git4idea.GitPushUtil;
import git4idea.GitRemoteBranch;
import git4idea.GitStandardRemoteBranch;
import git4idea.GitVcs;
import git4idea.history.GitLogUtil;
import git4idea.push.GitPushOperation;
import git4idea.push.GitPushRepoResult;
import git4idea.push.GitPushSource;
import git4idea.push.GitPushSupport;
import git4idea.push.GitPushTarget;
import git4idea.remote.hosting.GitAsyncExtensions;
import git4idea.repo.GitBranchTrackInfo;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepoInfo;
import git4idea.repo.GitRepository;
import git4idea.validators.GitRefNameValidator;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRDetailsService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataServiceExtensions;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService;
import org.jetbrains.plugins.github.ui.component.LabeledListPanelViewModel;
import org.jetbrains.plugins.github.ui.util.GHUIUtil;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class GHPRCreateViewModelImpl implements GHPRCreateViewModel, Disposable {
    private final Project myProject;
    private final GHHostedRepositoriesManager myRepositoryManager;
    private final GithubPullRequestsProjectUISettings mySettings;
    private final GHPRDataContext myDataContext;
    private final Consumer<GHPRIdentifier> myViewPullRequest;
    private final Runnable myCloseNewPullRequest;
    private final BiConsumer<@Nullable GHPRIdentifier, Boolean> myOpenPullRequestDiff;
    private final Runnable myRefreshPrOnCurrentBranch;

    private final Disposable myCs;
    private final IconsProvider<String> myAvatarIconsProvider;
    private final GHPRRepositoryDataService myRepoData;

    private final Object myLock = new Object();

    private volatile BranchesStateWithProgress myBranchesWithProgress;
    private final MutableObservableValue<BranchesState> myBranches;

    private final MutableObservableValue<@Nullable ComputedResult<@Nullable GHPRCreateChangesViewModel>> myChangesVm =
        MutableObservableValue.of(null);
    private @Nullable Disposable myChangesVmScope;

    private final GHPRCreateDiffViewModel myDiffVm;

    private final MutableObservableValue<String> myTitleText = MutableObservableValue.of("");
    private final MutableObservableValue<String> myDescriptionText = MutableObservableValue.of("");
    private final MutableObservableValue<ComputedResult<@Nullable String>> myTemplateLoadingState =
        MutableObservableValue.of(ComputedResult.loading());
    private final ObservableValue<Boolean> myIsTemplateLoading;

    private final LabeledListPanelViewModel<GHUser> myAssigneesVm;
    private final LabeledListPanelViewModel<GHPullRequestRequestedReviewer> myReviewersVm;
    private final LabeledListPanelViewModel<GHLabel> myLabelsVm;

    private final MutableObservableValue<@Nullable ComputedResult<BranchesCheckResult>> myBranchesCheckState =
        MutableObservableValue.of(null);

    private final MutableObservableValue<@Nullable CreationState> myCreationProgress = MutableObservableValue.of(null);
    private volatile @Nullable CompletableFuture<?> myCreation;

    private final Object myTitleLock = new Object();
    private boolean myAutoTitleCancelled;

    private volatile @Nullable Function<String, CompletableFuture<String>> myRemoteBranchNameCallback;

    public GHPRCreateViewModelImpl(Project project,
                                   Disposable parentCs,
                                   GHHostedRepositoriesManager repositoryManager,
                                   GithubPullRequestsProjectUISettings settings,
                                   GHPRDataContext dataContext,
                                   Consumer<GHPRIdentifier> viewPullRequest,
                                   Runnable closeNewPullRequest,
                                   BiConsumer<@Nullable GHPRIdentifier, Boolean> openPullRequestDiff,
                                   Runnable refreshPrOnCurrentBranch) {
        myProject = project;
        myRepositoryManager = repositoryManager;
        mySettings = settings;
        myDataContext = dataContext;
        myViewPullRequest = viewPullRequest;
        myCloseNewPullRequest = closeNewPullRequest;
        myOpenPullRequestDiff = openPullRequestDiff;
        myRefreshPrOnCurrentBranch = refreshPrOnCurrentBranch;

        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        Disposer.register(myCs, () -> {
            CompletableFuture<?> creation = myCreation;
            if (creation != null) {
                creation.cancel(false);
            }
        });

        myAvatarIconsProvider = dataContext.getAvatarIconsProvider();
        myRepoData = dataContext.getRepositoryDataService();

        myDiffVm = new GHPRCreateDiffViewModel(project, myCs);

        myIsTemplateLoading = myTemplateLoadingState.map(ComputedResult::isInProgress);

        myAssigneesVm = new MetadataListViewModel<>(myCs, myRepoData::addDataReloadListener,
            myRepoData::loadBatchedPotentialIssuesAssignees);

        GHPRSecurityService securityService = dataContext.getSecurityService();
        myReviewersVm = new MetadataListViewModel<GHPullRequestRequestedReviewer>(myCs, myRepoData::addDataReloadListener, consumer ->
            GHPRRepositoryDataServiceExtensions.getBatchedPotentialReviewers(myRepoData, securityService, batch -> consumer.accept(
                batch.stream().filter(it -> !it.getId().equals(securityService.getCurrentUser().getId())).toList()
            )));

        myLabelsVm = new MetadataListViewModel<>(myCs, myRepoData::addDataReloadListener, myRepoData::loadBatchedLabels);

        CompletableFuture<@Nullable String> template = myRepoData.loadTemplate();
        Disposer.register(myCs, () -> template.cancel(false));
        template.whenComplete((value, error) -> {
            if (error == null) {
                myTemplateLoadingState.set(ComputedResult.success(value));
                myDescriptionText.set(Objects.requireNonNullElse(value, ""));
            }
            else if (!FutureUtil.isCancellation(error)) {
                myTemplateLoadingState.set(ComputedResult.failure(FutureUtil.unwrap(error)));
                myDescriptionText.set("");
            }
        });

        myDiffVm.handleSelection(myCs, selection -> {
            int selectedIdx = selection.getSelectedIndex();
            List<RefComparisonChange> list = selection.getList();
            if (selectedIdx < 0 || selectedIdx >= list.size()) {
                return;
            }
            RefComparisonChange change = list.get(selectedIdx);
            ComputedResult<@Nullable GHPRCreateChangesViewModel> changesResult = myChangesVm.get();
            GHPRCreateChangesViewModel changesVm = changesResult == null ? null : changesResult.getOrNull();
            if (changesVm == null) {
                return;
            }
            //TODO: handle different commit
            GHPRCreateChangesViewModel.CommitChangesViewModel commitChangesVm = changesVm.getCommitChangesVm().get();
            CodeReviewChangeListViewModelBase changeListVm = commitChangesVm.getChangeListVm().get().getOrNull();
            if (changeListVm != null) {
                changeListVm.selectChange(change);
            }
        });

        BranchesStateWithProgress initial = new BranchesStateWithProgress(getDefaultBranches());
        myBranchesWithProgress = initial;
        myBranches = MutableObservableValue.of(initial.myBranches);
        synchronized (myLock) {
            onBranchesStateChanged(initial);
        }
    }

    @Override
    public Project getProject() {
        return myProject;
    }

    @Override
    public IconsProvider<String> getAvatarIconsProvider() {
        return myAvatarIconsProvider;
    }

    @Override
    public String getRepositoryName() {
        Set<GHRepositoryCoordinates> repositories = getRepositories().stream()
            .map(GHGitRepositoryMapping::getRepository)
            .collect(Collectors.toSet());
        return GHUIUtil.getRepositoryDisplayName(repositories, myRepoData.getRepositoryCoordinates());
    }

    @Override
    public Collection<GHGitRepositoryMapping> getRepositories() {
        return myRepositoryManager.getKnownRepositories();
    }

    @Override
    public ObservableValue<BranchesState> getBranches() {
        return myBranches;
    }

    @Override
    public ObservableValue<@Nullable ComputedResult<@Nullable GHPRCreateChangesViewModel>> getChangesVm() {
        return myChangesVm;
    }

    @Override
    public GHPRCreateDiffViewModel getDiffVm() {
        return myDiffVm;
    }

    @Override
    public ObservableValue<String> getTitleText() {
        return myTitleText;
    }

    @Override
    public ObservableValue<String> getDescriptionText() {
        return myDescriptionText;
    }

    @Override
    public ObservableValue<ComputedResult<@Nullable String>> getTemplateLoadingState() {
        return myTemplateLoadingState;
    }

    @Override
    public ObservableValue<Boolean> isTemplateLoading() {
        return myIsTemplateLoading;
    }

    @Override
    public LabeledListPanelViewModel<GHUser> getAssigneesVm() {
        return myAssigneesVm;
    }

    @Override
    public LabeledListPanelViewModel<GHPullRequestRequestedReviewer> getReviewersVm() {
        return myReviewersVm;
    }

    @Override
    public LabeledListPanelViewModel<GHLabel> getLabelsVm() {
        return myLabelsVm;
    }

    @Override
    public ObservableValue<@Nullable ComputedResult<BranchesCheckResult>> getBranchesCheckState() {
        return myBranchesCheckState;
    }

    @Override
    public ObservableValue<@Nullable CreationState> getCreationProgress() {
        return myCreationProgress;
    }

    @Override
    public @Nullable Function<String, CompletableFuture<String>> getRemoteBranchNameCallback() {
        return myRemoteBranchNameCallback;
    }

    @Override
    public void setRemoteBranchNameCallback(@Nullable Function<String, CompletableFuture<String>> callback) {
        myRemoteBranchNameCallback = callback;
    }

    @Override
    public void refreshBranchesState() {
        updateBranches(branches -> branches);
    }

    private void updateBranches(Function<BranchesState, @Nullable BranchesState> newBranches) {
        synchronized (myLock) {
            if (Disposer.isDisposed(myCs)) {
                return;
            }
            BranchesStateWithProgress current = myBranchesWithProgress;
            BranchesState next = newBranches.apply(current.myBranches);
            if (next == null) {
                return;
            }
            current.cancel();
            BranchesStateWithProgress state = new BranchesStateWithProgress(next);
            myBranchesWithProgress = state;
            onBranchesStateChanged(state);
        }
    }

    private void onBranchesStateChanged(BranchesStateWithProgress state) {
        myBranches.set(state.myBranches);

        disposeChangesVmScope();
        myChangesVm.set(null);

        state.myCheckWithCommits = initBranchesCheck(state);

        state.start();
    }

    private void onCommitsRequest(BranchesStateWithProgress state, @Nullable CompletableFuture<List<VcsCommitMetadata>> commitsRequest) {
        synchronized (myLock) {
            if (state != myBranchesWithProgress) {
                return;
            }

            GitRemoteBranch baseBranch = state.myBranches.baseBranch();
            GitBranch headBranch = state.myBranches.headBranch();
            if (baseBranch != null && headBranch != null) {
                updateChangesVm(state, baseBranch, headBranch, commitsRequest);
            }

            if (state.myCheckWithCommits && baseBranch != null && headBranch != null) {
                updateBranchesCheck(state, baseBranch, headBranch, commitsRequest);
            }

            if (headBranch != null && commitsRequest != null) {
                commitsRequest.whenComplete((commits, error) -> {
                    if (error == null) {
                        setTitleFromFirstCommitOrBranch(state, commitsRequest, headBranch, commits);
                    }
                });
            }
        }
    }

    private boolean isCurrent(BranchesStateWithProgress state, CompletableFuture<?> commitsRequest) {
        return state == myBranchesWithProgress && state.myCommitsRequest.get() == commitsRequest;
    }

    private void disposeChangesVmScope() {
        Disposable scope = myChangesVmScope;
        myChangesVmScope = null;
        if (scope != null) {
            Disposer.dispose(scope);
        }
    }

    private void updateChangesVm(BranchesStateWithProgress state,
                                 GitRemoteBranch baseBranch,
                                 GitBranch headBranch,
                                 @Nullable CompletableFuture<List<VcsCommitMetadata>> commitsRequest) {
        disposeChangesVmScope();
        if (commitsRequest == null) {
            myChangesVm.set(null);
            return;
        }
        myChangesVm.set(ComputedResult.loading());
        commitsRequest.whenComplete((commits, error) -> {
            synchronized (myLock) {
                if (!isCurrent(state, commitsRequest)) {
                    return;
                }
                if (error != null) {
                    if (FutureUtil.isCancellation(error)) {
                        myChangesVm.set(null);
                    }
                    else {
                        myChangesVm.set(ComputedResult.failure(FutureUtil.unwrap(error)));
                    }
                    return;
                }
                if (commits.isEmpty()) {
                    myChangesVm.set(ComputedResult.success(null));
                    return;
                }
                disposeChangesVmScope();
                Disposable scope = Disposable.newDisposable("GHPRCreateChangesViewModel scope");
                if (!Disposer.tryRegister(myCs, scope)) {
                    return;
                }
                myChangesVmScope = scope;
                GHPRCreateChangesViewModel vm = new GHPRCreateChangesViewModel(myProject, mySettings, scope, myDataContext,
                    baseBranch, headBranch, commits, myOpenPullRequestDiff);
                handleChangesSelection(scope, vm);
                myChangesVm.set(ComputedResult.success(vm));
            }
        });
    }

    private void handleChangesSelection(Disposable scope, GHPRCreateChangesViewModel changesVm) {
        collectScoped(scope, changesVm.getCommitChangesVm(), (commitScope, commitChangesVm) ->
            collectScoped(commitScope, commitChangesVm.getChangeListVm(), (changeListScope, changeListResult) -> {
                CodeReviewChangeListViewModelBase changeListVm = changeListResult.getOrNull();
                if (changeListVm == null) {
                    return;
                }
                changeListVm.handleSelection(changeListScope, selection -> {
                    if (selection != null) {
                        myDiffVm.showChanges(ListSelection.createAt(selection.getChanges(), selection.getSelectedIdx()));
                    }
                });
            }));
    }

    private static <T> void collectScoped(Disposable parent, ObservableValue<T> value, BiConsumer<Disposable, T> consumer) {
        Object lock = new Object();
        Disposable[] current = new Disposable[1];
        Consumer<T> handler = newValue -> {
            synchronized (lock) {
                Disposable previous = current[0];
                current[0] = null;
                if (previous != null) {
                    Disposer.dispose(previous);
                }
                Disposable scope = Disposable.newDisposable("GHPRCreateViewModel.collectScoped");
                if (!Disposer.tryRegister(parent, scope)) {
                    return;
                }
                current[0] = scope;
                consumer.accept(scope, newValue);
            }
        };
        Runnable unsubscribe = value.addListener(handler);
        Disposer.register(parent, unsubscribe::run);
        handler.accept(value.get());
    }

    private void setTitleFromFirstCommitOrBranch(BranchesStateWithProgress state,
                                                 CompletableFuture<List<VcsCommitMetadata>> commitsRequest,
                                                 GitBranch headBranch,
                                                 List<VcsCommitMetadata> commits) {
        String title = generateTitle(commits, headBranch);
        synchronized (myTitleLock) {
            if (myAutoTitleCancelled || Disposer.isDisposed(myCs) || !isCurrent(state, commitsRequest)) {
                return;
            }
            myTitleText.set(title);
        }
    }

    @Override
    public void setTitle(String text) {
        synchronized (myTitleLock) {
            if (myTitleText.get().equals(text)) {
                return;
            }
            myAutoTitleCancelled = true;
            myTitleText.set(text);
        }
    }

    @Override
    public void setDescription(String text) {
        myDescriptionText.set(text);
    }

    @Override
    public void setHead(@Nullable GHGitRepositoryMapping repo, @Nullable GitBranch branch) {
        updateBranches(branches -> {
            if (!Objects.equals(branches.headRepo(), repo) || !Objects.equals(branches.headBranch(), branch)) {
                return branches.withHead(repo, branch);
            }
            else {
                return null;
            }
        });
    }

    @Override
    public void setBaseBranch(@Nullable GitRemoteBranch branch) {
        updateBranches(branches -> {
            if (!Objects.equals(branches.baseBranch(), branch)) {
                return branches.withBaseBranch(branch);
            }
            else {
                return null;
            }
        });
    }

    @Override
    public void create(boolean isDraft) {
        BranchesStateWithProgress branchesWithProgress = myBranchesWithProgress;
        CompletableFuture<List<VcsCommitMetadata>> commitsRequest = branchesWithProgress.myCommitsRequest.get();
        if (commitsRequest == null || !commitsRequest.isDone()) {
            return;
        }
        CompletableFuture<@Nullable GHPRIdentifier> existingPrRequest = branchesWithProgress.myExistingPrRequest;
        if (existingPrRequest == null || !existingPrRequest.isDone()) {
            return;
        }

        BranchesState branches = branchesWithProgress.myBranches;

        GitRemoteBranch baseBranch = branches.baseBranch();
        if (baseBranch == null) {
            return;
        }
        GHGitRepositoryMapping headRepo = branches.headRepo();
        if (headRepo == null) {
            return;
        }
        GitBranch headBranch = branches.headBranch();
        if (headBranch == null) {
            return;
        }
        String title = myTitleText.get();
        String description = myDescriptionText.get();

        List<GHPullRequestRequestedReviewer> reviewers = myReviewersVm.getItems().get();
        List<GHUser> assignees = myAssigneesVm.getItems().get();
        List<GHLabel> labels = myLabelsVm.getItems().get();

        CreationState prevState = myCreationProgress.getAndUpdate(it -> {
            if (it == null || it instanceof CreationState.Error) {
                return CreationState.CollectingData.INSTANCE;
            }
            else {
                return it;
            }
        });
        if (prevState != null && !(prevState instanceof CreationState.Error)) {
            return;
        }

        CompletableFuture<GitRemoteBranch> remoteHeadBranchFuture;
        if (headBranch instanceof GitRemoteBranch remoteBranch) {
            remoteHeadBranchFuture = CompletableFuture.completedFuture(remoteBranch);
        }
        else if (headBranch instanceof GitLocalBranch localBranch) {
            GitPushTarget existingPushTarget =
                GitPushUtil.findPushTarget(headRepo.getGitRepository(), headRepo.getGitRemote(), localBranch);
            CompletableFuture<GitPushTarget> pushTargetFuture = existingPushTarget != null
                ? CompletableFuture.completedFuture(existingPushTarget)
                : createPushTarget(headRepo.getGitRemote(), localBranch);
            remoteHeadBranchFuture = pushTargetFuture.thenCompose(pushTarget -> {
                GitRepository gitRepository = headRepo.getGitRepository();
                Hash localHash = gitRepository.getBranches().getHash(localBranch);
                Hash remoteHash = gitRepository.getBranches().getHash(pushTarget.getBranch());
                boolean needToPush = !Objects.equals(localHash, remoteHash);
                if (needToPush) {
                    myCreationProgress.set(CreationState.Pushing.INSTANCE);
                    return pushBranch(headRepo, localBranch, pushTarget).thenApply(it -> pushTarget.getBranch());
                }
                return CompletableFuture.completedFuture(pushTarget.getBranch());
            });
        }
        else {
            remoteHeadBranchFuture = CompletableFuture.failedFuture(new IllegalStateException("Unknown branch type"));
        }

        CompletableFuture<GHPullRequestShort> creation = remoteHeadBranchFuture.thenCompose(remoteHeadBranch -> {
            myCreationProgress.set(CreationState.CallingAPI.INSTANCE);
            return myDataContext.getCreationService().createPullRequest(baseBranch, headRepo, remoteHeadBranch,
                title, description, isDraft);
        }).thenCompose(pullRequest -> {
            if (reviewers.isEmpty() && assignees.isEmpty() && labels.isEmpty()) {
                return CompletableFuture.completedFuture(pullRequest);
            }
            myCreationProgress.set(CreationState.SettingMetadata.INSTANCE);
            GHPRDetailsService detailsService = myDataContext.getDetailsService();
            GHPRIdentifier prId = pullRequest.getPrId();
            return detailsService.adjustReviewers(prId, new CollectionDelta<>(List.of(), reviewers))
                .thenCompose(it -> detailsService.adjustAssignees(prId, new CollectionDelta<>(List.of(), assignees)))
                .thenCompose(it -> detailsService.adjustLabels(prId, new CollectionDelta<>(List.of(), labels)))
                .thenApply(it -> pullRequest);
        });
        myCreation = creation;

        creation.whenCompleteAsync((pullRequest, error) -> {
            if (error != null) {
                if (FutureUtil.isCancellation(error)) {
                    myCreationProgress.set(null);
                }
                else {
                    myCreationProgress.set(new CreationState.Error(FutureUtil.unwrap(error)));
                }
                return;
            }
            if (Disposer.isDisposed(myCs)) {
                return;
            }

            myCreationProgress.set(CreationState.Created.INSTANCE);

            myViewPullRequest.accept(pullRequest.getPrId());
            mySettings.setRecentNewPullRequestHead(headRepo.getRepository());
            myRefreshPrOnCurrentBranch.run();
            myCloseNewPullRequest.run();
        }, myProject.getUIAccess());
    }

    private CompletableFuture<GitPushTarget> createPushTarget(GitRemote remote, GitLocalBranch localBranch) {
        Function<String, CompletableFuture<String>> callback = myRemoteBranchNameCallback;
        if (callback == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Missing branch name supplier"));
        }
        return callback.apply(localBranch.getName()).thenApply(name -> {
            String branchName = GitRefNameValidator.getInstance().cleanUpBranchName(name);
            GitRemoteBranch remoteBranch = new GitStandardRemoteBranch(remote, branchName);
            return new GitPushTarget(remoteBranch, true);
        });
    }

    private CompletableFuture<?> pushBranch(GHGitRepositoryMapping headRepo, GitLocalBranch localBranch, GitPushTarget pushTarget) {
        GitRepository gitRepository = headRepo.getGitRepository();
        return CompletableFuture.supplyAsync(() -> {
            GitPushSupport pushSupport = (GitPushSupport) DvcsUtil.getPushSupport(GitVcs.getInstance(myProject));
            if (pushSupport == null) {
                throw new IllegalStateException("Missing push support");
            }
            PushSpec<GitPushSource, GitPushTarget> pushSpec = new PushSpec<>(GitPushSource.create(localBranch), pushTarget);
            Supplier<@Nullable GitPushRepoResult> push = () -> {
                GitPushOperation pushOperation =
                    new GitPushOperation(myProject, pushSupport, Map.of(gitRepository, pushSpec), null, false, false);
                return pushOperation.execute().getResults().get(gitRepository);
            };
            GitPushRepoResult pushResult = ProgressManager.getInstance().runProcess(push, new EmptyProgressIndicator());
            if (pushResult == null) {
                throw new IllegalStateException("Missing push result");
            }
            if (pushResult.getError() != null) {
                throw new IllegalStateException(GitLocalize.pushFailedErrorMessage(StringUtil.notNullize(pushResult.getError())).get());
            }
            return pushResult;
        }, AppExecutorUtil.getAppExecutorService());
    }

    private boolean initBranchesCheck(BranchesStateWithProgress branchesWithProgress) {
        BranchesState branches = branchesWithProgress.myBranches;
        GitRemoteBranch baseBranch = branches.baseBranch();
        GHGitRepositoryMapping headRepo = branches.headRepo();
        GitBranch headBranch = branches.headBranch();
        if (baseBranch == null || headRepo == null || headBranch == null) {
            myBranchesCheckState.set(null);
            return false;
        }

        GitRemoteBranch trackedHead = getRemoteBranch(headRepo, headBranch);
        if (baseBranch.equals(trackedHead)) {
            myBranchesCheckState.set(ComputedResult.success(new BranchesCheckResult.NoChanges(baseBranch, headBranch)));
            return false;
        }

        if (branchesWithProgress.myExistingPrRequest == null) {
            myBranchesCheckState.set(null);
            return false;
        }

        myBranchesCheckState.set(null);
        return true;
    }

    private void updateBranchesCheck(BranchesStateWithProgress state,
                                     GitRemoteBranch baseBranch,
                                     GitBranch headBranch,
                                     @Nullable CompletableFuture<List<VcsCommitMetadata>> commitsRequest) {
        CompletableFuture<@Nullable GHPRIdentifier> existingPrRequest = state.myExistingPrRequest;
        if (commitsRequest == null || existingPrRequest == null) {
            myBranchesCheckState.set(null);
            return;
        }
        myBranchesCheckState.set(ComputedResult.loading());
        checkBranches(baseBranch, headBranch, commitsRequest, existingPrRequest).whenComplete((result, error) -> {
            synchronized (myLock) {
                if (!isCurrent(state, commitsRequest)) {
                    return;
                }
                if (error == null) {
                    myBranchesCheckState.set(ComputedResult.success(result));
                }
                else if (!FutureUtil.isCancellation(error)) {
                    myBranchesCheckState.set(ComputedResult.failure(FutureUtil.unwrap(error)));
                }
            }
        });
    }

    private CompletableFuture<BranchesCheckResult> checkBranches(GitRemoteBranch baseBranch,
                                                                 GitBranch headBranch,
                                                                 CompletableFuture<List<VcsCommitMetadata>> commitsRequest,
                                                                 CompletableFuture<@Nullable GHPRIdentifier> existingPrRequest) {
        return commitsRequest.thenCompose(commits -> {
            if (commits.isEmpty()) {
                return CompletableFuture.completedFuture(new BranchesCheckResult.NoChanges(baseBranch, headBranch));
            }
            return existingPrRequest.thenApply(existingPr -> {
                if (existingPr != null) {
                    return new BranchesCheckResult.AlreadyExists(() -> myViewPullRequest.accept(existingPr));
                }
                return BranchesCheckResult.OK.INSTANCE;
            });
        });
    }

    private static @Nullable GitRemoteBranch getRemoteBranch(GHGitRepositoryMapping headRepo, GitBranch headBranch) {
        if (headBranch instanceof GitRemoteBranch remoteBranch) {
            return remoteBranch;
        }
        if (headBranch instanceof GitLocalBranch) {
            GitBranchTrackInfo trackInfo = headRepo.getGitRepository().getBranchTrackInfo(headBranch.getName());
            return trackInfo == null ? null : trackInfo.getRemoteBranch();
        }
        return null;
    }

    @Override
    public void dispose() {
        Disposer.dispose(myCs);
    }

    private final class BranchesStateWithProgress {
        private final BranchesState myBranches;
        private final Disposable myLoadingCs = Disposable.newDisposable("BranchStatesLoader");

        private final MutableObservableValue<@Nullable CompletableFuture<List<VcsCommitMetadata>>> myCommitsRequest =
            MutableObservableValue.of(null);
        private final @Nullable CompletableFuture<@Nullable GHPRIdentifier> myExistingPrRequest;

        private boolean myCheckWithCommits;

        private BranchesStateWithProgress(BranchesState branches) {
            myBranches = branches;
            if (!Disposer.tryRegister(myCs, myLoadingCs)) {
                Disposer.dispose(myLoadingCs);
            }
            Disposer.register(myLoadingCs, () -> {
                CompletableFuture<List<VcsCommitMetadata>> request = myCommitsRequest.get();
                if (request != null) {
                    request.cancel(false);
                }
            });
            myExistingPrRequest = findExistingPrAsync();
            if (myExistingPrRequest != null) {
                CompletableFuture<@Nullable GHPRIdentifier> existingPrRequest = myExistingPrRequest;
                Disposer.register(myLoadingCs, () -> existingPrRequest.cancel(false));
            }
        }

        private void start() {
            if (Disposer.isDisposed(myLoadingCs)) {
                return;
            }
            Runnable unsubscribe = myCommitsRequest.addListener(request -> onCommitsRequest(this, request));
            Disposer.register(myLoadingCs, unsubscribe::run);
            initCommitsLoader();
        }

        private void initCommitsLoader() {
            GHGitRepositoryMapping headRepo = myBranches.headRepo();
            if (headRepo == null) {
                return;
            }
            GitRepository repository = headRepo.getGitRepository();
            GitRemoteBranch baseBranch = myBranches.baseBranch();
            if (baseBranch == null) {
                return;
            }
            GitBranch headBranch = myBranches.headBranch();
            if (headBranch == null) {
                return;
            }

            ObservableValue<GitRepoInfo> infoState = GitAsyncExtensions.infoStateIn(repository, myLoadingCs);
            Object[] lastKey = new Object[1];
            Consumer<GitRepoInfo> handler = info -> {
                List<@Nullable Hash> key = Arrays.asList(info.getRemoteBranchesWithHashes().get(baseBranch), getHeadHash(info, headBranch));
                synchronized (lastKey) {
                    if (key.equals(lastKey[0])) {
                        return;
                    }
                    lastKey[0] = key;
                }
                if (Disposer.isDisposed(myLoadingCs)) {
                    return;
                }
                CompletableFuture<List<VcsCommitMetadata>> request = CompletableFuture.supplyAsync(() -> {
                    try {
                        return GitLogUtil.collectMetadata(repository.getProject(), repository.getRoot(),
                            baseBranch.getName() + ".." + headBranch.getName()).getCommits();
                    }
                    catch (VcsException e) {
                        throw new CompletionException(e);
                    }
                }, AppExecutorUtil.getAppExecutorService());
                CompletableFuture<List<VcsCommitMetadata>> previous = myCommitsRequest.getAndUpdate(it -> request);
                if (previous != null) {
                    previous.cancel(false);
                }
            };
            Runnable unsubscribe = infoState.addListener(handler);
            Disposer.register(myLoadingCs, unsubscribe::run);
            handler.accept(infoState.get());
        }

        private static @Nullable Hash getHeadHash(GitRepoInfo info, GitBranch headBranch) {
            if (headBranch instanceof GitRemoteBranch) {
                return info.getRemoteBranchesWithHashes().get(headBranch);
            }
            if (headBranch instanceof GitLocalBranch) {
                return info.getLocalBranchesWithHashes().get(headBranch);
            }
            return null;
        }

        private @Nullable CompletableFuture<@Nullable GHPRIdentifier> findExistingPrAsync() {
            GHGitRepositoryMapping headRepo = myBranches.headRepo();
            if (headRepo == null) {
                return null;
            }
            GitRemoteBranch baseBranch = myBranches.baseBranch();
            if (baseBranch == null) {
                return null;
            }
            GitBranch headBranch = myBranches.headBranch();
            if (headBranch == null) {
                return null;
            }
            GitRemoteBranch remoteHeadBranch = getRemoteBranch(headRepo, headBranch);
            if (remoteHeadBranch == null) {
                return CompletableFuture.completedFuture(null);
            }
            return myDataContext.getCreationService().findOpenPullRequest(baseBranch, headRepo.getRepository().getRepositoryPath(),
                remoteHeadBranch);
        }

        private void cancel() {
            Disposer.dispose(myLoadingCs);
        }
    }

    private BranchesState getDefaultBranches() {
        GHGitRepositoryMapping baseRepo = myRepoData.getRepositoryMapping();
        GitRepository baseGitRepo = baseRepo.getGitRepository();
        GitRemoteBranch defaultBranch = myRepoData.getDefaultRemoteBranch();
        GitLocalBranch currentBranch = baseGitRepo.getCurrentBranch();
        GitBranchTrackInfo currentBranchTrackInfo = currentBranch == null ? null : baseGitRepo.getBranchTrackInfo(currentBranch.getName());
        GHGitRepositoryMapping headRepo = null;
        if (currentBranchTrackInfo != null) {
            GitRemote remote = currentBranchTrackInfo.getRemote();
            for (GHGitRepositoryMapping it : getRepositories()) {
                if (it.getGitRepository().getRoot().equals(baseGitRepo.getRoot()) && it.getRemote().remote().equals(remote)) {
                    headRepo = it;
                    break;
                }
            }
        }
        return new BranchesState(baseRepo, defaultBranch, headRepo != null ? headRepo : baseRepo, currentBranch);
    }

    private static String generateTitle(List<VcsCommitMetadata> commits, GitBranch headBranch) {
        return commits.size() == 1 ? commits.get(0).getSubject() : headBranch.getName();
    }
}
