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
package org.jetbrains.plugins.github.pullrequest.ui.timeline.item;

import consulo.diff.util.LineRange;
import consulo.diff.util.Side;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.logging.attachment.AttachmentFactory;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.async.MappingScopedItemsContainer;
import consulo.review.comment.CodeReviewNewCommentViewModel;
import consulo.review.comment.CodeReviewSubmittableTextViewModelBase;
import consulo.review.icon.IconsProvider;
import consulo.review.localize.ReviewLocalize;
import consulo.review.timeline.CodeReviewCommentViewModel;
import consulo.review.timeline.thread.RepliesStateData;
import consulo.review.util.ComputedResult;
import consulo.review.util.FutureUtil;
import consulo.review.util.SingleCoroutineLauncher;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.messageList.AsyncHtmlImageLoader;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.action.VcsContextFactory;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.change.patch.PatchHunk;
import consulo.versionControlSystem.change.patch.PatchHunkUtil;
import consulo.versionControlSystem.change.patch.PatchReader;
import consulo.versionControlSystem.change.patch.PatchSyntaxException;
import consulo.versionControlSystem.change.patch.TextFilePatch;
import consulo.versionControlSystem.review.diff.DiffLineLocation;
import consulo.versionControlSystem.review.timeline.TimelineDiffComponentFactory;
import consulo.versionControlSystem.review.util.ChangesSelection;
import consulo.versionControlSystem.review.util.PreciseChangesSelection;
import consulo.virtualFileSystem.VirtualFile;
import git4idea.changes.GitBranchComparisonResult;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHCommitHash;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThreadSubjectType;
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineUIUtil;
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRNewThreadCommentViewModel;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewThreadCommentViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelMentionCompletion;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion;
import org.jetbrains.plugins.github.pullrequest.ui.comment.UpdateableGHPRReviewThreadCommentViewModel;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class UpdateableGHPRTimelineThreadViewModel implements GHPRTimelineThreadViewModel {
    private static final Logger LOG = Logger.getInstance(UpdateableGHPRTimelineThreadViewModel.class);

    private final Project myProject;
    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;
    private final GHViewModelWithTextCompletion myViewModelWithTextCompletion;
    private final GHPRReviewDataProvider myReviewData;
    private final SingleCoroutineLauncher myTaskLauncher = new SingleCoroutineLauncher();

    private final IconsProvider<String> myAvatarIconsProvider;
    private final AsyncHtmlImageLoader myHtmlImageLoader;

    private final MutableObservableValue<GHPullRequestReviewThread> myDataState;

    private final String myId;
    private final GHActor myAuthor;
    private final Date myCreatedAt;
    private final String myFilePath;
    private final boolean myIsFileComment;

    private final ObservableValue<Boolean> myIsOutdated;
    private final ObservableValue<Boolean> myIsPending;

    private final ObservableValue<@Nullable Pair<PatchHunk, @Nullable LineRange>> myPatchHunkWithAnchor;

    private final ObservableValue<Boolean> myCanChangeResolvedState;
    private final ObservableValue<Boolean> myIsResolved;

    // fields order: put the field initialization after all fields that are used in comment VMs,
    // because they have to be initialized first to avoid NPE
    private final ObservableValue<List<UpdateableGHPRReviewThreadCommentViewModel>> myCommentsVms;

    private final MutableObservableValue<@Nullable GHPRReviewThreadCommentViewModel> myMainCommentVm = MutableObservableValue.of(null);
    private final MutableObservableValue<List<GHPRReviewThreadCommentViewModel>> myReplies = MutableObservableValue.of(List.of());
    private final MutableObservableValue<List<CodeReviewCommentViewModel>> myComments = MutableObservableValue.of(List.of());

    private final ObservableValue<RepliesStateData> myRepliesState;

    private final ObservableValue<Boolean> myCanCreateReplies;
    private final GHPRNewThreadCommentViewModel myNewReplyVm;

    private final MutableObservableValue<Boolean> myCollapsed;
    private final MutableObservableValue<Boolean> myRepliesFolded = MutableObservableValue.of(true);

    private final ComputationState<GitBranchComparisonResult> myCurrentChanges;

    private final List<Consumer<ChangesSelection>> myShowDiffRequestListeners = new CopyOnWriteArrayList<>();

    public UpdateableGHPRTimelineThreadViewModel(Project project,
                                                 Disposable parentCs,
                                                 GHPRDataContext dataContext,
                                                 GHPRDataProvider dataProvider,
                                                 GHViewModelWithTextCompletion viewModelWithTextCompletion,
                                                 GHPullRequestReviewThread initialData) {
        myProject = project;
        myCs = Disposable.newDisposable("GitHub Pull Request Timeline Thread View Model");
        Disposer.register(parentCs, myCs);
        Disposer.register(myCs, myTaskLauncher::cancel);
        myDataContext = dataContext;
        myDataProvider = dataProvider;
        myViewModelWithTextCompletion = viewModelWithTextCompletion;
        myReviewData = dataProvider.getReviewData();

        myAvatarIconsProvider = dataContext.getAvatarIconsProvider();
        myHtmlImageLoader = dataContext.getHtmlImageLoader();

        myDataState = MutableObservableValue.of(initialData);

        myId = initialData.getId();
        GHActor author = initialData.getAuthor();
        myAuthor = author != null ? author : dataContext.getSecurityService().getGhostUser();
        myCreatedAt = initialData.getCreatedAt();
        myFilePath = initialData.getPath();
        myIsFileComment = initialData.getSubjectType() == GHPullRequestReviewThreadSubjectType.FILE;

        myIsOutdated = myDataState.map(GHPullRequestReviewThread::isOutdated);
        myIsPending = myDataState.map(it -> !it.getComments().isEmpty()
            && it.getComments().get(0).getState() == GHPullRequestReviewCommentState.PENDING);

        myPatchHunkWithAnchor = myDataState.map(UpdateableGHPRTimelineThreadViewModel::calcDiffWithAnchor);

        myCanChangeResolvedState = myDataState.map(it -> it.getViewerCanResolve() || it.getViewerCanUnresolve());
        myIsResolved = myDataState.map(GHPullRequestReviewThread::isResolved);

        myCommentsVms = MappingScopedItemsContainer.mapDataToModel(myCs,
            myDataState.map(UpdateableGHPRReviewThreadCommentViewModel.IndexedComment::withIndex),
            it -> it.value().getId(),
            this::createComment,
            UpdateableGHPRReviewThreadCommentViewModel::update);
        updateComments();
        Runnable commentsListener = myCommentsVms.addListener(it -> updateComments());
        Disposer.register(myCs, commentsListener::run);

        myRepliesState = myDataState.map(it -> GHPRTimelineUIUtil.createRepliesState(it.getComments(), myAvatarIconsProvider));

        myCanCreateReplies = myDataState.map(GHPullRequestReviewThread::getViewerCanReply);
        myNewReplyVm = new ReplyViewModel();

        myCollapsed = MutableObservableValue.of(initialData.isResolved());

        myCurrentChanges = dataProvider.getChangesData().changesComputationState(myCs);
    }


    private void updateComments() {
        List<UpdateableGHPRReviewThreadCommentViewModel> comments = myCommentsVms.get();
        myMainCommentVm.set(comments.isEmpty() ? null : comments.get(0));
        myReplies.set(comments.isEmpty() ? List.of() : List.copyOf(comments.subList(1, comments.size())));
        myComments.set(List.copyOf(comments));
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
    public AsyncHtmlImageLoader getHtmlImageLoader() {
        return myHtmlImageLoader;
    }

    @Override
    public ObservableValue<Boolean> isBusy() {
        return myTaskLauncher.getBusy();
    }

    @Override
    public String getId() {
        return myId;
    }

    @Override
    public GHActor getActor() {
        return myAuthor;
    }

    @Override
    public Date getCreatedAt() {
        return myCreatedAt;
    }

    @Override
    public String getFilePath() {
        return myFilePath;
    }

    @Override
    public boolean isFileComment() {
        return myIsFileComment;
    }

    @Override
    public ObservableValue<Boolean> isOutdated() {
        return myIsOutdated;
    }

    @Override
    public ObservableValue<Boolean> isPending() {
        return myIsPending;
    }

    @Override
    public ObservableValue<@Nullable Pair<PatchHunk, @Nullable LineRange>> getPatchHunkWithAnchor() {
        return myPatchHunkWithAnchor;
    }

    @Override
    public ObservableValue<Boolean> getCanChangeResolvedState() {
        return myCanChangeResolvedState;
    }

    @Override
    public ObservableValue<Boolean> isResolved() {
        return myIsResolved;
    }

    @Override
    public ObservableValue<@Nullable GHPRReviewThreadCommentViewModel> getMainCommentVm() {
        return myMainCommentVm;
    }

    @Override
    public ObservableValue<List<GHPRReviewThreadCommentViewModel>> getReplies() {
        return myReplies;
    }

    @Override
    public ObservableValue<List<CodeReviewCommentViewModel>> getComments() {
        return myComments;
    }

    @Override
    public ObservableValue<RepliesStateData> getRepliesState() {
        return myRepliesState;
    }

    @Override
    public ObservableValue<Boolean> getCanCreateReplies() {
        return myCanCreateReplies;
    }

    @Override
    public GHPRNewThreadCommentViewModel getNewReplyVm() {
        return myNewReplyVm;
    }

    @Override
    public CodeReviewNewCommentViewModel getReplyVm() {
        return myNewReplyVm;
    }

    @Override
    public ObservableValue<Boolean> getCollapsible() {
        return myIsResolved;
    }

    @Override
    public ObservableValue<Boolean> getCollapsed() {
        return myCollapsed;
    }

    @Override
    public ObservableValue<Boolean> getRepliesFolded() {
        return myRepliesFolded;
    }

    @Override
    public @Nullable String getLocation() {
        return myFilePath;
    }

    @Override
    public boolean canNavigate() {
        return true;
    }

    @RequiredUIAccess
    @Override
    public void navigate() {
        showDiff();
    }

    @RequiredUIAccess
    @Override
    public @Nullable Component createDiffComponent(Disposable parentDisposable) {
        TimelineDiffComponentFactory factory = myProject.getInstance(TimelineDiffComponentFactory.class);
        return factory.createDiffWithHeader(parentDisposable,
            this,
            myFilePath,
            ObservableValue.<@Nullable Runnable>of(this::showDiff),
            disposable -> {
                Pair<PatchHunk, @Nullable LineRange> hunkWithAnchor = myPatchHunkWithAnchor.get();
                if (hunkWithAnchor != null && !hunkWithAnchor.getFirst().getLines().isEmpty()) {
                    return factory.createDiffComponent(disposable, hunkWithAnchor.getFirst(), hunkWithAnchor.getSecond());
                }
                else if (myIsFileComment) {
                    return VerticalLayout.create();
                }
                else {
                    return Label.create(ReviewLocalize.reviewThreadDiffNotLoaded());
                }
            });
    }

    public void addShowDiffRequestListener(Disposable parentDisposable, Consumer<ChangesSelection> listener) {
        myShowDiffRequestListeners.add(listener);
        Disposer.register(parentDisposable, () -> myShowDiffRequestListeners.remove(listener));
    }

    @Override
    public void changeResolvedState() {
        boolean resolved = myIsResolved.get();
        myTaskLauncher.launch(() -> {
            CompletableFuture<GHPullRequestReviewThread> request =
                resolved ? myReviewData.unresolveThread(myId) : myReviewData.resolveThread(myId);
            return request.handle((newData, error) -> {
                if (error != null) {
                    if (!FutureUtil.isCancellation(error)) {
                        LOG.warn("Failed to change thread resolution", FutureUtil.unwrap(error));
                    }
                    return null;
                }
                myDataState.set(newData);
                setCollapsed(newData.isResolved());
                return null;
            });
        });
    }

    @Override
    public void setCollapsed(boolean collapsed) {
        myCollapsed.set(collapsed);
        if (collapsed) {
            myRepliesFolded.set(true);
        }
    }

    @Override
    public void unfoldReplies() {
        myRepliesFolded.set(false);
        myCollapsed.set(false);
        myNewReplyVm.requestFocus();
    }

    @Override
    public void showDiff() {
        ComputedResult<GitBranchComparisonResult> changesResult = myCurrentChanges.get();
        GitBranchComparisonResult changes = changesResult == null ? null : changesResult.getOrNull();
        if (changes == null) {
            return;
        }
        GHPullRequestReviewThread thread = myDataState.get();
        String path = thread.getPath();
        GHCommitHash commit = thread.getCommit();
        Integer line = thread.getLine();
        DiffLineLocation location = line == null ? null : new DiffLineLocation(thread.getSide(), line - 1);
        ChangesSelection selection;
        if (commit == null || changes.getHeadSha().equals(commit.getOid())) {
            RefComparisonChange change = findByFilePath(changes.getChanges(), path);
            if (change == null) {
                return;
            }
            selection = new PreciseChangesSelection(changes.getChanges(), change, location);
        }
        else {
            List<RefComparisonChange> commitChanges = changes.getChangesByCommits().get(commit.getOid());
            RefComparisonChange change = commitChanges == null ? null : findByFilePath(commitChanges, path);
            if (commitChanges == null || change == null) {
                return;
            }
            selection = new PreciseChangesSelection(commitChanges, change, location);
        }
        for (Consumer<ChangesSelection> listener : myShowDiffRequestListeners) {
            listener.accept(selection);
        }
    }

    public void update(GHPullRequestReviewThread data) {
        myDataState.set(data);
    }

    private static @Nullable Pair<PatchHunk, @Nullable LineRange> calcDiffWithAnchor(GHPullRequestReviewThread thread) {
        // GitHub returns an empty diff hunk for file-level comments and some outdated threads: there is nothing to anchor a diff to.
        if (thread.getDiffHunk().isBlank()) {
            return null;
        }

        PatchHunk hunk = null;
        try {
            PatchReader patchReader = new PatchReader(PatchHunkUtil.createPatchFromHunk("_", thread.getDiffHunk()));
            List<TextFilePatch> patches = patchReader.readTextPatches();
            if (!patches.isEmpty() && !patches.get(0).getHunks().isEmpty()) {
                hunk = patches.get(0).getHunks().get(0);
            }
        }
        catch (PatchSyntaxException e) {
            LOG.warn(e);
        }
        if (hunk == null) {
            LOG.error("Failed to parse non-empty diff hunk for thread " + thread.getId(),
                AttachmentFactory.get().create("threadData.txt", thread.toString()),
                AttachmentFactory.get().create("threadDiffHunk.txt", thread.getDiffHunk()));
            return null;
        }

        Integer originalLine = thread.getOriginalLine();
        Pair<Side, Integer> anchorLocation = originalLine == null ? null : Pair.create(thread.getSide(), originalLine - 1);
        Pair<Side, Integer> startAnchorLocation = null;
        Side startSide = thread.getStartSide();
        Integer originalStartLine = thread.getOriginalStartLine();
        if (startSide != null && originalStartLine != null) {
            int hunkStartLine = startSide == Side.LEFT ? hunk.getStartLineBefore() : hunk.getStartLineAfter();
            startAnchorLocation = Pair.create(startSide, Math.max(originalStartLine - 1, hunkStartLine));
        }

        Side anchorSide = anchorLocation == null ? null : anchorLocation.getFirst();
        Side startAnchorSide = startAnchorLocation == null ? null : startAnchorLocation.getFirst();
        int anchorLength;
        if (startAnchorSide == anchorSide) {
            int anchorLine = anchorLocation == null ? 0 : anchorLocation.getSecond();
            int startAnchorLine = startAnchorLocation == null ? 0 : startAnchorLocation.getSecond();
            anchorLength = Math.max(anchorLine - startAnchorLine, 0);
        }
        else {
            anchorLength = 0;
        }

        int hunkLength = anchorLength + TimelineDiffComponentFactory.DIFF_CONTEXT_SIZE;
        PatchHunk truncatedHunk = PatchHunkUtil.truncateHunkBefore(hunk, hunk.getLines().size() - 1 - hunkLength);

        LineRange anchorRange = new LineRange(truncatedHunk.getLines().size() - 1 - anchorLength, truncatedHunk.getLines().size());
        return Pair.create(truncatedHunk, anchorRange);
    }

    private UpdateableGHPRReviewThreadCommentViewModel createComment(Disposable cs,
                                                                     UpdateableGHPRReviewThreadCommentViewModel.IndexedComment comment) {
        return new UpdateableGHPRReviewThreadCommentViewModel(myProject, cs, myDataContext, myDataProvider,
            myViewModelWithTextCompletion, comment,
            myId, myIsResolved);
    }

    private @Nullable RefComparisonChange findByFilePath(Collection<RefComparisonChange> changes, String path) {
        VirtualFile repoRoot = myDataContext.getRepositoryDataService().getRemoteCoordinates().repository().getRoot();
        FilePath filePath = VcsContextFactory.getInstance().createFilePath(repoRoot, path, false);
        for (RefComparisonChange change : changes) {
            if ((change.filePathAfter() != null && Objects.equals(change.filePathAfter(), filePath))
                || (change.filePathBefore() != null && Objects.equals(change.filePathBefore(), filePath))) {
                return change;
            }
        }
        return null;
    }

    private class ReplyViewModel extends CodeReviewSubmittableTextViewModelBase implements GHPRNewThreadCommentViewModel {
        private final GHActor myCurrentUser = myDataContext.getSecurityService().getCurrentUser();

        ReplyViewModel() {
            super(myProject, myCs, "");
        }

        @Override
        public GHActor getCurrentUser() {
            return myCurrentUser;
        }

        @Override
        public void withMentionCompletionModel(Consumer<GHViewModelMentionCompletion> consumer) {
            myViewModelWithTextCompletion.withMentionCompletionModel(consumer);
        }

        @Override
        public void submit() {
            List<GHPullRequestReviewComment> comments = myDataState.get().getComments();
            if (comments.isEmpty()) {
                return;
            }
            String replyId = comments.get(0).getId();
            submit(text -> myReviewData.addComment(replyId, text).thenRun(() -> getText().set("")));
        }
    }
}
