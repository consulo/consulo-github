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
package org.jetbrains.plugins.github.pullrequest.ui.editor;

import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.diff.util.LineRange;
import consulo.diff.util.Range;
import consulo.diff.util.Side;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.async.MappingScopedItemsContainer;
import consulo.review.icon.IconsProvider;
import consulo.review.util.ComputedResult;
import consulo.review.util.FutureUtil;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.change.ContentRevision;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.change.patch.PatchHunk;
import consulo.versionControlSystem.change.patch.PatchHunkUtil;
import consulo.versionControlSystem.change.patch.TextFilePatch;
import consulo.versionControlSystem.review.diff.UnifiedCodeReviewItemPosition;
import consulo.versionControlSystem.review.util.RefComparisonChangeUtil;
import consulo.versionControlSystem.util.VcsFileUtil;
import git4idea.changes.GitBranchComparisonResult;
import git4idea.changes.GitTextFilePatchWithHistory;
import git4idea.repo.GitRepository;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestFileViewedState;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModels;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public class GHPRReviewFileEditorViewModelImpl implements GHPRReviewFileEditorViewModel {
    private static final Logger LOG = Logger.getInstance(GHPRReviewFileEditorViewModelImpl.class);

    private final Project myProject;
    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;
    private final RefComparisonChange myChange;
    private final GitTextFilePatchWithHistory myDiffData;
    private final GHPRThreadsViewModels myThreadsVm;
    private final ObservableValue<Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData>> myAllMappedThreads;
    private final BiConsumer<RefComparisonChange, @Nullable Integer> myShowDiff;

    private final IconsProvider<String> myIconProvider;
    private final GHUser myCurrentUser;

    private final ObservableValue<Boolean> myIsUpdateRequired;

    private final MutableObservableValue<@Nullable ComputedResult<CharSequence>> myOriginalContent =
        MutableObservableValue.of(ComputedResult.loading());
    private final AtomicBoolean myOriginalContentRequested = new AtomicBoolean();

    private final List<Range> myChangedRanges;

    private final boolean myCanComment;
    private final List<Range> myCommentableRanges;

    private final MutableObservableValue<Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData>> myMappedThreads;

    private final ObservableValue<List<GHPRReviewFileEditorThreadViewModel>> myThreads;

    private final MutableObservableValue<Set<Integer>> myLinesWithComments = MutableObservableValue.of(Set.of());

    private final MappingScopedItemsContainer<GHPRReviewNewCommentEditorViewModel, GHPRReviewFileEditorNewCommentViewModel> myNewCommentsContainer;
    private final ObservableValue<List<GHPRReviewFileEditorNewCommentViewModel>> myNewComments;

    private final MutableObservableValue<ComputedResult<Boolean>> myIsViewedState = MutableObservableValue.of(ComputedResult.loading());

    public GHPRReviewFileEditorViewModelImpl(Project project,
                                             Disposable parentCs,
                                             GHPRDataContext dataContext,
                                             GHPRDataProvider dataProvider,
                                             GHPRReviewInEditorViewModel reviewInEditorVm,
                                             RefComparisonChange change,
                                             GitTextFilePatchWithHistory diffData,
                                             GHPRThreadsViewModels threadsVm,
                                             ObservableValue<Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData>> allMappedThreads,
                                             BiConsumer<RefComparisonChange, @Nullable Integer> showDiff) {
        myProject = project;
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myDataContext = dataContext;
        myDataProvider = dataProvider;
        myChange = change;
        myDiffData = diffData;
        myThreadsVm = threadsVm;
        myAllMappedThreads = allMappedThreads;
        myShowDiff = showDiff;

        myIconProvider = dataContext.getAvatarIconsProvider();
        myCurrentUser = dataContext.getSecurityService().getCurrentUser();

        myIsUpdateRequired = reviewInEditorVm.getUpdateRequired();

        myChangedRanges = PatchHunkUtil.withoutContext(diffData.getPatch().getHunks());

        myCanComment = dataProvider.getReviewData().canComment();
        myCommentableRanges = getRanges(diffData.getPatch());

        myMappedThreads = MutableObservableValue.of(filterMappedThreads(allMappedThreads.get()));
        Disposer.register(myCs, allMappedThreads.addListener(map -> myMappedThreads.set(filterMappedThreads(map)))::run);

        myThreads = MappingScopedItemsContainer.<GHPRCompactReviewThreadViewModel, GHPRReviewFileEditorThreadViewModel>mapStatefulToStateful(myCs,
            threadsVm.getCompactThreads(),
            (threadCs, sharedVm) -> new MappedGHPRReviewEditorThreadViewModel(threadCs, sharedVm, myMappedThreads));

        Disposer.register(myCs, myMappedThreads.addListener(this::updateLinesWithComments)::run);
        updateLinesWithComments(myMappedThreads.get());

        myNewCommentsContainer = MappingScopedItemsContainer.byIdentity(myCs,
            (commentCs, sharedVm) -> new GHPRReviewFileEditorNewCommentViewModelImpl(commentCs, sharedVm.getPosition(), sharedVm));
        myNewComments = myNewCommentsContainer.getMappedState();

        Disposer.register(myCs, threadsVm.getNewComments().addListener(this::updateNewComments)::run);
        updateNewComments(threadsVm.getNewComments().get());

        ComputationState<Map<String, GHPullRequestFileViewedState>> viewedStateComputationState =
            dataProvider.getViewedStateData().viewedStateComputationState(myCs);
        Disposer.register(myCs, viewedStateComputationState.addListener(this::updateViewedState)::run);
        updateViewedState(viewedStateComputationState.get());
    }

    private GitRepository getRepository() {
        return myDataContext.getRepositoryDataService().getRemoteCoordinates().repository();
    }

    private String getPath() {
        return VcsFileUtil.relativePath(getRepository().getRoot(), RefComparisonChangeUtil.getFilePath(myChange));
    }

    @Override
    public RefComparisonChange getChange() {
        return myChange;
    }

    @Override
    public ObservableValue<Boolean> isUpdateRequired() {
        return myIsUpdateRequired;
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
    public ObservableValue<@Nullable ComputedResult<CharSequence>> getOriginalContent() {
        if (myOriginalContentRequested.compareAndSet(false, true)) {
            loadOriginalContent();
        }
        return myOriginalContent;
    }

    private void loadOriginalContent() {
        CompletableFuture<CharSequence> future = CompletableFuture.supplyAsync(() -> {
            ContentRevision afterRevision = GitBranchComparisonResult.createVcsChange(myChange, myProject).getAfterRevision();
            String content;
            try {
                content = afterRevision == null ? null : afterRevision.getContent();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
            return StringUtil.convertLineSeparators(content == null ? "" : content);
        }, AppExecutorUtil.getAppExecutorService());
        Disposer.register(myCs, () -> future.cancel(true));
        future.whenComplete((content, error) -> {
            if (error == null) {
                myOriginalContent.set(ComputedResult.success(content));
            }
            else if (!FutureUtil.isCancellation(error)) {
                Throwable cause = FutureUtil.unwrap(error);
                LOG.warn("Couldn't load head content for " + myChange, cause);
                myOriginalContent.set(ComputedResult.failure(cause));
            }
        });
    }

    @Override
    public List<Range> getChangedRanges() {
        return myChangedRanges;
    }

    @Override
    public @Nullable String getBaseContent(LineRange lines) {
        if (lines.start() == lines.end()) {
            return "";
        }
        return PatchHunkUtil.getLinesLeft(myDiffData.getPatch(), lines);
    }

    @Override
    public boolean getCanComment() {
        return myCanComment;
    }

    @Override
    public List<Range> getCommentableRanges() {
        return myCommentableRanges;
    }

    private Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData> filterMappedThreads(
        Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData> map) {
        Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData> result = new LinkedHashMap<>();
        for (Map.Entry<String, MappedGHPRReviewEditorThreadViewModel.MappingData> entry : map.entrySet()) {
            if (Objects.equals(entry.getValue().change(), myChange)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(result);
    }

    @Override
    public ObservableValue<List<GHPRReviewFileEditorThreadViewModel>> getThreads() {
        return myThreads;
    }

    private void updateLinesWithComments(Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData> mappedThreads) {
        Set<Integer> lines = new LinkedHashSet<>();
        for (MappedGHPRReviewEditorThreadViewModel.MappingData data : mappedThreads.values()) {
            Integer line = data.getLine();
            if (line != null && data.isVisible()) {
                lines.add(line);
            }
        }
        myLinesWithComments.set(Set.copyOf(lines));
    }

    @Override
    public ObservableValue<Set<Integer>> getLinesWithComments() {
        return myLinesWithComments;
    }

    @Override
    public ObservableValue<List<GHPRReviewFileEditorNewCommentViewModel>> getNewComments() {
        return myNewComments;
    }

    private void updateNewComments(Collection<GHPRReviewNewCommentEditorViewModel> comments) {
        List<GHPRReviewNewCommentEditorViewModel> commentsForChange = new ArrayList<>();
        for (GHPRReviewNewCommentEditorViewModel comment : comments) {
            GHPRReviewCommentPosition position = comment.getPosition().get();
            if (position.change().equals(myChange) && position.location().side() == Side.RIGHT) {
                commentsForChange.add(comment);
            }
        }
        myNewCommentsContainer.update(commentsForChange);
    }

    @Override
    public @Nullable String lookupNextComment(String threadId) {
        return myThreadsVm.lookupNextComment(threadId, this::threadIsVisible);
    }

    @Override
    public @Nullable String lookupNextComment(int line) {
        return myThreadsVm.lookupNextComment(lineToUnified(line), this::threadIsVisible);
    }

    @Override
    public @Nullable String lookupPreviousComment(String threadId) {
        return myThreadsVm.lookupPreviousComment(threadId, this::threadIsVisible);
    }

    @Override
    public @Nullable String lookupPreviousComment(int line) {
        return myThreadsVm.lookupPreviousComment(lineToUnified(line), this::threadIsVisible);
    }

    @Override
    public @Nullable Pair<RefComparisonChange, Integer> getThreadPosition(String threadId) {
        MappedGHPRReviewEditorThreadViewModel.MappingData position = myAllMappedThreads.get().get(threadId);
        if (position != null) {
            RefComparisonChange change = position.change();
            Integer line = position.getLine();
            if (change == null || line == null) {
                return null;
            }
            return Pair.create(change, line);
        }
        for (GHPRReviewFileEditorNewCommentViewModel newComment : myNewComments.get()) {
            if (newComment.getTrackingId().equals(threadId)) {
                GHPRReviewCommentPosition commentPosition = newComment.getPosition().get();
                return Pair.create(commentPosition.change(), commentPosition.location().lineIdx());
            }
        }
        return null;
    }

    @Override
    public void requestThreadFocus(String threadId) {
        for (GHPRCompactReviewThreadViewModel thread : myThreadsVm.getCompactThreads().get()) {
            if (thread.getId().equals(threadId)) {
                thread.requestFocus();
                return;
            }
        }
        for (GHPRReviewFileEditorNewCommentViewModel newComment : myNewComments.get()) {
            if (newComment.getTrackingId().equals(threadId)) {
                newComment.requestFocus();
                return;
            }
        }
    }

    /**
     * We don't really care about the left-sided line number. It needs to be at the beginning to make sure
     * the first comment on the line is picked though.
     */
    private UnifiedCodeReviewItemPosition lineToUnified(int line) {
        return new UnifiedCodeReviewItemPosition(myChange, -1, line);
    }

    private boolean threadIsVisible(String threadId) {
        MappedGHPRReviewEditorThreadViewModel.MappingData data = myAllMappedThreads.get().get(threadId);
        if (data != null) {
            RefComparisonChange change = data.change();
            return data.isVisible() && data.getLine() != null && change != null && change.filePathAfter() != null;
        }

        for (GHPRReviewFileEditorNewCommentViewModel newComment : myNewComments.get()) {
            if (newComment.getTrackingId().equals(threadId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void requestNewComment(int lineIdx, boolean focus) {
        GHPRReviewCommentPosition position = new GHPRReviewCommentPosition(myChange, new GHPRReviewCommentLocation.SingleLine(Side.RIGHT, lineIdx));
        GHPRReviewNewCommentEditorViewModel sharedVm = myThreadsVm.requestNewComment(position);
        if (focus) {
            myNewCommentsContainer.addIfAbsent(sharedVm).requestFocus();
        }
    }

    @Override
    public void requestNewComment(LineRange lineRange, boolean focus) {
        GHPRReviewCommentPosition position = new GHPRReviewCommentPosition(myChange,
            new GHPRReviewCommentLocation.MultiLine(Side.RIGHT, lineRange.start(), Side.RIGHT, lineRange.end()));
        GHPRReviewNewCommentEditorViewModel sharedVm = myThreadsVm.requestNewComment(position);
        if (focus) {
            myNewCommentsContainer.addIfAbsent(sharedVm).requestFocus();
        }
    }

    @Override
    public void cancelNewComment(int lineIdx) {
        myThreadsVm.cancelNewComment(myChange, Side.RIGHT, lineIdx);
    }

    @Override
    public void showDiff(@Nullable Integer lineIdx) {
        myShowDiff.accept(myChange, lineIdx);
    }

    private void updateViewedState(@Nullable ComputedResult<Map<String, GHPullRequestFileViewedState>> viewedStateByPath) {
        Map<String, GHPullRequestFileViewedState> states = viewedStateByPath == null ? null : viewedStateByPath.getOrNull();
        GHPullRequestFileViewedState state = states == null ? null : states.get(getPath());
        myIsViewedState.set(state == null ? ComputedResult.loading() : ComputedResult.success(state.isViewed()));
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
        myDataProvider.getViewedStateData().updateViewedState(List.of(getPath()), isViewed);
    }

    public static List<Range> getRanges(TextFilePatch patch) {
        List<Range> ranges = new ArrayList<>();
        for (PatchHunk hunk : patch.getHunks()) {
            ranges.add(PatchHunkUtil.getRange(hunk));
        }
        return List.copyOf(ranges);
    }
}
