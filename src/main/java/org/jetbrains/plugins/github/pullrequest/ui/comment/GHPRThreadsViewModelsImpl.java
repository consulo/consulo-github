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
package org.jetbrains.plugins.github.pullrequest.ui.comment;

import consulo.diff.util.Side;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.async.MappingScopedItemsContainer;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.diff.UnifiedCodeReviewItemPosition;
import git4idea.changes.GitBranchComparisonResult;
import git4idea.changes.GitTextFilePatchWithHistory;
import org.jetbrains.plugins.github.api.data.GHCommitHash;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRChangesDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModelImpl;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

public class GHPRThreadsViewModelsImpl implements GHPRThreadsViewModels {
    private final Project myProject;
    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;
    private final GHViewModelWithTextCompletion myViewModelWithTextCompletion;

    private final boolean myCanComment;

    private final MutableObservableValue<Collection<GHPRReviewNewCommentEditorViewModel>> myNewComments =
        MutableObservableValue.of(List.of());
    private @Nullable Disposable myNewCommentPositionsDisposable;

    private final ComputationState<GitBranchComparisonResult> myChangesFetchFlow;
    private final ComputationState<List<GHPullRequestReviewThread>> myThreadsComputationFlow;

    private final MutableObservableValue<@Nullable ComputedResult<TreeSet<ThreadIdAndPosition>>> myThreadOrder =
        MutableObservableValue.of(ComputedResult.loading());
    private final MutableObservableValue<@Nullable ComputedResult<Map<String, ThreadIdAndPosition>>> myThreadPositionsById =
        MutableObservableValue.of(ComputedResult.loading());

    private final MutableObservableValue<List<GHPullRequestReviewThread>> myLoadedThreads = MutableObservableValue.of(List.of());
    private final ObservableValue<List<UpdateableGHPRCompactReviewThreadViewModel>> myUnorderedCompactThreads;
    private final MutableObservableValue<Collection<GHPRCompactReviewThreadViewModel>> myCompactThreads =
        MutableObservableValue.of(List.of());

    private final MutableObservableValue<Map<String, ThreadMappingData>> myThreadMappingData = MutableObservableValue.of(Map.of());

    public GHPRThreadsViewModelsImpl(Project project,
                                     Disposable parentCs,
                                     GHPRDataContext dataContext,
                                     GHPRDataProvider dataProvider,
                                     GHViewModelWithTextCompletion completionVm) {
        myProject = project;
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myDataContext = dataContext;
        myDataProvider = dataProvider;
        myViewModelWithTextCompletion = completionVm;
        myCanComment = dataProvider.getReviewData().canComment();

        GHPRChangesDataProvider changesData = dataProvider.getChangesData();
        myChangesFetchFlow = ComputationState.create(myCs,
            changesData::addChangesNeedReloadListener,
            () -> changesData.loadChanges().thenCompose(changes -> changesData.ensureAllRevisionsFetched().thenApply(it -> changes)));
        myThreadsComputationFlow = dataProvider.getReviewData().threadsComputationFlow(myCs);

        myUnorderedCompactThreads = MappingScopedItemsContainer.mapDataToModel(myCs,
            myLoadedThreads,
            GHPullRequestReviewThread::getId,
            this::createThread,
            UpdateableGHPRCompactReviewThreadViewModel::update);

        Runnable changesListener = myChangesFetchFlow.addListener(it -> {
            updateThreadOrder();
            updateThreadMappingData();
        });
        Disposer.register(myCs, changesListener::run);
        Runnable threadsListener = myThreadsComputationFlow.addListener(it -> {
            updateLoadedThreads();
            updateThreadOrder();
            updateThreadMappingData();
        });
        Disposer.register(myCs, threadsListener::run);
        Runnable newCommentsListener = myNewComments.addListener(it -> {
            listenNewCommentPositions(it);
            updateThreadOrder();
        });
        Disposer.register(myCs, newCommentsListener::run);
        Runnable unorderedListener = myUnorderedCompactThreads.addListener(it -> updateCompactThreads());
        Disposer.register(myCs, unorderedListener::run);
        Disposer.register(myCs, () -> {
            Disposable positionsDisposable = myNewCommentPositionsDisposable;
            if (positionsDisposable != null) {
                Disposer.dispose(positionsDisposable);
            }
        });

        updateLoadedThreads();
        updateThreadOrder();
        updateThreadMappingData();
    }

