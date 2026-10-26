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
import consulo.project.Project;
import consulo.review.comment.CodeReviewCommentBodyBlock;
import consulo.review.comment.CodeReviewSubmittableTextViewModelBase;
import consulo.review.comment.CodeReviewTextEditingViewModel;
import consulo.review.icon.IconsProvider;
import consulo.review.list.UserPresentation;
import consulo.review.util.SingleCoroutineLauncher;
import consulo.ui.messageList.AsyncHtmlImageLoader;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHReaction;
import org.jetbrains.plugins.github.api.data.GHReactionContent;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewCommentState;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineUIUtil;
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewCommentBodyViewModel;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionViewModelImpl;
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsComponentFactory;
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsViewModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class UpdateableGHPRReviewThreadCommentViewModel implements GHPRReviewThreadCommentViewModel {
    public record IndexedComment(int index, GHPullRequestReviewComment value) {
        public static List<IndexedComment> withIndex(GHPullRequestReviewThread thread) {
            List<GHPullRequestReviewComment> comments = thread.getComments();
            List<IndexedComment> result = new ArrayList<>(comments.size());
            for (int i = 0; i < comments.size(); i++) {
                result.add(new IndexedComment(i, comments.get(i)));
            }
            return result;
        }
    }

    private final Project myProject;
    private final Disposable myCs;
    private final GHPRReviewDataProvider myReviewData;
    private final SingleCoroutineLauncher myTaskLauncher = new SingleCoroutineLauncher();
    private final GHViewModelWithTextCompletion myViewModelWithTextCompletion;

    private final GHUser myCurrentUser;
    private final IconsProvider<String> myAvatarIconsProvider;
    private final IconsProvider<GHReactionContent> myReactionIconsProvider;
    private final AsyncHtmlImageLoader myHtmlImageLoader;

    private final MutableObservableValue<IndexedComment> myDataState;

    private final String myId;
    private final GHActor myAuthor;
    private final Date myCreatedAt;

    private final ObservableValue<Boolean> myIsPending;
    private final MutableObservableValue<Boolean> myIsFirstInResolvedThread;

    private final GHPRReviewCommentBodyViewModel myBodyVm;
    private final ObservableValue<String> myBody;
    private final MutableObservableValue<List<CodeReviewCommentBodyBlock>> myBodyBlocks = MutableObservableValue.of(List.of());

    private final boolean myCanDelete;
    private final boolean myCanEdit;
    private final boolean myCanReact;

    private final MutableObservableValue<@Nullable CodeReviewTextEditingViewModel> myEditVm = MutableObservableValue.of(null);

    private final GHReactionsViewModel myReactionsVm;

    public UpdateableGHPRReviewThreadCommentViewModel(Project project,
                                                      Disposable parentCs,
                                                      GHPRDataContext dataContext,
                                                      GHPRDataProvider dataProvider,
                                                      GHViewModelWithTextCompletion viewModelWithTextCompletion,
                                                      IndexedComment initialDataWithIndex,
                                                      String threadId,
                                                      ObservableValue<Boolean> threadIsResolvedFlow) {
        myProject = project;
        myCs = Disposable.newDisposable("GitHub Pull Request Thread Comment View Model");
        Disposer.register(parentCs, myCs);
        Disposer.register(myCs, myTaskLauncher::cancel);
        myReviewData = dataProvider.getReviewData();
        myViewModelWithTextCompletion = viewModelWithTextCompletion;

        myCurrentUser = dataContext.getSecurityService().getCurrentUser();
        myAvatarIconsProvider = dataContext.getAvatarIconsProvider();
        myReactionIconsProvider = dataContext.getReactionIconsProvider();
        myHtmlImageLoader = dataContext.getHtmlImageLoader();

        GHPullRequestReviewComment initialData = initialDataWithIndex.value();
        myDataState = MutableObservableValue.of(initialDataWithIndex);

        myId = initialData.getId();
        GHActor author = initialData.getAuthor();
        myAuthor = author != null ? author : dataContext.getSecurityService().getGhostUser();
        myCreatedAt = initialData.getCreatedAt();

        myIsPending = myDataState.map(it -> it.value().getState() == GHPullRequestReviewCommentState.PENDING);
        myIsFirstInResolvedThread = MutableObservableValue.of(initialDataWithIndex.index() == 0 && threadIsResolvedFlow.get());
        Runnable resolvedListener = threadIsResolvedFlow.addListener(resolved ->
            myIsFirstInResolvedThread.set(myDataState.get().index() == 0 && resolved));
        Disposer.register(myCs, resolvedListener::run);
        Runnable indexListener = myDataState.addListener(it ->
            myIsFirstInResolvedThread.set(it.index() == 0 && threadIsResolvedFlow.get()));
        Disposer.register(myCs, indexListener::run);

        myBodyVm = new GHPRReviewCommentBodyViewModel(myCs, project, dataContext, dataProvider, threadId, myId);
        myBody = myDataState.map(it -> it.value().getBody());

        myCanDelete = initialData.getViewerCanDelete();
        myCanEdit = initialData.getViewerCanUpdate();
        myCanReact = initialData.getViewerCanReact();

        ObservableValue<List<GHReaction>> reactions = myDataState.map(it -> it.value().getReactions().getNodes());
        myReactionsVm = new GHReactionViewModelImpl(myCs, myId, reactions, myCurrentUser, dataContext.getReactionsService(),
            myReactionIconsProvider);

        updateBodyBlocks();
        Runnable blocksListener = myBodyVm.getBlocks().addListener(it -> updateBodyBlocks());
        Disposer.register(myCs, blocksListener::run);
        Runnable bodyListener = myBody.addListener(it -> updateBodyBlocks());
        Disposer.register(myCs, bodyListener::run);
        Runnable reactionsListener = myReactionsVm.getReactionsWithInfo().addListener(it -> updateBodyBlocks());
        Disposer.register(myCs, reactionsListener::run);
    }

    private void updateBodyBlocks() {
        myBodyBlocks.set(GHReactionsComponentFactory.appendReactions(myBodyVm.getBlocks().get(), myBody.get(), myReactionsVm, myCanReact));
    }

    @Override
    public void withMentionCompletionModel(Consumer<GHViewModelMentionCompletion> consumer) {
        myViewModelWithTextCompletion.withMentionCompletionModel(consumer);
    }

    @Override
    public IconsProvider<String> getAvatarIconsProvider() {
        return myAvatarIconsProvider;
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
    public UserPresentation getAuthor() {
        return GHPRTimelineUIUtil.createUserPresentation(myAuthor, myAvatarIconsProvider);
    }

    @Override
    public Date getCreatedAt() {
        return myCreatedAt;
    }

    @Override
    public ObservableValue<String> getBody() {
        return myBody;
    }

    @Override
    public ObservableValue<List<CodeReviewCommentBodyBlock>> getBodyBlocks() {
        return myBodyBlocks;
    }

    @Override
    public boolean isOwn() {
        return Objects.equals(myAuthor.getId(), myCurrentUser.getId());
    }

    @Override
    public ObservableValue<Boolean> isPending() {
        return myIsPending;
    }

    @Override
    public ObservableValue<Boolean> isFirstInResolvedThread() {
        return myIsFirstInResolvedThread;
    }

    @Override
    public GHPRReviewCommentBodyViewModel getBodyVm() {
        return myBodyVm;
    }

    public AsyncHtmlImageLoader getHtmlImageLoader() {
        return myHtmlImageLoader;
    }

    @Override
    public ObservableValue<Boolean> isBusy() {
        return myTaskLauncher.getBusy();
    }

    @Override
    public boolean canDelete() {
        return myCanDelete;
    }

    @Override
    public boolean canEdit() {
        return myCanEdit;
    }

    @Override
    public boolean canReact() {
        return myCanReact;
    }

    @Override
    public ObservableValue<@Nullable CodeReviewTextEditingViewModel> getEditVm() {
        return myEditVm;
    }

    @Override
    public GHReactionsViewModel getReactionsVm() {
        return myReactionsVm;
    }

    @Override
    public void editBody() {
        String currentText = myDataState.get().value().getBody();
        CodeReviewTextEditingViewModel editVm = myEditVm.get();
        if (editVm == null) {
            editVm = new EditViewModel(currentText);
            myEditVm.set(editVm);
        }
        editVm.requestFocus();
    }

    private void stopEditing() {
        CodeReviewTextEditingViewModel editVm = myEditVm.getAndUpdate(it -> null);
        if (editVm instanceof EditViewModel vm) {
            Disposer.dispose(vm);
        }
    }

    @Override
    public void delete() {
        myTaskLauncher.launch(() -> myReviewData.deleteComment(myId));
    }

    public void update(IndexedComment data) {
        myDataState.set(data);
    }

    private class EditViewModel extends CodeReviewSubmittableTextViewModelBase implements CodeReviewTextEditingViewModel {
        EditViewModel(String initialText) {
            super(myProject, myCs, initialText);
        }

        @Override
        public void save() {
            submit(text -> myReviewData.updateComment(myId, text).thenAccept(updated -> {
                myDataState.update(it -> new IndexedComment(it.index(), updated));
                UpdateableGHPRReviewThreadCommentViewModel.this.stopEditing();
            }));
        }

        @Override
        public void stopEditing() {
            UpdateableGHPRReviewThreadCommentViewModel.this.stopEditing();
        }
    }
}
