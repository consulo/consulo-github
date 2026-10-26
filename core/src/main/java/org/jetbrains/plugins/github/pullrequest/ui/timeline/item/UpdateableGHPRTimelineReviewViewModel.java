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

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.async.MappingScopedItemsContainer;
import consulo.review.comment.CodeReviewSubmittableTextViewModelBase;
import consulo.review.comment.CodeReviewTextEditingViewModel;
import consulo.review.list.UserPresentation;
import consulo.review.timeline.CodeReviewCommentViewModel;
import consulo.review.util.ComputedResult;
import consulo.review.util.SingleCoroutineLauncher;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.review.util.ChangesSelection;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewState;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import consulo.github.localize.GithubLocalize;
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineUIUtil;
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class UpdateableGHPRTimelineReviewViewModel implements GHPRTimelineReviewViewModel,
    GHPRTimelineItem.Review,
    CodeReviewCommentViewModel {
    private final Project myProject;
    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;
    private final GHViewModelWithTextCompletion myViewModelWithTextCompletion;
    private final GHPRReviewDataProvider myReviewData;
    private final SingleCoroutineLauncher myTaskLauncher = new SingleCoroutineLauncher();

    private final MutableObservableValue<GHPullRequestReview> myDataState;

    private final String myId;
    private final GHActor myAuthor;
    private final Date myCreatedAt;
    private final GHPullRequestReviewState myState;

    private final MutableObservableValue<List<GHPullRequestReviewThread>> myReviewThreads = MutableObservableValue.of(List.of());
    private final MutableObservableValue<ComputedResult<List<GHPRTimelineThreadViewModel>>> myThreads =
        MutableObservableValue.of(ComputedResult.loading());
    private boolean myThreadsLoaded;

    private final MutableObservableValue<String> myBodyHtml = MutableObservableValue.of("");
    private final MutableObservableValue<String> myBody = MutableObservableValue.of("");

    // GH fails to edit reviews that are initialized with an empty body.
    // bodyHtml will contain <body></body>, so it's less easy to check for emptiness.
    private final ObservableValue<Boolean> myCanEdit;

    private final MutableObservableValue<@Nullable CodeReviewTextEditingViewModel> myEditVm = MutableObservableValue.of(null);

    private final List<Consumer<ChangesSelection>> myShowDiffRequestListeners = new CopyOnWriteArrayList<>();

    public UpdateableGHPRTimelineReviewViewModel(Project project,
                                                 Disposable parentCs,
                                                 GHPRDataContext dataContext,
                                                 GHPRDataProvider dataProvider,
                                                 GHViewModelWithTextCompletion viewModelWithTextCompletion,
                                                 GHPullRequestReview initialData) {
        myProject = project;
        myCs = Disposable.newDisposable("GitHub Pull Request Timeline Review View Model");
        Disposer.register(parentCs, myCs);
        Disposer.register(myCs, myTaskLauncher::cancel);
        myDataContext = dataContext;
        myDataProvider = dataProvider;
        myViewModelWithTextCompletion = viewModelWithTextCompletion;
        myReviewData = dataProvider.getReviewData();

        myDataState = MutableObservableValue.of(initialData);

        myId = initialData.getId();
        GHActor author = initialData.getAuthor();
        myAuthor = author != null ? author : dataContext.getSecurityService().getGhostUser();
        myCreatedAt = initialData.getCreatedAt();
        myState = initialData.getState();

        ObservableValue<List<UpdateableGHPRTimelineThreadViewModel>> threadVms = MappingScopedItemsContainer.mapDataToModel(myCs,
            myReviewThreads,
            GHPullRequestReviewThread::getId,
            this::createThread,
            UpdateableGHPRTimelineThreadViewModel::update);
        ComputationState<List<GHPullRequestReviewThread>> threadsComputation = myReviewData.threadsComputationFlow(myCs);
        Runnable threadsListener = threadsComputation.addListener(result -> onThreadsUpdated(result, threadVms));
        Disposer.register(myCs, threadsListener::run);
        onThreadsUpdated(threadsComputation.get(), threadVms);

        GithubServerPath server = dataContext.getRepositoryDataService().getRepositoryMapping().getRepository().getServerPath();
        updateBody(initialData, server);
        Runnable bodyListener = myDataState.addListener(it -> updateBody(it, server));
        Disposer.register(myCs, bodyListener::run);

        myCanEdit = myDataState.map(it -> !it.getBody().isEmpty() && initialData.getViewerCanUpdate());
    }

    private void onThreadsUpdated(@Nullable ComputedResult<List<GHPullRequestReviewThread>> result,
                                  ObservableValue<List<UpdateableGHPRTimelineThreadViewModel>> threadVms) {
        if (result == null) {
            return;
        }
        if (result.isInProgress()) {
            if (!myThreadsLoaded) {
                myThreads.set(ComputedResult.loading());
            }
            return;
        }
        List<GHPullRequestReviewThread> threads = result.getOrNull();
        if (threads == null) {
            myThreadsLoaded = false;
            Throwable error = result.exceptionOrNull();
            myThreads.set(error != null ? ComputedResult.failure(error) : ComputedResult.loading());
            return;
        }
        List<GHPullRequestReviewThread> reviewThreads = new ArrayList<>();
        for (GHPullRequestReviewThread thread : threads) {
            if (Objects.equals(thread.getReviewId(), myId)) {
                reviewThreads.add(thread);
            }
        }
        myReviewThreads.set(List.copyOf(reviewThreads));
        myThreadsLoaded = true;
        myThreads.set(ComputedResult.success(List.<GHPRTimelineThreadViewModel>copyOf(threadVms.get())));
    }

    private void updateBody(GHPullRequestReview data, GithubServerPath server) {
        String bodyHtml = GHMarkdownToHtmlConverter.convertToHtml(data.getBody(), myProject, server);
        myBodyHtml.set(bodyHtml);
        String stateText = "_" + getStateText(myState).get() + "_";
        myBody.set(bodyHtml.isBlank() ? stateText : stateText + "\n\n" + bodyHtml);
    }

    private static LocalizeValue getStateText(GHPullRequestReviewState state) {
        return switch (state) {
            case APPROVED -> GithubLocalize.pullRequestTimelineApprovedChanges();
            case CHANGES_REQUESTED -> GithubLocalize.pullRequestTimelineRequestedChanges();
            case PENDING -> GithubLocalize.pullRequestTimelineStartedReview();
            case COMMENTED, DISMISSED -> GithubLocalize.pullRequestTimelineReviewed();
        };
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
        return GHPRTimelineUIUtil.createUserPresentation(myAuthor, myDataContext.getAvatarIconsProvider());
    }

    @Override
    public Date getCreatedAt() {
        return myCreatedAt;
    }

    @Override
    public GHPullRequestReviewState getState() {
        return myState;
    }

    @Override
    public ObservableValue<ComputedResult<List<GHPRTimelineThreadViewModel>>> getThreads() {
        return myThreads;
    }

    @Override
    public ObservableValue<String> getBodyHtml() {
        return myBodyHtml;
    }

    @Override
    public ObservableValue<String> getBody() {
        return myBody;
    }

    @Override
    public boolean isOwn() {
        return Objects.equals(myAuthor.getId(), myDataContext.getSecurityService().getCurrentUser().getId());
    }

    @Override
    public ObservableValue<Boolean> isBusy() {
        return myTaskLauncher.getBusy();
    }

    @Override
    public ObservableValue<Boolean> getCanEdit() {
        return myCanEdit;
    }

    @Override
    public boolean canEdit() {
        return myCanEdit.get();
    }

    @Override
    public ObservableValue<@Nullable CodeReviewTextEditingViewModel> getEditVm() {
        return myEditVm;
    }

    public void addShowDiffRequestListener(Disposable parentDisposable, Consumer<ChangesSelection> listener) {
        myShowDiffRequestListeners.add(listener);
        Disposer.register(parentDisposable, () -> myShowDiffRequestListeners.remove(listener));
    }

    private void fireShowDiffRequest(ChangesSelection selection) {
        for (Consumer<ChangesSelection> listener : myShowDiffRequestListeners) {
            listener.accept(selection);
        }
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

    public void update(GHPullRequestReview data) {
        myDataState.set(data);
    }

    private UpdateableGHPRTimelineThreadViewModel createThread(Disposable cs, GHPullRequestReviewThread data) {
        UpdateableGHPRTimelineThreadViewModel threadVm =
            new UpdateableGHPRTimelineThreadViewModel(myProject, cs, myDataContext, myDataProvider, myViewModelWithTextCompletion, data);
        threadVm.addShowDiffRequestListener(cs, this::fireShowDiffRequest);
        return threadVm;
    }

    private class EditViewModel extends CodeReviewSubmittableTextViewModelBase implements CodeReviewTextEditingViewModel {
        EditViewModel(String initialText) {
            super(myProject, myCs, initialText);
        }

        @Override
        public void save() {
            submit(text -> myReviewData.updateReviewBody(myId, text).thenAccept(updated -> {
                myDataState.update(it -> it.withBody(updated));
                UpdateableGHPRTimelineReviewViewModel.this.stopEditing();
            }));
        }

        @Override
        public void stopEditing() {
            UpdateableGHPRTimelineReviewViewModel.this.stopEditing();
        }
    }
}