    private UpdateableGHPRCompactReviewThreadViewModel createThread(Disposable cs, GHPullRequestReviewThread initialData) {
        return new UpdateableGHPRCompactReviewThreadViewModel(myProject, cs, myDataContext, myDataProvider, initialData,
            myViewModelWithTextCompletion);
    }

    private synchronized void listenNewCommentPositions(Collection<GHPRReviewNewCommentEditorViewModel> comments) {
        Disposable previous = myNewCommentPositionsDisposable;
        if (previous != null) {
            Disposer.dispose(previous);
        }
        Disposable positionsDisposable = Disposable.newDisposable("GitHub new comment positions");
        if (!Disposer.tryRegister(myCs, positionsDisposable)) {
            return;
        }
        myNewCommentPositionsDisposable = positionsDisposable;
        for (GHPRReviewNewCommentEditorViewModel comment : comments) {
            Runnable positionListener = comment.getPosition().addListener(it -> updateThreadOrder());
            Disposer.register(positionsDisposable, positionListener::run);
        }
    }

    private void updateLoadedThreads() {
        ComputedResult<List<GHPullRequestReviewThread>> result = myThreadsComputationFlow.get();
        if (result == null || result.isInProgress()) {
            return;
        }
        List<GHPullRequestReviewThread> threads = result.getOrNull();
        myLoadedThreads.set(threads != null ? threads : List.of());
    }

    private synchronized void updateThreadOrder() {
        ComputedResult<GitBranchComparisonResult> allChangesResult = myChangesFetchFlow.get();
        ComputedResult<List<GHPullRequestReviewThread>> allThreadsResult = myThreadsComputationFlow.get();
        GitBranchComparisonResult allChanges = allChangesResult == null ? null : allChangesResult.getOrNull();
        List<GHPullRequestReviewThread> allThreads = allThreadsResult == null ? null : allThreadsResult.getOrNull();
        ComputedResult<TreeSet<ThreadIdAndPosition>> order;
        if (allChanges == null || allThreads == null) {
            order = ComputedResult.loading();
        }
        else {
            List<NewCommentAndPosition> newComments = new ArrayList<>();
            for (GHPRReviewNewCommentEditorViewModel comment : myNewComments.get()) {
                newComments.add(new NewCommentAndPosition(comment, comment.getPosition().get()));
            }
            order = ComputedResult.compute(() -> createSortedThreadPositionsAndIds(allChanges, allThreads, newComments));
        }
        myThreadOrder.set(order);
        myThreadPositionsById.set(order == null ? null : order.map(threadOrder -> {
            Map<String, ThreadIdAndPosition> result = new LinkedHashMap<>();
            for (ThreadIdAndPosition pos : threadOrder) {
                String id = pos.id();
                if (id != null) {
                    result.put(id, pos);
                }
            }
            return result;
        }));
        updateCompactThreads();
    }

