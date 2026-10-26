// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.comment.CodeReviewCommentBodyBlock;
import consulo.review.comment.CodeReviewSubmittableTextViewModelBase;
import consulo.review.comment.CodeReviewTextEditingViewModel;
import consulo.review.icon.IconsProvider;
import consulo.review.list.UserPresentation;
import consulo.review.util.SingleCoroutineLauncher;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHIssueComment;
import org.jetbrains.plugins.github.api.data.GHReaction;
import org.jetbrains.plugins.github.api.data.GHReactionContent;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineUIUtil;
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelMentionCompletion;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion;
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionViewModelImpl;
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsComponentFactory;
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsViewModel;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class UpdateableGHPRTimelineCommentViewModel implements GHPRTimelineCommentViewModel, GHPRTimelineItem.Comment {
    private final Project myProject;
    private final Disposable myCs;
    private final SingleCoroutineLauncher myTaskLauncher = new SingleCoroutineLauncher();
    private final GHPRCommentsDataProvider myCommentsData;
    private final GHViewModelWithTextCompletion myViewModelWithTextCompletion;

    private final GHUser myCurrentUser;
    private final GHUser myGhostUser;
    private final IconsProvider<GHReactionContent> myReactionIconsProvider;
    private final IconsProvider<String> myAvatarIconsProvider;

    private final MutableObservableValue<GHIssueComment> myDataState;

    private final String myId;
    private final GHActor myAuthor;
    private final Date myCreatedAt;

    private final MutableObservableValue<String> myBodyHtml = MutableObservableValue.of("");
    private final MutableObservableValue<List<CodeReviewCommentBodyBlock>> myBodyBlocks = MutableObservableValue.of(List.of());

    private final boolean myCanDelete;
    private final boolean myCanEdit;
    private final boolean myCanReact;

    private final MutableObservableValue<@Nullable CodeReviewTextEditingViewModel> myEditVm = MutableObservableValue.of(null);

    private final GHReactionsViewModel myReactionsVm;

    public UpdateableGHPRTimelineCommentViewModel(Project project,
                                                  Disposable parentCs,
                                                  GHPRDataContext dataContext,
                                                  GHPRCommentsDataProvider commentsData,
                                                  GHViewModelWithTextCompletion viewModelWithTextCompletion,
                                                  GHIssueComment initialData) {
        myProject = project;
        myCs = Disposable.newDisposable("GitHub Pull Request Timeline Comment View Model");
        Disposer.register(parentCs, myCs);
        Disposer.register(myCs, myTaskLauncher::cancel);
        myCommentsData = commentsData;
        myViewModelWithTextCompletion = viewModelWithTextCompletion;

        myCurrentUser = dataContext.getSecurityService().getCurrentUser();
        myGhostUser = dataContext.getSecurityService().getGhostUser();
        myReactionIconsProvider = dataContext.getReactionIconsProvider();
        myAvatarIconsProvider = dataContext.getAvatarIconsProvider();

        myDataState = MutableObservableValue.of(initialData);

        myId = initialData.getId();
        GHActor author = initialData.getAuthor();
        myAuthor = author != null ? author : myGhostUser;
        myCreatedAt = initialData.getCreatedAt();

        GithubServerPath server = dataContext.getRepositoryDataService().getRepositoryMapping().getRepository().getServerPath();
        myBodyHtml.set(GHMarkdownToHtmlConverter.convertToHtml(initialData.getBody(), project, server));
        Runnable bodyListener = myDataState.addListener(it ->
            myBodyHtml.set(GHMarkdownToHtmlConverter.convertToHtml(it.getBody(), project, server)));
        Disposer.register(myCs, bodyListener::run);

        myCanDelete = initialData.getViewerCanDelete();
        myCanEdit = initialData.getViewerCanUpdate();
        myCanReact = initialData.getViewerCanReact();

        ObservableValue<List<GHReaction>> reactions = myDataState.map(it -> it.getReactions().getNodes());
        myReactionsVm = new GHReactionViewModelImpl(myCs, myId, reactions, myCurrentUser, dataContext.getReactionsService(),
            myReactionIconsProvider);

        updateBodyBlocks();
        Runnable blocksListener = myBodyHtml.addListener(it -> updateBodyBlocks());
        Disposer.register(myCs, blocksListener::run);
        Runnable reactionsListener = myReactionsVm.getReactionsWithInfo().addListener(it -> updateBodyBlocks());
        Disposer.register(myCs, reactionsListener::run);
    }

    private void updateBodyBlocks() {
        myBodyBlocks.set(GHReactionsComponentFactory.appendReactions(List.of(), myBodyHtml.get(), myReactionsVm, myCanReact));
    }

    @Override
    public void withMentionCompletionModel(Consumer<GHViewModelMentionCompletion> consumer) {
        myViewModelWithTextCompletion.withMentionCompletionModel(consumer);
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
    public ObservableValue<String> getBodyHtml() {
        return myBodyHtml;
    }

    @Override
    public ObservableValue<String> getBody() {
        return myBodyHtml;
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
        String currentText = myDataState.get().getBody();
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
        myTaskLauncher.launch(() -> myCommentsData.deleteComment(myDataState.get().getId()));
    }

    public void update(GHIssueComment data) {
        myDataState.set(data);
    }

    private class EditViewModel extends CodeReviewSubmittableTextViewModelBase implements CodeReviewTextEditingViewModel {
        EditViewModel(String initialText) {
            super(myProject, myCs, initialText);
        }

        @Override
        public void save() {
            submit(text -> myCommentsData.updateComment(myId, text).thenAccept(updated -> {
                myDataState.update(it -> it.withBody(updated));
                UpdateableGHPRTimelineCommentViewModel.this.stopEditing();
            }));
        }

        @Override
        public void stopEditing() {
            UpdateableGHPRTimelineCommentViewModel.this.stopEditing();
        }
    }
}
