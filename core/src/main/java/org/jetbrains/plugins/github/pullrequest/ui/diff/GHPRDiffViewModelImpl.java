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
package org.jetbrains.plugins.github.pullrequest.ui.diff;

import consulo.application.util.ListSelection;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.async.MappingScopedItemsContainer;
import consulo.review.diff.DiscussionsViewOption;
import consulo.review.icon.IconsProvider;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.diff.DiffLineLocation;
import consulo.versionControlSystem.review.diff.UnifiedCodeReviewItemPosition;
import consulo.versionControlSystem.review.diff.model.CodeReviewDiffProcessorState;
import consulo.versionControlSystem.review.diff.model.DiffViewerScrollRequest;
import consulo.versionControlSystem.review.diff.model.PreLoadingCodeReviewAsyncDiffViewModelDelegate;
import consulo.versionControlSystem.review.util.ChangesSelection;
import consulo.versionControlSystem.review.util.PreciseChangesSelection;
import git4idea.changes.GitBranchComparisonResult;
import git4idea.changes.GitTextFilePatchWithHistory;
import org.jetbrains.plugins.github.api.data.GHCommitHash;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRChangesDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModels;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.review.DelegatingGHPRReviewViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class GHPRDiffViewModelImpl implements GHPRDiffViewModel {
    private final Project myProject;
    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;
    private final GHPRThreadsViewModels myThreadsVm;
    private final GHPRReviewDataProvider myReviewDataProvider;

    private final IconsProvider<String> myIconProvider;
    private final GHUser myCurrentUser;

    private final DelegatingGHPRReviewViewModel myReviewVm;

    private final GithubPullRequestsProjectUISettings mySettings;

    private final ComputationState<GitBranchComparisonResult> myChangesFetchFlow;

    private final PreLoadingCodeReviewAsyncDiffViewModelDelegate<RefComparisonChange, GHPRDiffChangeViewModel> myDelegate;

    private final Map<RefComparisonChange, ObservableValue<@Nullable GHPRDiffReviewViewModel>> myChangeVmsMap = new HashMap<>();

    private final ComputationState<List<GHPullRequestReviewThread>> myThreads;

    private final MutableObservableValue<DiscussionsViewOption> myDiscussionsViewOption;

    private final MutableObservableValue<Boolean> myIsLoadingReviewData;

    private final MutableObservableValue<Map<String, GHPRReviewThreadDiffViewModel.MappingData>> myThreadMappings =
        MutableObservableValue.of(Map.of());

    private final ObservableValue<List<MappedGHPRReviewThreadDiffViewModel>> myMappedThreads;

    public GHPRDiffViewModelImpl(Project project,
                                 Disposable parentCs,
                                 GHPRDataContext dataContext,
                                 GHPRDataProvider dataProvider,
                                 GHPRReviewViewModelHelper reviewVmHelper,
                                 GHPRThreadsViewModels threadsVm) {
        myProject = project;
        myDataContext = dataContext;
        myDataProvider = dataProvider;
        myThreadsVm = threadsVm;
        myCs = Disposable.newDisposable("GitHub Pull Request Diff View Model");
        Disposer.register(parentCs, myCs);
        myReviewDataProvider = dataProvider.getReviewData();

        myIconProvider = dataContext.getAvatarIconsProvider();
        myCurrentUser = dataContext.getSecurityService().getCurrentUser();

        myReviewVm = new DelegatingGHPRReviewViewModel(reviewVmHelper);

        mySettings = GithubPullRequestsProjectUISettings.getInstance(project);

        GHPRChangesDataProvider changesData = dataProvider.getChangesData();
        myChangesFetchFlow = ComputationState.create(myCs,
            changesData::addChangesNeedReloadListener,
            () -> changesData.loadChanges().thenCompose(changes -> changesData.ensureAllRevisionsFetched().thenApply(it -> changes)));

        myDelegate = PreLoadingCodeReviewAsyncDiffViewModelDelegate.create(myCs,
            myChangesFetchFlow,
            (scope, allChanges, change) -> new GHPRDiffChangeViewModelImpl(project, scope, allChanges, change));

        myThreads = myReviewDataProvider.threadsComputationFlow(myCs);

        myDiscussionsViewOption = MutableObservableValue.of(mySettings.getEditorReviewViewOption());

        myIsLoadingReviewData = MutableObservableValue.of(calcIsLoadingReviewData());
        Runnable updateLoading = () -> myIsLoadingReviewData.set(calcIsLoadingReviewData());
        Disposer.register(myCs, myReviewVm.getPendingReview().addListener(it -> updateLoading.run())::run);
        Disposer.register(myCs, myThreads.addListener(it -> updateLoading.run())::run);

        Runnable updateMappings = () -> myThreadMappings.set(calcThreadMappings());
        Disposer.register(myCs, myThreads.addListener(it -> updateMappings.run())::run);
        Disposer.register(myCs, myDiscussionsViewOption.addListener(it -> updateMappings.run())::run);
        Disposer.register(myCs, myDelegate.getChanges().addListener(it -> updateMappings.run())::run);
        updateMappings.run();

        myMappedThreads = MappingScopedItemsContainer.mapStatefulToStateful(myCs,
            threadsVm.getCompactThreads(),
            (scope, sharedVm) -> new MappedGHPRReviewThreadDiffViewModel(scope, sharedVm, myThreadMappings));
    }

    private boolean calcIsLoadingReviewData() {
        ComputedResult<?> threadsResult = myThreads.get();
        return myReviewVm.getPendingReview().get().isInProgress() || threadsResult == null || threadsResult.isInProgress();
    }

    private Map<String, GHPRReviewThreadDiffViewModel.MappingData> calcThreadMappings() {
        ComputedResult<CodeReviewDiffProcessorState<GHPRDiffChangeViewModel>> changesResult = myDelegate.getChanges().get();
        CodeReviewDiffProcessorState<GHPRDiffChangeViewModel> changesState = changesResult == null ? null : changesResult.getOrNull();
        if (changesState == null) {
            return Map.of();
        }
        List<GHPRDiffChangeViewModel> changeVms = changesState.getSelectedChanges().getList();
        ComputedResult<List<GHPullRequestReviewThread>> threadDataResult = myThreads.get();
        List<GHPullRequestReviewThread> threadData = threadDataResult == null ? null : threadDataResult.getOrNull();
        if (threadData == null) {
            return Map.of();
        }
        return mapThreadsToChanges(threadData, myDiscussionsViewOption.get(), changeVms);
    }

    @Override
    public GHPRReviewViewModel getReviewVm() {
        return myReviewVm;
    }

    @Override
    public ObservableValue<Boolean> isLoadingReviewData() {
        return myIsLoadingReviewData;
    }

    @Override
    public IconsProvider<String> getIconProvider() {
        return myIconProvider;
    }

    @Override
    public GHUser getCurrentUser() {
        return myCurrentUser;
    }

    @Override
    public ObservableValue<@Nullable ComputedResult<CodeReviewDiffProcessorState<GHPRDiffChangeViewModel>>> getChanges() {
        return myDelegate.getChanges();
    }

    @Override
    public ObservableValue<DiscussionsViewOption> getDiscussionsViewOption() {
        return myDiscussionsViewOption;
    }

    @Override
    public void reloadReview() {
        myReviewDataProvider.signalPendingReviewNeedsReload();
        myReviewDataProvider.signalThreadsNeedReload();
    }

    @Override
    public synchronized ObservableValue<@Nullable GHPRDiffReviewViewModel> getViewModelFor(RefComparisonChange change) {
        return myChangeVmsMap.computeIfAbsent(change, this::createViewModelFor);
    }

    private ObservableValue<@Nullable GHPRDiffReviewViewModel> createViewModelFor(RefComparisonChange change) {
        MutableObservableValue<@Nullable GHPRDiffReviewViewModel> result = MutableObservableValue.of(null);
        Disposable[] scope = new Disposable[1];
        Consumer<@Nullable ComputedResult<GitBranchComparisonResult>> handler = changesResult -> {
            GitBranchComparisonResult allChanges = changesResult == null ? null : changesResult.getOrNull();
            if (allChanges == null) {
                return;
            }
            GitTextFilePatchWithHistory diffData = allChanges.getPatchesByChange().get(change);
            synchronized (scope) {
                if (scope[0] != null) {
                    Disposer.dispose(scope[0]);
                    scope[0] = null;
                }
                if (diffData == null) {
                    result.set(null);
                    return;
                }
                Disposable vmScope = Disposable.newDisposable("GHPRDiffReviewViewModel");
                if (!Disposer.tryRegister(myCs, vmScope)) {
                    return;
                }
                scope[0] = vmScope;
                result.set(createFileReviewVm(vmScope, change, diffData));
            }
        };
        Disposer.register(myCs, myChangesFetchFlow.addListener(handler)::run);
        handler.accept(myChangesFetchFlow.get());
        return result;
    }

    @Override
    public void setDiscussionsViewOption(DiscussionsViewOption viewOption) {
        myDiscussionsViewOption.set(viewOption);
        mySettings.setEditorReviewViewOption(viewOption);
    }

    private GHPRDiffReviewViewModelImpl createFileReviewVm(Disposable cs, RefComparisonChange change, GitTextFilePatchWithHistory diffData) {
        return new GHPRDiffReviewViewModelImpl(myProject, cs, myDataContext, myDataProvider, change, diffData, myThreadsVm, myMappedThreads);
    }

    public void handleSelection(Disposable parentDisposable, Consumer<ListSelection<RefComparisonChange>> listener) {
        myDelegate.handleSelection(parentDisposable, listener);
    }

    @Override
    public void showDiffFor(ChangesSelection changes) {
        DiffLineLocation scrollLocation = changes instanceof PreciseChangesSelection precise ? precise.getLocation() : null;
        myDelegate.showChanges(ListSelection.createAt(changes.getChanges(), changes.getSelectedIdx()),
            scrollLocation == null ? null : DiffViewerScrollRequest.toLine(scrollLocation));
    }

    private void showChange(RefComparisonChange change, @Nullable DiffViewerScrollRequest scrollRequest) {
        myDelegate.showChange(change, scrollRequest);
    }

    @Override
    public void showChange(GHPRDiffChangeViewModel change, @Nullable DiffViewerScrollRequest scrollRequest) {
        showChange(change.getChange(), scrollRequest);
    }

    @Override
    public void showChange(int changeIdx, @Nullable DiffViewerScrollRequest scrollRequest) {
        ComputedResult<CodeReviewDiffProcessorState<GHPRDiffChangeViewModel>> result = getChanges().get();
        CodeReviewDiffProcessorState<GHPRDiffChangeViewModel> state = result == null ? null : result.getOrNull();
        if (state == null) {
            return;
        }
        List<GHPRDiffChangeViewModel> list = state.getSelectedChanges().getList();
        if (changeIdx < 0 || changeIdx >= list.size()) {
            return;
        }
        showChange(list.get(changeIdx).getChange(), scrollRequest);
    }

    @Override
    public @Nullable String nextComment(String focused) {
        return myThreadsVm.lookupNextComment(focused, this::threadIsVisible);
    }

    @Override
    public @Nullable String nextComment(UnifiedCodeReviewItemPosition cursorLocation) {
        // TODO: Find a good way to map cursorLocations here (only broken for per-commit nav)
        return myThreadsVm.lookupNextComment(cursorLocation, this::threadIsVisible);
    }

    @Override
    public @Nullable String previousComment(String focused) {
        return myThreadsVm.lookupPreviousComment(focused, this::threadIsVisible);
    }

    @Override
    public @Nullable String previousComment(UnifiedCodeReviewItemPosition cursorLocation) {
        // TODO: Find a good way to map cursorLocations here (only broken for per-commit nav)
        return myThreadsVm.lookupPreviousComment(cursorLocation, this::threadIsVisible);
    }

    @Override
    public void showDiffAtComment(String commentId) {
        GHPRReviewThreadDiffViewModel.MappingData mapping = myThreadMappings.get().get(commentId);
        if (mapping != null && mapping.change() != null) {
            GHPRReviewCommentLocation location = mapping.location();
            showChange(mapping.change(), location == null ? null : DiffViewerScrollRequest.toLine(location.getLineLocation()));
            for (MappedGHPRReviewThreadDiffViewModel thread : myMappedThreads.get()) {
                if (thread.getId().equals(commentId)) {
                    thread.requestFocus();
                    return;
                }
            }
        }
        GHPRReviewNewCommentEditorViewModel newComment = null;
        for (GHPRReviewNewCommentEditorViewModel comment : myThreadsVm.getNewComments().get()) {
            if (comment.getTrackingId().equals(commentId)) {
                newComment = comment;
                break;
            }
        }
        if (newComment == null) {
            return;
        }
        RefComparisonChange newCommentChange = newComment.getPosition().get().change();
        ObservableValue<@Nullable GHPRDiffReviewViewModel> changeVm;
        synchronized (this) {
            changeVm = myChangeVmsMap.get(newCommentChange);
        }
        GHPRDiffReviewViewModel reviewVm = changeVm == null ? null : changeVm.get();
        if (reviewVm == null) {
            return;
        }
        for (GHPRNewCommentDiffViewModel it : reviewVm.getNewComments().get()) {
            if (it.getTrackingId().equals(commentId)) {
                showChange(newCommentChange, DiffViewerScrollRequest.toLine(it.getLocation().get().getLineLocation()));
                it.requestFocus();
                return;
            }
        }
    }

    private boolean threadIsVisible(String threadId) {
        GHPRReviewThreadDiffViewModel.MappingData mapping = myThreadMappings.get().get(threadId);
        if (mapping != null) {
            return mapping.isVisible() && mapping.location() != null && mapping.change() != null;
        }
        Collection<GHPRReviewNewCommentEditorViewModel> newComments = myThreadsVm.getNewComments().get();
        for (GHPRReviewNewCommentEditorViewModel comment : newComments) {
            if (comment.getTrackingId().equals(threadId)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, GHPRReviewThreadDiffViewModel.MappingData> mapThreadsToChanges(List<GHPullRequestReviewThread> threadsData,
                                                                                             DiscussionsViewOption viewOption,
                                                                                             List<GHPRDiffChangeViewModel> changeVms) {
        Map<String, GHPRReviewThreadDiffViewModel.MappingData> result = new LinkedHashMap<>();
        for (GHPullRequestReviewThread threadData : threadsData) {
            GHPRDiffChangeViewModel changeVm = findChangeVmForThread(changeVms, threadData);
            if (changeVm == null) {
                continue;
            }
            result.put(threadData.getId(), mapThreadToChange(threadData, viewOption, changeVm));
        }
        return result;
    }

    private static @Nullable GHPRDiffChangeViewModel findChangeVmForThread(List<GHPRDiffChangeViewModel> changeVms,
                                                                           GHPullRequestReviewThread threadData) {
        String filePath = threadData.getPath();
        GHCommitHash commit = threadData.getCommit();
        if (commit == null) {
            return null;
        }
        String commitSha = commit.getOid();
        for (GHPRDiffChangeViewModel changeVm : changeVms) {
            GitTextFilePatchWithHistory diffData = changeVm.getDiffData();
            if (diffData != null && diffData.contains(commitSha, filePath)) {
                return changeVm;
            }
        }
        return null;
    }

    private static GHPRReviewThreadDiffViewModel.MappingData mapThreadToChange(GHPullRequestReviewThread threadData,
                                                                               DiscussionsViewOption viewOption,
                                                                               GHPRDiffChangeViewModel changeVm) {
        RefComparisonChange change = changeVm.getChange();
        boolean isVisible = GHPullRequestReviewThread.isVisible(threadData, viewOption);
        GitTextFilePatchWithHistory diffData = changeVm.getDiffData();
        if (diffData == null) {
            return new GHPRReviewThreadDiffViewModel.MappingData(isVisible, change, null);
        }
        Pair<DiffLineLocation, DiffLineLocation> sideToRange = GHPullRequestReviewThread.mapToRange(threadData, diffData);
        if (sideToRange == null) {
            return new GHPRReviewThreadDiffViewModel.MappingData(isVisible, change, null);
        }
        DiffLineLocation startLineLocation = sideToRange.getFirst();
        DiffLineLocation endLineLocation = sideToRange.getSecond();
        GHPRReviewCommentLocation location;
        if (startLineLocation.line() == endLineLocation.line()) {
            location = new GHPRReviewCommentLocation.SingleLine(endLineLocation.side(), startLineLocation.line());
        }
        else {
            location = new GHPRReviewCommentLocation.MultiLine(startLineLocation.side(), startLineLocation.line(),
                endLineLocation.side(), endLineLocation.line());
        }
        return new GHPRReviewThreadDiffViewModel.MappingData(isVisible, change, location);
    }
}