    private synchronized void updateCompactThreads() {
        List<UpdateableGHPRCompactReviewThreadViewModel> threads = myUnorderedCompactThreads.get();
        ComputedResult<TreeSet<ThreadIdAndPosition>> threadOrderResult = myThreadOrder.get();
        ComputedResult<Map<String, ThreadIdAndPosition>> threadPositionsByIdResult = myThreadPositionsById.get();
        TreeSet<ThreadIdAndPosition> threadOrder = threadOrderResult == null ? null : threadOrderResult.getOrNull();
        Map<String, ThreadIdAndPosition> threadPositionsById =
            threadPositionsByIdResult == null ? null : threadPositionsByIdResult.getOrNull();
        if (threadOrder == null || threadPositionsById == null) {
            myCompactThreads.set(List.copyOf(threads));
            return;
        }

        List<ThreadIdAndPosition> orderList = new ArrayList<>(threadOrder);
        List<UpdateableGHPRCompactReviewThreadViewModel> sorted = new ArrayList<>(threads);
        sorted.sort(Comparator.comparingInt(it -> {
            ThreadIdAndPosition position = threadPositionsById.get(it.getId());
            if (position == null) {
                return Integer.MAX_VALUE;
            }
            int index = orderList.indexOf(position);
            return index == -1 ? Integer.MAX_VALUE : index;
        }));
        myCompactThreads.set(List.copyOf(sorted));
    }

    private void updateThreadMappingData() {
        ComputedResult<List<GHPullRequestReviewThread>> threadsResult = myThreadsComputationFlow.get();
        if (threadsResult == null || threadsResult.isInProgress()) {
            return;
        }
        List<GHPullRequestReviewThread> threads = threadsResult.getOrNull();
        ComputedResult<GitBranchComparisonResult> changesResult = myChangesFetchFlow.get();
        GitBranchComparisonResult allChanges = changesResult == null ? null : changesResult.getOrNull();
        if (threads == null || allChanges == null) {
            myThreadMappingData.set(Map.of());
            return;
        }

        Map<String, ThreadMappingData> result = new LinkedHashMap<>();
        for (GHPullRequestReviewThread threadData : threads) {
            GHCommitHash commit = threadData.getCommit();
            if (commit == null) {
                result.put(threadData.getId(), new ThreadMappingData(threadData, null, null));
                continue;
            }
            RefComparisonChange change = allChanges.findCumulativeChange(commit.getOid(), threadData.getPath());
            if (change == null) {
                result.put(threadData.getId(), new ThreadMappingData(threadData, null, null));
                continue;
            }
            GitTextFilePatchWithHistory diffData = allChanges.getPatchesByChange().get(change);
            result.put(threadData.getId(), new ThreadMappingData(threadData, change, diffData));
        }
        myThreadMappingData.set(result);
    }

    @Override
    public boolean canComment() {
        return myCanComment;
    }

    @Override
    public ObservableValue<Collection<GHPRCompactReviewThreadViewModel>> getCompactThreads() {
        return myCompactThreads;
    }

    @Override
    public ObservableValue<Collection<GHPRReviewNewCommentEditorViewModel>> getNewComments() {
        return myNewComments;
    }

    @Override
    public ObservableValue<Map<String, ThreadMappingData>> getThreadMappingData() {
        return myThreadMappingData;
    }

    @Override
    public synchronized GHPRReviewNewCommentEditorViewModel requestNewComment(GHPRReviewCommentPosition position) {
        for (GHPRReviewNewCommentEditorViewModel vm : myNewComments.get()) {
            if (vm.getPosition().get().equals(position)) {
                return vm;
            }
        }
        GHPRReviewNewCommentEditorViewModel vm = createNewCommentVm(position);
        List<GHPRReviewNewCommentEditorViewModel> updated = new ArrayList<>(myNewComments.get());
        updated.add(vm);
        myNewComments.set(List.copyOf(updated));
        return vm;
    }

    @Override
    public synchronized void cancelNewComment(RefComparisonChange change, Side side, int lineIdx) {
        GHPRReviewNewCommentEditorViewModel oldVm = null;
        for (GHPRReviewNewCommentEditorViewModel vm : myNewComments.get()) {
            GHPRReviewCommentPosition vmPosition = vm.getPosition().get();
            if (vmPosition.location().side() == side && vmPosition.location().lineIdx() == lineIdx && vmPosition.change().equals(change)) {
                oldVm = vm;
                break;
            }
        }
        if (oldVm == null) {
            return;
        }
        List<GHPRReviewNewCommentEditorViewModel> newList = new ArrayList<>(myNewComments.get());
        newList.remove(oldVm);
        if (oldVm instanceof GHPRReviewNewCommentEditorViewModelImpl impl) {
            impl.destroy();
        }
        myNewComments.set(List.copyOf(newList));
    }

