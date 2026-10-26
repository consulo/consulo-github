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
package org.jetbrains.plugins.github.pullrequest.ui.timeline;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.async.MappingScopedItemsContainer;
import consulo.review.comment.CodeReviewCommentBodyBlock;
import consulo.review.comment.CodeReviewNewCommentViewModel;
import consulo.review.comment.CodeReviewTextEditingViewModel;
import consulo.review.icon.IconsProvider;
import consulo.review.list.UserPresentation;
import consulo.review.timeline.CodeReviewCommentViewModel;
import consulo.review.timeline.CodeReviewTimelineItem;
import consulo.review.timeline.CodeReviewTimelineViewModel;
import consulo.review.util.AsyncIncrementalListComputer;
import consulo.review.util.ComputedResult;
import consulo.review.util.IncrementallyComputedValue;
import consulo.ui.messageList.AsyncHtmlImageLoader;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.review.util.ChangesSelection;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHIssueComment;
import org.jetbrains.plugins.github.api.data.GHNode;
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent;
import consulo.github.localize.GithubLocalize;
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineUIUtil;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRTimelineDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRPersistentInteractionState;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService;
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler;
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandler;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsFull;
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsComponentFactory;
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.GHPRTimelineItem;
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.GHPRTimelineThreadViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.UpdateableGHPRTimelineCommentViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.UpdateableGHPRTimelineReviewViewModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GHPRTimelineViewModelImpl implements GHPRTimelineViewModel {
    private final Project myProject;
    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;
    private final GHViewModelWithTextCompletion myViewModelWithTextCompletion;

    private final GHPRSecurityService mySecurityService;

    private final GHPRDetailsDataProvider myDetailsData;
    private final GHPRTimelineDataProvider myTimelineData;
    private final GHPRReviewDataProvider myReviewData;
    private final GHPRCommentsDataProvider myCommentsData;

    private final GHPRPersistentInteractionState myInteractionState;

    private final GHUser myGhostUser;
    private final GHUser myCurrentUser;

    private final GHPRDetailsTimelineViewModel myDetailsVm;

    private boolean myLoadedCompletely;
    private volatile @Nullable AsyncIncrementalListComputer<
        org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem> myTimelineLoader;
    private @Nullable Disposable myTimelineLoaderDisposable;

    private final GHLoadingErrorHandler myLoadingErrorHandler;

    private final @Nullable GHPRNewCommentViewModel myCommentVm;

    private final AsyncHtmlImageLoader myHtmlImageLoader;
    private final IconsProvider<String> myAvatarIconsProvider;

    private final MutableObservableValue<IncrementallyComputedValue<List<
        org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem>>> myTimelineLoaderState =
        MutableObservableValue.of(IncrementallyComputedValue.initial());
    private final MutableObservableValue<List<org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem>> myMergedItems =
        MutableObservableValue.of(List.of());
    private final ObservableValue<List<GHPRTimelineItem>> myTimelineItems;

    private final MutableObservableValue<Boolean> myIsLoading = MutableObservableValue.of(false);
    private final MutableObservableValue<@Nullable Throwable> myLoadingError = MutableObservableValue.of(null);

    private final List<Consumer<String>> myShowCommitRequestListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<ChangesSelection>> myShowDiffRequestListeners = new CopyOnWriteArrayList<>();

    private final GHPRTimelineItemComponentFactory myItemComponentFactory;
    private final MutableObservableValue<ComputedResult<List<CodeReviewTimelineItem>>> myItems =
        MutableObservableValue.of(ComputedResult.loading());
    private Map<Object, CodeReviewTimelineItem> myItemsCache = new HashMap<>();
    private @Nullable String myItemsCacheAuthorId;
    private @Nullable Disposable myThreadsListenersDisposable;
    private @Nullable DescriptionItem myDescriptionItem;

    public GHPRTimelineViewModelImpl(Project project,
                                     Disposable parentCs,
                                     GHPRDataContext dataContext,
                                     GHPRDataProvider dataProvider,
                                     GHViewModelWithTextCompletion commentTextCompletionVm) {
        myProject = project;
        myCs = Disposable.newDisposable("GitHub Pull Request Timeline View Model");
        Disposer.register(parentCs, myCs);
        myDataContext = dataContext;
        myDataProvider = dataProvider;
        myViewModelWithTextCompletion = commentTextCompletionVm;

        mySecurityService = dataContext.getSecurityService();

        myDetailsData = dataProvider.getDetailsData();
        myTimelineData = dataProvider.getTimelineData();
        myReviewData = dataProvider.getReviewData();
        myCommentsData = dataProvider.getCommentsData();

        myInteractionState = dataContext.getInteractionState();

        myGhostUser = mySecurityService.getGhostUser();
        myCurrentUser = mySecurityService.getCurrentUser();

        myDetailsVm = new GHPRDetailsTimelineViewModel(project, myCs, dataContext, dataProvider);

        myLoadingErrorHandler =
            new GHApiLoadingErrorHandler(project, mySecurityService.getAccount(), myTimelineData::signalTimelineNeedsReload);

        myCommentVm = mySecurityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.READ)
            ? new GHPRNewCommentViewModel(project, myCs, myCommentsData, myViewModelWithTextCompletion)
            : null;

        myHtmlImageLoader = dataContext.getHtmlImageLoader();
        myAvatarIconsProvider = dataContext.getAvatarIconsProvider();

        myItemComponentFactory = new GHPRTimelineItemComponentFactory(project, this);

        myTimelineItems = MappingScopedItemsContainer.mapDataToModel(myCs,
            myMergedItems,
            GHPRTimelineViewModelImpl::getItemID,
            this::createItemFromDTO,
            GHPRTimelineViewModelImpl::update);

        Runnable itemsListener = myTimelineItems.addListener(it -> updateItems());
        Disposer.register(myCs, itemsListener::run);
        Runnable detailsListener = myDetailsVm.getDetails().addListener(it -> updateItems());
        Disposer.register(myCs, detailsListener::run);
        Disposer.register(myCs, this::disposeTimelineLoader);
        Disposer.register(myCs, () -> {
            Disposable threadsListeners = myThreadsListenersDisposable;
            if (threadsListeners != null) {
                Disposer.dispose(threadsListeners);
            }
        });

        myTimelineData.addTimelineChangeListener(myCs, this::createComputer);
        myTimelineData.addTimelineUpdateListener(myCs, () -> {
            AsyncIncrementalListComputer<?> loader = myTimelineLoader;
            if (loader != null) {
                loader.requestMore();
            }
        });
        createComputer();
        updateItems();
    }

    private synchronized void createComputer() {
        disposeTimelineLoader();
        Disposable loaderDisposable = Disposable.newDisposable("GitHub Pull Request Timeline Loader");
        if (!Disposer.tryRegister(myCs, loaderDisposable)) {
            return;
        }
        AsyncIncrementalListComputer<org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem> loader =
            AsyncIncrementalListComputer.createIn(loaderDisposable, myTimelineData.getTimelineItems(), true);
        myTimelineLoaderDisposable = loaderDisposable;
        myTimelineLoader = loader;
        Runnable stateListener = loader.getState().addListener(state -> onTimelineLoaderState(loader, state));
        Disposer.register(loaderDisposable, stateListener::run);
        onTimelineLoaderState(loader, loader.getState().get());
        loader.requestMore();
    }

    private synchronized void disposeTimelineLoader() {
        Disposable loaderDisposable = myTimelineLoaderDisposable;
        myTimelineLoaderDisposable = null;
        myTimelineLoader = null;
        if (loaderDisposable != null) {
            Disposer.dispose(loaderDisposable);
        }
    }

    private void onTimelineLoaderState(AsyncIncrementalListComputer<?> loader,
                                       IncrementallyComputedValue<List<
                                           org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem>> state) {
        if (loader != myTimelineLoader) {
            return;
        }
        myTimelineLoaderState.set(state);
        myIsLoading.set(state.isLoading());
        myLoadingError.set(state.getExceptionOrNull());

        // don't rebuild on full reload until the first batch
        // don't rebuild in batches if loaded completely at least once
        if (state.isValueAvailable() && !state.isLoading() && (!myLoadedCompletely || state.isComplete())) {
            myLoadedCompletely = state.isComplete();
            List<org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem> value = state.getValueOrNull();
            GHPRTimelineMergingModel mergingModel = new GHPRTimelineMergingModel();
            mergingModel.add(value != null ? value : List.of());
            myMergedItems.set(getItemsList(mergingModel));
        }

        if (!state.isLoading() && state.isComplete()) {
            // Update the last seen date to the last time a full timeline was loaded
            updateLastSeen(System.currentTimeMillis());
        }

        // load fully, the timeline shows every item at once
        if (!state.isLoading() && !state.isComplete() && state.getExceptionOrNull() == null && state.isValueAvailable()) {
            loader.requestMore();
        }

        updateItems();
    }

    private void updateLastSeen(long lastSeenMillis) {
        GHPRDetailsFull details = myDetailsVm.getDetails().get().getOrNull();
        if (details == null) {
            return;
        }
        myInteractionState.updateStateFor(details.id(), st -> {
            Long lastSeen = st == null ? null : st.getLastSeen();
            return new GHPRPersistentInteractionState.PRState(details.id(), Math.max(lastSeenMillis, lastSeen == null ? 0L : lastSeen));
        });
    }

    private static Object getItemID(org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem data) {
        if (data instanceof GHNode node) {
            return node.getId();
        }
        return data;
    }

    private GHPRTimelineItem createItemFromDTO(Disposable cs,
                                               org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem data) {
        if (data instanceof GHIssueComment comment) {
            return new UpdateableGHPRTimelineCommentViewModel(myProject,
                cs,
                myDataContext,
                myDataProvider.getCommentsData(),
                myViewModelWithTextCompletion,
                comment);
        }
        else if (data instanceof GHPullRequestReview review) {
            UpdateableGHPRTimelineReviewViewModel reviewVm =
                new UpdateableGHPRTimelineReviewViewModel(myProject, cs, myDataContext, myDataProvider, myViewModelWithTextCompletion,
                    review);
            reviewVm.addShowDiffRequestListener(cs, this::fireShowDiffRequest);
            return reviewVm;
        }
        else if (data instanceof GHPullRequestCommitShort commit) {
            return new GHPRTimelineItem.Commits(List.of(commit));
        }
        else if (data instanceof GHPRTimelineGroupedCommits commits) {
            return new GHPRTimelineItem.Commits(List.copyOf(commits.getItems()));
        }
        else if (data instanceof GHPRTimelineEvent event) {
            return new GHPRTimelineItem.Event(event);
        }
        return new GHPRTimelineItem.Unknown("");
    }

    private static void update(GHPRTimelineItem item, org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem data) {
        if (item instanceof UpdateableGHPRTimelineCommentViewModel comment && data instanceof GHIssueComment issueComment) {
            comment.update(issueComment);
        }
    }

    private synchronized void updateItems() {
        Disposable threadsListeners = myThreadsListenersDisposable;
        if (threadsListeners != null) {
            Disposer.dispose(threadsListeners);
        }
        Disposable newThreadsListeners = Disposable.newDisposable("GitHub Pull Request Timeline threads listeners");
        if (!Disposer.tryRegister(myCs, newThreadsListeners)) {
            return;
        }
        myThreadsListenersDisposable = newThreadsListeners;

        List<CodeReviewTimelineItem> result = new ArrayList<>();
        Map<Object, CodeReviewTimelineItem> cache = new HashMap<>();

        ComputedResult<GHPRDetailsFull> detailsResult = myDetailsVm.getDetails().get();
        GHPRDetailsFull details = detailsResult.getOrNull();
        GHActor prAuthor = details == null ? null : details.author();
        String prAuthorId = prAuthor == null ? null : prAuthor.getId();
        if (!Objects.equals(prAuthorId, myItemsCacheAuthorId)) {
            myItemsCache = new HashMap<>();
            myItemsCacheAuthorId = prAuthorId;
        }
        if (details != null) {
            DescriptionItem descriptionItem = myDescriptionItem;
            if (descriptionItem == null) {
                descriptionItem = new DescriptionItem(details);
                myDescriptionItem = descriptionItem;
            }
            result.add(descriptionItem);
        }

        for (GHPRTimelineItem item : myTimelineItems.get()) {
            if (item instanceof UpdateableGHPRTimelineReviewViewModel review) {
                result.add(review);
                Runnable threadsListener = review.getThreads().addListener(it -> updateItems());
                Disposer.register(newThreadsListeners, threadsListener::run);
                List<GHPRTimelineThreadViewModel> threads = review.getThreads().get().getOrNull();
                if (threads != null) {
                    result.addAll(threads);
                }
            }
            else {
                CodeReviewTimelineItem mapped = myItemsCache.get(item);
                if (mapped == null) {
                    mapped = myItemComponentFactory.invoke(item);
                }
                cache.put(item, mapped);
                result.add(mapped);
            }
        }
        myItemsCache = cache;

        IncrementallyComputedValue<?> state = myTimelineLoaderState.get();
        if (state.isValueAvailable() || myLoadedCompletely) {
            myItems.set(ComputedResult.success(List.copyOf(result)));
        }
        else if (state.getExceptionOrNull() != null) {
            myItems.set(ComputedResult.failure(Objects.requireNonNull(state.getExceptionOrNull())));
        }
        else if (detailsResult.exceptionOrNull() != null) {
            myItems.set(ComputedResult.failure(Objects.requireNonNull(detailsResult.exceptionOrNull())));
        }
        else {
            myItems.set(ComputedResult.loading());
        }
    }

    @Override
    public ObservableValue<ComputedResult<List<CodeReviewTimelineItem>>> getItems() {
        return myItems;
    }

    @Override
    public @Nullable CodeReviewNewCommentViewModel getNewCommentVm() {
        return myCommentVm;
    }

    @Override
    public GHUser getGhostUser() {
        return myGhostUser;
    }

    @Override
    public GHUser getCurrentUser() {
        return myCurrentUser;
    }

    @Override
    public GHPRDetailsTimelineViewModel getDetailsVm() {
        return myDetailsVm;
    }

    @Override
    public ObservableValue<List<GHPRTimelineItem>> getTimelineItems() {
        return myTimelineItems;
    }

    @Override
    public ObservableValue<Boolean> isLoading() {
        return myIsLoading;
    }

    @Override
    public ObservableValue<@Nullable Throwable> getLoadingError() {
        return myLoadingError;
    }

    @Override
    public GHLoadingErrorHandler getLoadingErrorHandler() {
        return myLoadingErrorHandler;
    }

    @Override
    public @Nullable GHPRNewCommentViewModel getCommentVm() {
        return myCommentVm;
    }

    @Override
    public AsyncHtmlImageLoader getHtmlImageLoader() {
        return myHtmlImageLoader;
    }

    @Override
    public IconsProvider<String> getAvatarIconsProvider() {
        return myAvatarIconsProvider;
    }

    @Override
    public void requestMore() {
        AsyncIncrementalListComputer<?> loader = myTimelineLoader;
        if (loader != null && !myTimelineLoaderState.get().isComplete()) {
            loader.requestMore();
        }
    }

    @Override
    public void update() {
        AsyncIncrementalListComputer<?> loader = myTimelineLoader;
        if (loader != null) {
            loader.requestMore();
        }
    }

    @Override
    public void updateAll() {
        myDetailsData.signalDetailsNeedReload();
        myDetailsData.signalMergeabilityNeedsReload();
        myTimelineData.signalTimelineNeedsReload();
        myReviewData.signalThreadsNeedReload();
    }

    public void addShowCommitRequestListener(Disposable parentDisposable, Consumer<String> listener) {
        myShowCommitRequestListeners.add(listener);
        Disposer.register(parentDisposable, () -> myShowCommitRequestListeners.remove(listener));
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
    public void showCommit(String oid) {
        for (Consumer<String> listener : myShowCommitRequestListeners) {
            listener.accept(oid);
        }
    }

    @Override
    public void openPullRequestInfoAndTimeline(long number) {
        GHPRConnectedProjectViewModel vm = GHPRProjectViewModel.getInstance(myProject).getConnectedProjectVm().get();
        if (vm != null) {
            vm.openPullRequestInfoAndTimeline(number);
        }
    }

    private static List<org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem> getItemsList(
        GHPRTimelineMergingModel model) {
        List<org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem> result = new ArrayList<>(model.getSize());
        for (int i = 0; i < model.getSize(); i++) {
            result.add(model.getElementAt(i));
        }
        return result;
    }

    private final class DescriptionItem implements CodeReviewCommentViewModel {
        private final GHPRDetailsFull myInitialDetails;
        private final ObservableValue<String> myBody;
        private final MutableObservableValue<List<CodeReviewCommentBodyBlock>> myBodyBlocks = MutableObservableValue.of(List.of());
        private final ObservableValue<@Nullable CodeReviewTextEditingViewModel> myEditVm;

        private DescriptionItem(GHPRDetailsFull initialDetails) {
            myInitialDetails = initialDetails;
            myBody = myDetailsVm.getDetails().map(it -> {
                GHPRDetailsFull details = it.getOrNull();
                String descriptionHtml = details != null ? details.descriptionHtml() : myInitialDetails.descriptionHtml();
                return descriptionHtml == null || descriptionHtml.isBlank()
                    ? "_" + GithubLocalize.pullRequestTimelineNoDescription().get() + "_"
                    : descriptionHtml;
            });
            myEditVm = myDetailsVm.getDescriptionEditVm().map(it -> it);
            updateBodyBlocks();
            Runnable bodyListener = myBody.addListener(it -> updateBodyBlocks());
            Disposer.register(myCs, bodyListener::run);
            Runnable reactionsListener = myDetailsVm.getReactionsVm().getReactionsWithInfo().addListener(it -> updateBodyBlocks());
            Disposer.register(myCs, reactionsListener::run);
        }

        private void updateBodyBlocks() {
            myBodyBlocks.set(GHReactionsComponentFactory.appendReactions(List.of(),
                myBody.get(),
                myDetailsVm.getReactionsVm(),
                myInitialDetails.canReactDescription()));
        }

        @Override
        public String getId() {
            return myInitialDetails.id().id();
        }

        @Override
        public UserPresentation getAuthor() {
            return GHPRTimelineUIUtil.createUserPresentation(myInitialDetails.author(), myAvatarIconsProvider);
        }

        @Override
        public Date getCreatedAt() {
            return myInitialDetails.createdAt();
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
            return Objects.equals(myInitialDetails.author().getId(), myCurrentUser.getId());
        }

        @Override
        public boolean canEdit() {
            return myInitialDetails.canEditDescription();
        }

        @Override
        public ObservableValue<@Nullable CodeReviewTextEditingViewModel> getEditVm() {
            return myEditVm;
        }

        @Override
        public void editBody() {
            myDetailsVm.editDescription();
        }
    }
}
