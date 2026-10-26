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

import consulo.diff.util.Side;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.comment.CodeReviewCommentSubmitAction;
import consulo.review.comment.CodeReviewSubmittableTextViewModelBase;
import consulo.review.icon.IconsProvider;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.review.diff.DiffLineLocation;
import consulo.versionControlSystem.review.util.RefComparisonChangeUtil;
import consulo.versionControlSystem.util.VcsFileUtil;
import git4idea.changes.GitBranchComparisonResult;
import git4idea.repo.GitRepository;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent;
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelMentionCompletion;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel.SubmitAction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class GHPRReviewNewCommentEditorViewModelImpl extends CodeReviewSubmittableTextViewModelBase implements GHPRReviewNewCommentEditorViewModel {
    private final Disposable myCs;
    private final GitRepository myRepository;
    private final GHActor myCurrentUser;
    private final GHViewModelWithTextCompletion myViewModelWithTextCompletion;
    private final IconsProvider<String> myAvatarIconsProvider;
    private final Consumer<GHPRReviewCommentPosition> myOnCancel;

    private final GithubPullRequestsProjectUISettings mySettings;
    private final GHPRReviewDataProvider myReviewDataProvider;
    private final ComputationState<GitBranchComparisonResult> myChangesState;

    private final MutableObservableValue<GHPRReviewCommentPosition> myPosition;

    private final ComputationState<@Nullable GHPullRequestPendingReview> myPendingReviewState;

    private final ObservableValue<Boolean> myReviewCommentPreferred;

    private final SubmitAction.CreateSingleComment myCreateSingleCommentAction;
    private final SubmitAction.CreateReview myCreateReviewAction;

    private final MutableObservableValue<List<SubmitAction>> mySubmitActions = MutableObservableValue.of(List.of());
    private final MutableObservableValue<List<CodeReviewCommentSubmitAction>> myCommentSubmitActions = MutableObservableValue.of(List.of());

    private final Date myCreatedAt = new Date();

    private final String myTrackingId = UUID.randomUUID().toString();

    public GHPRReviewNewCommentEditorViewModelImpl(Project project,
                                                   Disposable parentCs,
                                                   GHPRDataProvider dataProvider,
                                                   GitRepository repository,
                                                   GHActor currentUser,
                                                   GHViewModelWithTextCompletion viewModelWithTextCompletion,
                                                   IconsProvider<String> avatarIconsProvider,
                                                   GHPRReviewCommentPosition pos,
                                                   Consumer<GHPRReviewCommentPosition> onCancel) {
        super(project, parentCs, "");
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(this, myCs);
        myRepository = repository;
        myCurrentUser = currentUser;
        myViewModelWithTextCompletion = viewModelWithTextCompletion;
        myAvatarIconsProvider = avatarIconsProvider;
        myOnCancel = onCancel;

        mySettings = GithubPullRequestsProjectUISettings.getInstance(project);
        myReviewDataProvider = dataProvider.getReviewData();
        myChangesState = dataProvider.getChangesData().changesComputationState(myCs);

        myPosition = MutableObservableValue.of(pos);

        myPendingReviewState = myReviewDataProvider.pendingReviewComputationFlow(myCs);
        myReviewCommentPreferred = mySettings.getReviewCommentsPreferredState();

        myCreateSingleCommentAction = () -> submit(text -> {
            GHPullRequestDraftReviewThread thread = createThreadDTO(text);
            String commitSha = myPosition.get().change().revisionNumberAfter().asString();
            return myReviewDataProvider.createReview(GHPullRequestReviewEvent.COMMENT, null, commitSha, List.of(thread)).thenRun(() -> {
                mySettings.setReviewCommentsPreferred(false);
                cancel();
            });
        });

        myCreateReviewAction = () -> submit(text -> {
            GHPullRequestDraftReviewThread thread = createThreadDTO(text);
            String commitSha = myPosition.get().change().revisionNumberAfter().asString();
            return myReviewDataProvider.createReview(null, null, commitSha, List.of(thread)).thenRun(() -> {
                mySettings.setReviewCommentsPreferred(true);
                cancel();
            });
        });

        Disposer.register(myCs, myPendingReviewState.addListener(it -> updateSubmitActions())::run);
        Disposer.register(myCs, myReviewCommentPreferred.addListener(it -> updateSubmitActions())::run);
        Disposer.register(myCs, myChangesState.addListener(it -> updateSubmitActions())::run);
        Disposer.register(myCs, myPosition.addListener(it -> updateSubmitActions())::run);
        updateSubmitActions();
    }

    @Override
    public ObservableValue<GHPRReviewCommentPosition> getPosition() {
        return myPosition;
    }

    @Override
    public GHActor getCurrentUser() {
        return myCurrentUser;
    }

    @Override
    public IconsProvider<String> getAvatarIconsProvider() {
        return myAvatarIconsProvider;
    }

    @Override
    public void withMentionCompletionModel(Consumer<GHViewModelMentionCompletion> consumer) {
        myViewModelWithTextCompletion.withMentionCompletionModel(consumer);
    }

    @Override
    public void updateLineRange(@Nullable DiffLineLocation newStartLocation, @Nullable DiffLineLocation newEndLocation) {
        GHPRReviewCommentPosition position = myPosition.get();
        GHPRReviewCommentLocation location = position.location();
        DiffLineLocation oldStartLocation;
        DiffLineLocation oldEndLocation;
        if (location instanceof GHPRReviewCommentLocation.MultiLine multiLine) {
            oldStartLocation = new DiffLineLocation(multiLine.startSide(), multiLine.startLineIdx());
            oldEndLocation = new DiffLineLocation(multiLine.side(), multiLine.lineIdx());
        }
        else {
            oldStartLocation = new DiffLineLocation(location.side(), location.lineIdx());
            oldEndLocation = oldStartLocation;
        }
        DiffLineLocation start = newStartLocation != null ? newStartLocation : oldStartLocation;
        DiffLineLocation end = newEndLocation != null ? newEndLocation : oldEndLocation;
        if (start.line() == end.line()) {
            myPosition.set(new GHPRReviewCommentPosition(position.change(), new GHPRReviewCommentLocation.SingleLine(end.side(), end.line())));
        }
        else {
            myPosition.set(new GHPRReviewCommentPosition(position.change(),
                new GHPRReviewCommentLocation.MultiLine(start.side(), start.line(), end.side(), end.line())));
        }
    }

    private synchronized void updateSubmitActions() {
        ComputedResult<@Nullable GHPullRequestPendingReview> reviewResult = loadingIfNull(myPendingReviewState.get());
        ComputedResult<GitBranchComparisonResult> changesResult = loadingIfNull(myChangesState.get());
        boolean reviewPreferred = myReviewCommentPreferred.get();

        List<SubmitAction> actions;
        if (reviewResult.isSuccess() && changesResult.isSuccess()) {
            GHPullRequestPendingReview review = reviewResult.getOrNull();
            GitBranchComparisonResult changes = changesResult.getOrNull();
            if (review == null) {
                if (reviewPreferred) {
                    actions = List.of(myCreateReviewAction, myCreateSingleCommentAction);
                }
                else {
                    actions = List.of(myCreateSingleCommentAction, myCreateReviewAction);
                }
            }
            else if (changes != null) {
                actions = List.of(createReviewCommentAction(review.id(), changes.getChanges().contains(myPosition.get().change())));
            }
            else {
                actions = List.of();
            }
        }
        else {
            actions = List.of();
        }
        mySubmitActions.set(actions);

        List<CodeReviewCommentSubmitAction> commentActions = new ArrayList<>(actions.size());
        for (SubmitAction action : actions) {
            commentActions.add(new CodeReviewCommentSubmitAction(getSubmitActionText(action), action));
        }
        myCommentSubmitActions.set(List.copyOf(commentActions));
    }

    private static LocalizeValue getSubmitActionText(SubmitAction action) {
        return switch (action) {
            case SubmitAction.CreateReview it -> GithubLocalize.pullRequestReviewEditorStartReview();
            case SubmitAction.CreateReviewComment it -> GithubLocalize.pullRequestReviewEditorAddReviewComment();
            case SubmitAction.CreateSingleComment it -> GithubLocalize.pullRequestReviewEditorAddSingleComment();
        };
    }

    private static <T extends @Nullable Object> ComputedResult<T> loadingIfNull(@Nullable ComputedResult<T> result) {
        return result == null ? ComputedResult.loading() : result;
    }

    @Override
    public ObservableValue<List<SubmitAction>> getSubmitActions() {
        return mySubmitActions;
    }

    @Override
    public ObservableValue<List<CodeReviewCommentSubmitAction>> getCommentSubmitActions() {
        return myCommentSubmitActions;
    }

    @Override
    public void submit() {
        List<SubmitAction> actions = mySubmitActions.get();
        if (!actions.isEmpty()) {
            actions.get(0).run();
        }
    }

    @Override
    public Date getCreatedAt() {
        return myCreatedAt;
    }

    @Override
    public void cancel() {
        myOnCancel.accept(myPosition.get());
    }

    private SubmitAction.CreateReviewComment createReviewCommentAction(String reviewId, boolean isCumulative) {
        return () -> submit(text -> {
            GHPRReviewCommentPosition position = myPosition.get();
            String filePath = VcsFileUtil.relativePath(myRepository.getRoot(), RefComparisonChangeUtil.getFilePath(position.change()));
            GHPRReviewCommentLocation location = position.location();
            int line = location.lineIdx() + 1;
            if (isCumulative) {
                Side startSide;
                int startLine;
                if (location instanceof GHPRReviewCommentLocation.MultiLine multiLine) {
                    startSide = multiLine.startSide();
                    startLine = multiLine.startLineIdx() + 1;
                }
                else {
                    startSide = location.side();
                    startLine = line;
                }
                return myReviewDataProvider.createThread(reviewId, text, line, location.side(), startLine, startSide, filePath)
                    .thenRun(this::cancel);
            }
            else {
                String commitSha = position.change().revisionNumberAfter().asString();
                return myReviewDataProvider.addComment(reviewId, text, commitSha, filePath, location).thenRun(this::cancel);
            }
        });
    }

    private GHPullRequestDraftReviewThread createThreadDTO(String body) {
        GHPRReviewCommentPosition position = myPosition.get();
        String filePath = VcsFileUtil.relativePath(myRepository.getRoot(), RefComparisonChangeUtil.getFilePath(position.change()));
        GHPRReviewCommentLocation location = position.location();
        if (location instanceof GHPRReviewCommentLocation.MultiLine multiLine) {
            return new GHPullRequestDraftReviewThread(body, multiLine.lineIdx() + 1, filePath, multiLine.side(),
                multiLine.startLineIdx() + 1, multiLine.startSide());
        }
        return new GHPullRequestDraftReviewThread(body, location.lineIdx() + 1, filePath, location.side(), null, null);
    }

    public void destroy() {
        Disposer.dispose(this);
    }

    @Override
    public String getTrackingId() {
        return myTrackingId;
    }
}