    private GHPRReviewNewCommentEditorViewModel createNewCommentVm(GHPRReviewCommentPosition position) {
        return new GHPRReviewNewCommentEditorViewModelImpl(myProject, myCs, myDataProvider,
            myDataContext.getRepositoryDataService().getRemoteCoordinates().repository(),
            myDataContext.getSecurityService().getCurrentUser(),
            myViewModelWithTextCompletion,
            myDataContext.getAvatarIconsProvider(),
            position,
            pos -> cancelNewComment(pos.change(), pos.location().side(), pos.location().lineIdx()));
    }

    @Override
    public @Nullable String lookupNextComment(UnifiedCodeReviewItemPosition cursorLocation, Predicate<String> isVisible) {
        return lookupAdjacentComment(cursorLocation, true, isVisible);
    }

    @Override
    public @Nullable String lookupNextComment(String currentThreadId, Predicate<String> isVisible) {
        return lookupAdjacentComment(currentThreadId, true, isVisible);
    }

    @Override
    public @Nullable String lookupPreviousComment(UnifiedCodeReviewItemPosition cursorLocation, Predicate<String> isVisible) {
        return lookupAdjacentComment(cursorLocation, false, isVisible);
    }

    @Override
    public @Nullable String lookupPreviousComment(String currentThreadId, Predicate<String> isVisible) {
        return lookupAdjacentComment(currentThreadId, false, isVisible);
    }

    private @Nullable String lookupAdjacentComment(UnifiedCodeReviewItemPosition cursorLocation,
                                                   boolean isNext,
                                                   Predicate<String> isVisible) {
        // Fetch stuff
        ComputedResult<TreeSet<ThreadIdAndPosition>> threadsResult = myThreadOrder.get();
        TreeSet<ThreadIdAndPosition> threads = threadsResult == null ? null : threadsResult.getOrNull();
        if (threads == null || threads.isEmpty()) {
            return null;
        }

        // Search from before the first comment on the selected line
        ThreadIdAndPosition location = new ThreadIdAndPosition(null, new Date(0), cursorLocation);

        // Find the next or previous comment
        location = isNext ? threads.ceiling(location) : threads.floor(location);

        String id = location == null ? null : location.id();
        if (id == null || isVisible.test(id)) {
            return id;
        }
        else {
            return lookupAdjacentComment(id, isNext, isVisible);
        }
    }

    private @Nullable String lookupAdjacentComment(String currentThreadId, boolean isNext, Predicate<String> isVisible) {
        // Fetch stuff
        ComputedResult<TreeSet<ThreadIdAndPosition>> threadsResult = myThreadOrder.get();
        TreeSet<ThreadIdAndPosition> threads = threadsResult == null ? null : threadsResult.getOrNull();
        if (threads == null || threads.isEmpty()) {
            return null;
        }

        // Find the current position
        ComputedResult<Map<String, ThreadIdAndPosition>> positionsResult = myThreadPositionsById.get();
        Map<String, ThreadIdAndPosition> threadPositionsById = positionsResult == null ? null : positionsResult.getOrNull();
        if (threadPositionsById == null || threadPositionsById.isEmpty()) {
            return null;
        }

        ThreadIdAndPosition location = threadPositionsById.get(currentThreadId);
        if (location == null) {
            return null;
        }

        // Find the next or previous comment
        String id;
        do {
            location = isNext ? threads.higher(location) : threads.lower(location);
            id = location == null ? null : location.id();
        }
        while (id != null && !isVisible.test(id));

        return id;
    }

