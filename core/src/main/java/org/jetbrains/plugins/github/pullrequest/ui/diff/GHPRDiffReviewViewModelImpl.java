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

import consulo.diff.util.Range;
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
import consulo.versionControlSystem.change.patch.PatchHunk;
import consulo.versionControlSystem.change.patch.PatchHunkUtil;
import consulo.versionControlSystem.review.diff.DiffLineLocation;
import consulo.versionControlSystem.review.util.RefComparisonChangeUtil;
import consulo.versionControlSystem.util.VcsFileUtil;
import git4idea.changes.GitTextFilePatchWithHistory;
import git4idea.repo.GitRepository;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestFileViewedState;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModels;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GHPRDiffReviewViewModelImpl implements GHPRDiffReviewViewModel {
    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;
    private final RefComparisonChange myChange;
    private final GitTextFilePatchWithHistory myDiffData;
    private final GHPRThreadsViewModels myThreadsVms;
    private final ObservableValue<? extends List<? extends GHPRReviewThreadDiffViewModel>> myAllThreads;

    private final boolean myCanComment;
    private final boolean myCanNavigate;

    private final List<Range> myCommentableRanges;
    private final List<Range> myChangedRanges;

    private final MutableObservableValue<Collection<GHPRReviewThreadDiffViewModel>> myThreads = MutableObservableValue.of(List.of());

    private final MutableObservableValue<Set<DiffLineLocation>> myLocationsWithDiscussions = MutableObservableValue.of(Set.of());

    private final MappingScopedItemsContainer<GHPRReviewNewCommentEditorViewModel, GHPRNewCommentDiffViewModel> myNewCommentsContainer;

    private final MutableObservableValue<ComputedResult<Boolean>> myIsViewedState = MutableObservableValue.of(ComputedResult.loading());

    private @Nullable Disposable myThreadMappingsSubscription;

    public GHPRDiffReviewViewModelImpl(Project project,
                                       Disposable parentCs,
                                       GHPRDataContext dataContext,
                                       GHPRDataProvider dataProvider,
                                       RefComparisonChange change,
                                       GitTextFilePatchWithHistory diffData,
                                       GHPRThreadsViewModels threadsVms,
                                       ObservableValue<? extends List<? extends GHPRReviewThreadDiffViewModel>> allThreads) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myDataContext = dataContext;
        myDataProvider = dataProvider;
        myChange = change;
        myDiffData = diffData;
        myThreadsVms = threadsVms;
        myAllThreads = allThreads;

        myCanComment = threadsVms.canComment();
        myCanNavigate = diffData.isCumulative();

        List<Range> commentableRanges = new ArrayList<>();
        List<Range> changedRanges = new ArrayList<>();
        for (PatchHunk hunk : diffData.getPatch().getHunks()) {
            commentableRanges.add(PatchHunkUtil.getRange(hunk));
            changedRanges.addAll(PatchHunkUtil.getChangeOnlyRanges(hunk));
        }
        myCommentableRanges = List.copyOf(commentableRanges);
        myChangedRanges = List.copyOf(changedRanges);

        // Filter out only the threads relevant to the diff
        Disposer.register(myCs, allThreads.addListener(threads -> resubscribeThreadMappings())::run);
        resubscribeThreadMappings();

        myNewCommentsContainer = MappingScopedItemsContainer.byIdentity(myCs,
            (scope, it) -> new GHPRNewCommentDiffViewModelImpl(scope, it.getPosition(), it));

        Runnable updateNewComments = () -> {
            List<GHPRReviewNewCommentEditorViewModel> commentForChange = new ArrayList<>();
            for (GHPRReviewNewCommentEditorViewModel comment : threadsVms.getNewComments().get()) {
                if (comment.getPosition().get().change().equals(change)) {
                    commentForChange.add(comment);
                }
            }
            myNewCommentsContainer.update(commentForChange);
        };
        Disposer.register(myCs, threadsVms.getNewComments().addListener(it -> updateNewComments.run())::run);
        updateNewComments.run();

        ComputationState<Map<String, GHPullRequestFileViewedState>> viewedState =
            dataProvider.getViewedStateData().viewedStateComputationState(myCs);
        Runnable updateViewedState = () -> {
            ComputedResult<Map<String, GHPullRequestFileViewedState>> viewedStateByPath = viewedState.get();
            Map<String, GHPullRequestFileViewedState> byPath = viewedStateByPath == null ? null : viewedStateByPath.getOrNull();
            GHPullRequestFileViewedState fileState = byPath == null ? null : byPath.get(getPath());
            myIsViewedState.set(fileState == null ? ComputedResult.loading() : ComputedResult.success(fileState.isViewed()));
        };
        Disposer.register(myCs, viewedState.addListener(it -> updateViewedState.run())::run);
        updateViewedState.run();
    }

    private GitRepository getRepository() {
        return myDataContext.getRepositoryDataService().getRemoteCoordinates().repository();
    }

    private String getPath() {
        return VcsFileUtil.relativePath(getRepository().getRoot(), RefComparisonChangeUtil.getFilePath(myChange));
    }

    private void resubscribeThreadMappings() {
        Disposable subscription = Disposable.newDisposable("GHPRDiffReviewViewModel.threadMappings");
        synchronized (this) {
            Disposable previous = myThreadMappingsSubscription;
            myThreadMappingsSubscription = null;
            if (previous != null) {
                Disposer.dispose(previous);
            }
            if (!Disposer.tryRegister(myCs, subscription)) {
                return;
            }
            myThreadMappingsSubscription = subscription;
        }
        for (GHPRReviewThreadDiffViewModel thread : myAllThreads.get()) {
            Disposer.register(subscription, thread.getMapping().addListener(mapping -> updateThreads())::run);
        }
        updateThreads();
    }

    private synchronized void updateThreads() {
        List<GHPRReviewThreadDiffViewModel> threads = new ArrayList<>();
        Set<DiffLineLocation> locations = new LinkedHashSet<>();
        for (GHPRReviewThreadDiffViewModel thread : myAllThreads.get()) {
            GHPRReviewThreadDiffViewModel.MappingData mapping = thread.getMapping().get();
            if (!myChange.equals(mapping.change())) {
                continue;
            }
            threads.add(thread);
            if (mapping.isVisible() && mapping.location() != null) {
                locations.add(mapping.location().getLineLocation());
            }
        }
        myThreads.set(List.copyOf(threads));
        myLocationsWithDiscussions.set(Set.copyOf(locations));
    }

    @Override
    public boolean canComment() {
        return myCanComment;
    }

    @Override
    public boolean canNavigate() {
        return myCanNavigate;
    }

    @Override
    public List<Range> getCommentableRanges() {
        return myCommentableRanges;
    }

    @Override
    public List<Range> getChangedRanges() {
        return myChangedRanges;
    }

    @Override
    public ObservableValue<Collection<GHPRReviewThreadDiffViewModel>> getThreads() {
        return myThreads;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ObservableValue<Collection<GHPRNewCommentDiffViewModel>> getNewComments() {
        return (ObservableValue<Collection<GHPRNewCommentDiffViewModel>>) (ObservableValue<?>) myNewCommentsContainer.getMappedState();
    }

    @Override
    public ObservableValue<Set<DiffLineLocation>> getLocationsWithDiscussions() {
        return myLocationsWithDiscussions;
    }

    @Override
    public void requestNewComment(GHPRReviewCommentLocation location, boolean focus) {
        GHPRReviewCommentPosition position = new GHPRReviewCommentPosition(myChange, location);
        GHPRReviewNewCommentEditorViewModel sharedVm = myThreadsVms.requestNewComment(position);
        if (focus) {
            myNewCommentsContainer.addIfAbsent(sharedVm).requestFocus();
        }
    }

    @Override
    public void cancelNewComment(Side side, int lineIdx) {
        myThreadsVms.cancelNewComment(myChange, side, lineIdx);
    }

    @Override
    public ObservableValue<ComputedResult<Boolean>> isViewedState() {
        return myIsViewedState;
    }

    @Override
    public void setViewedState(boolean isViewed) {
        if (!myDiffData.isCumulative()) {
            return;
        }
        // TODO: handle error
        myDataProvider.getViewedStateData().updateViewedState(List.of(getPath()), isViewed);
    }
}
