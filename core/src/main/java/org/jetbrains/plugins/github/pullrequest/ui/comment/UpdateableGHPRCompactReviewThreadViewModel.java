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

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.async.MappingScopedItemsContainer;
import consulo.review.comment.CodeReviewNewCommentViewModel;
import consulo.review.comment.CodeReviewSubmittableTextViewModelBase;
import consulo.review.icon.IconsProvider;
import consulo.review.timeline.CodeReviewCommentViewModel;
import consulo.review.timeline.thread.RepliesStateData;
import consulo.review.util.FutureUtil;
import consulo.review.util.SingleCoroutineLauncher;
import consulo.ui.messageList.AsyncHtmlImageLoader;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineUIUtil;
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRNewThreadCommentViewModel;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class UpdateableGHPRCompactReviewThreadViewModel implements GHPRCompactReviewThreadViewModel {
    private static final Logger LOG = Logger.getInstance(UpdateableGHPRCompactReviewThreadViewModel.class);

    private final Project myProject;
    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;
    private final GHViewModelWithTextCompletion myViewModelWithTextCompletion;
    private final GHPRReviewDataProvider myReviewData;
    private final SingleCoroutineLauncher myTaskLauncher = new SingleCoroutineLauncher();

    private final MutableObservableValue<GHPullRequestReviewThread> myDataState;

    private final String myId;

    private final IconsProvider<String> myAvatarIconsProvider;

    private final MutableObservableValue<Boolean> myRepliesFolded;

    private final ObservableValue<Boolean> myCanCreateReplies;
    private final MutableObservableValue<Boolean> myIsWritingReply = MutableObservableValue.of(false);
    private final GHPRNewThreadCommentViewModel myNewReplyVm;

    private final ObservableValue<Boolean> myCanChangeResolvedState;
    private final ObservableValue<Boolean> myIsResolved;

    private final ObservableValue<RepliesStateData> myRepliesState;

    // have to do it LAST, bc comment VMs depend on thread fields
    // so they have to be initialized first
    private final ObservableValue<List<UpdateableGHPRReviewThreadCommentViewModel>> myCommentsVms;
    private final MutableObservableValue<List<CommentItem>> myComments = MutableObservableValue.of(List.of());
    private final MutableObservableValue<List<CodeReviewCommentViewModel>> myCommentViewModels = MutableObservableValue.of(List.of());

    private final List<Runnable> myFocusRequestListeners = new CopyOnWriteArrayList<>();

    public UpdateableGHPRCompactReviewThreadViewModel(Project project,
                                                      Disposable parentCs,
                                                      GHPRDataContext dataContext,
                                                      GHPRDataProvider dataProvider,
                                                      GHPullRequestReviewThread initialData,
                                                      GHViewModelWithTextCompletion viewModelWithTextCompletion) {
        myProject = project;
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        Disposer.register(myCs, myTaskLauncher::cancel);
        myDataContext = dataContext;
        myDataProvider = dataProvider;
        myViewModelWithTextCompletion = viewModelWithTextCompletion;
        myReviewData = dataProvider.getReviewData();

        myDataState = MutableObservableValue.of(initialData);
        myId = initialData.getId();
        myAvatarIconsProvider = dataContext.getAvatarIconsProvider();

        myRepliesFolded = MutableObservableValue.of(initialData.getComments().size() > 3);

        myCanCreateReplies = myDataState.map(GHPullRequestReviewThread::getViewerCanReply);
        myNewReplyVm = new ReplyViewModel();

        myCanChangeResolvedState = myDataState.map(it -> it.getViewerCanResolve() || it.getViewerCanUnresolve());
        myIsResolved = myDataState.map(GHPullRequestReviewThread::isResolved);

        myRepliesState = myDataState.map(it -> GHPRTimelineUIUtil.createRepliesState(it.getComments(), myAvatarIconsProvider));

        ObservableValue<List<UpdateableGHPRReviewThreadCommentViewModel.IndexedComment>> indexedComments =
            myDataState.map(UpdateableGHPRReviewThreadCommentViewModel.IndexedComment::withIndex);
        myCommentsVms = MappingScopedItemsContainer.mapDataToModel(myCs,
            indexedComments,
            it -> it.value().getId(),
            this::createComment,
            UpdateableGHPRReviewThreadCommentViewModel::update);

        updateComments();
        Runnable commentsListener = myCommentsVms.addListener(it -> updateComments());
        Disposer.register(myCs, commentsListener::run);
        Runnable foldedListener = myRepliesFolded.addListener(it -> updateComments());
        Disposer.register(myCs, foldedListener::run);
    }


    private void updateComments() {
        List<UpdateableGHPRReviewThreadCommentViewModel> comments = myCommentsVms.get();
        boolean folded = myRepliesFolded.get();
        List<CommentItem> items = new ArrayList<>();
        if (!folded || comments.size() <= 3) {
            for (UpdateableGHPRReviewThreadCommentViewModel comment : comments) {
                items.add(new CommentItem.Comment(comment));
            }
        }
        else {
            items.add(new CommentItem.Comment(comments.get(0)));
            items.add(new CommentItem.Expander(comments.size() - 2, () -> myRepliesFolded.set(false)));
            items.add(new CommentItem.Comment(comments.get(comments.size() - 1)));
        }
        myComments.set(List.copyOf(items));
        myCommentViewModels.set(List.copyOf(comments));
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
    public IconsProvider<String> getAvatarIconsProvider() {
        return myAvatarIconsProvider;
    }

    @Override
    public ObservableValue<List<CommentItem>> getCommentItems() {
        return myComments;
    }

    @Override
    public ObservableValue<List<CodeReviewCommentViewModel>> getComments() {
        return myCommentViewModels;
    }

    @Override
    public ObservableValue<Boolean> getCanCreateReplies() {
        return myCanCreateReplies;
    }

    @Override
    public ObservableValue<Boolean> isWritingReply() {
        return myIsWritingReply;
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
    public ObservableValue<Boolean> getCanChangeResolvedState() {
        return myCanChangeResolvedState;
    }

    @Override
    public ObservableValue<Boolean> isResolved() {
        return myIsResolved;
    }

    @Override
    public ObservableValue<RepliesStateData> getRepliesState() {
        return myRepliesState;
    }

    @Override
    public ObservableValue<Boolean> getRepliesFolded() {
        return myRepliesFolded;
    }

    @Override
    public void unfoldReplies() {
        myRepliesFolded.set(false);
    }

    @Override
    public String getLocation() {
        return myDataState.get().getPath();
    }

    @Override
    public AsyncHtmlImageLoader getHtmlImageLoader() {
        return myDataContext.getHtmlImageLoader();
    }

    @Override
    public void addFocusRequestListener(Disposable parentDisposable, Runnable listener) {
        myFocusRequestListeners.add(listener);
        Disposer.register(parentDisposable, () -> myFocusRequestListeners.remove(listener));
    }

    @Override
    public void requestFocus() {
        for (Runnable listener : myFocusRequestListeners) {
            listener.run();
        }
    }

    @Override
    public void startWritingReply() {
        myIsWritingReply.set(true);
        myNewReplyVm.requestFocus();
    }

    @Override
    public void stopWritingReply() {
        myIsWritingReply.set(false);
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
                return null;
            });
        });
    }

    public void update(GHPullRequestReviewThread data) {
        myDataState.set(data);
    }

    private UpdateableGHPRReviewThreadCommentViewModel createComment(Disposable cs,
                                                                     UpdateableGHPRReviewThreadCommentViewModel.IndexedComment comment) {
        return new UpdateableGHPRReviewThreadCommentViewModel(myProject, cs, myDataContext, myDataProvider,
            myViewModelWithTextCompletion, comment,
            myId, myIsResolved);
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