    private static Comparator<UnifiedCodeReviewItemPosition> positionComparator(
        Function<RefComparisonChange, @Nullable Integer> changeIndexLookup) {
        Comparator<UnifiedCodeReviewItemPosition> byChange = (left, right) -> {
            Integer leftChangeIdx = changeIndexLookup.apply(left.change());
            Integer rightChangeIdx = changeIndexLookup.apply(right.change());

            if (Objects.equals(leftChangeIdx, rightChangeIdx)) {
                return 0;
            }

            // nulls first
            if (leftChangeIdx == null) {
                return -1;
            }
            if (rightChangeIdx == null) {
                return 1;
            }

            return leftChangeIdx.compareTo(rightChangeIdx);
        };
        return byChange.thenComparingInt(UnifiedCodeReviewItemPosition::rightLine)
            .thenComparingInt(UnifiedCodeReviewItemPosition::leftLine);
    }

    private static TreeSet<ThreadIdAndPosition> createSortedThreadPositionsAndIds(GitBranchComparisonResult allChanges,
                                                                                  List<GHPullRequestReviewThread> allThreads,
                                                                                  List<NewCommentAndPosition> newComments) {
        Map<RefComparisonChange, Integer> changeIndices = new HashMap<>();
        List<RefComparisonChange> changes = allChanges.getChanges();
        for (int idx = 0; idx < changes.size(); idx++) {
            changeIndices.put(changes.get(idx), idx);
        }

        List<ThreadIdAndPosition> locations = new ArrayList<>();
        for (GHPullRequestReviewThread threadData : allThreads) {
            GHCommitHash commit = threadData.getCommit();
            if (commit == null) {
                continue;
            }
            RefComparisonChange change = allChanges.findCumulativeChange(commit.getOid(), threadData.getPath());
            if (change == null) {
                continue;
            }
            GitTextFilePatchWithHistory diffData = allChanges.getPatchesByChange().get(change);
            if (diffData == null) {
                continue;
            }

            Integer leftLine = GHPullRequestReviewThread.mapToLeftSideLine(threadData, diffData);
            Integer rightLine = GHPullRequestReviewThread.mapToRightSideLine(threadData, diffData);
            locations.add(new ThreadIdAndPosition(threadData.getId(),
                threadData.getCreatedAt(),
                new UnifiedCodeReviewItemPosition(change, leftLine != null ? leftLine : -1, rightLine != null ? rightLine : -1)));
        }

        List<ThreadIdAndPosition> newCommentLocations = new ArrayList<>();
        for (NewCommentAndPosition newComment : newComments) {
            GHPRReviewCommentLocation location = newComment.position().location();
            int leftLine = location.side() == Side.LEFT ? location.lineIdx() : -1;
            int rightLine = location.side() == Side.LEFT ? -1 : location.lineIdx();

            newCommentLocations.add(new ThreadIdAndPosition(newComment.comment().getTrackingId(),
                newComment.comment().getCreatedAt(),
                new UnifiedCodeReviewItemPosition(newComment.position().change(), leftLine, rightLine)));
        }

        Comparator<UnifiedCodeReviewItemPosition> comparator = positionComparator(changeIndices::get);
        Comparator<ThreadIdAndPosition> threadComparator =
            Comparator.<ThreadIdAndPosition, UnifiedCodeReviewItemPosition>comparing(ThreadIdAndPosition::positionInDiff, comparator)
                .thenComparing(ThreadIdAndPosition::createdAt);
        TreeSet<ThreadIdAndPosition> result = new TreeSet<>(threadComparator);
        result.addAll(locations);
        result.addAll(newCommentLocations);
        return result;
    }

    private record ThreadIdAndPosition(@Nullable String id, Date createdAt, UnifiedCodeReviewItemPosition positionInDiff) {
    }

    private record NewCommentAndPosition(GHPRReviewNewCommentEditorViewModel comment, GHPRReviewCommentPosition position) {
    }
}
