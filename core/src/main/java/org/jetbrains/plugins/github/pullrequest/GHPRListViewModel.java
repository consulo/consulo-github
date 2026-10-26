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
package org.jetbrains.plugins.github.pullrequest;

import consulo.accountManagement.Account;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.review.icon.IconsProvider;
import consulo.review.icon.ReviewIconGroup;
import consulo.review.list.CodeReviewListItemsViewModel;
import consulo.review.list.CommentsCounter;
import consulo.review.list.NamedCollection;
import consulo.review.list.ReviewListItemPresentation;
import consulo.review.list.ReviewListItemStatus;
import consulo.review.list.SimpleReviewListItemPresentation;
import consulo.review.list.SimpleTagPresentation;
import consulo.review.list.SimpleUserPresentation;
import consulo.review.list.TagPresentation;
import consulo.review.list.UserPresentation;
import consulo.review.util.AsyncIncrementalListComputer;
import consulo.review.util.ComputedResult;
import consulo.review.util.IncrementallyComputedValue;
import consulo.ui.color.RGBColor;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeableState;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewRequest;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRPersistentInteractionState;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRReviewerState;
import org.jetbrains.plugins.github.pullrequest.ui.GHReviewersUtils;
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRListPersistentSearchHistory;
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRListSearchValue;
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRSearchHistoryModel;
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRSearchPanelViewModel;
import org.jetbrains.plugins.github.ui.util.GHUIUtil;
import org.jetbrains.plugins.github.util.GithubSettings;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GHPRListViewModel implements CodeReviewListItemsViewModel<GHPullRequestShort> {
    /**
     * Order in which reviewers are grouped in the PR list popup.
     */
    private static final List<GHPRReviewerState> REVIEWER_DISPLAY_ORDER = List.of(
        GHPRReviewerState.APPROVED,
        GHPRReviewerState.CHANGES_REQUESTED,
        GHPRReviewerState.COMMENTED,
        GHPRReviewerState.RE_REQUESTED,
        GHPRReviewerState.NEEDS_REVIEW
    );

    private static final int AVATAR_SIZE = 20;

    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GHPRPersistentInteractionState myInteractionStateService;
    private final GHPRRepositoryDataService myRepositoryDataService;
    private final GithubSettings mySettings;
    private final Consumer<GHPullRequestShort> myOpener;

    private final Account myAccount;
    private final GHUser myCurrentUser;
    private final GHUser myGhostUser;
    private final String myRepository;

    private final List<Runnable> myReloadListeners = new CopyOnWriteArrayList<>();

    private final GHPRSearchPanelViewModel mySearchVm;

    private final MutableObservableValue<@Nullable AsyncIncrementalListComputer<GHPullRequestShort>> myLoaderState = MutableObservableValue.of(null);
    private @Nullable Disposable myLoaderCs;
    private @Nullable Runnable myLoaderUnsubscribe;

    private final MutableObservableValue<List<GHPullRequestShort>> myLoadedData = MutableObservableValue.of(List.of());
    private final MutableObservableValue<Boolean> myIsLoading = MutableObservableValue.of(false);
    private final MutableObservableValue<@Nullable Throwable> myError = MutableObservableValue.of(null);
    private final MutableObservableValue<ComputedResult<List<GHPullRequestShort>>> myItems = MutableObservableValue.of(ComputedResult.loading());

    /**
     * Whether the list view contains any PRs with updates, or {@code null} if it's unknown (because of the setting).
     */
    private final MutableObservableValue<@Nullable Boolean> myHasUpdatesState = MutableObservableValue.of(false);

    private final List<Runnable> myFocusRequestListeners = new CopyOnWriteArrayList<>();

    private final IconsProvider<String> myAvatarIconsProvider;

    public GHPRListViewModel(Project project, Disposable parentCs, GHPRDataContext dataContext, Consumer<GHPullRequestShort> opener) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myDataContext = dataContext;
        myInteractionStateService = GHPRPersistentInteractionState.getInstance(project);
        myRepositoryDataService = dataContext.getRepositoryDataService();
        mySettings = GithubSettings.getInstance();
        myOpener = opener;

        myAccount = dataContext.getSecurityService().getAccount();
        myCurrentUser = dataContext.getSecurityService().getCurrentUser();
        myGhostUser = dataContext.getSecurityService().getGhostUser();
        myRepository = myRepositoryDataService.getRepositoryCoordinates().getRepositoryPath().getRepository();
        myAvatarIconsProvider = dataContext.getAvatarIconsProvider();

        GHPRSearchHistoryModel searchHistoryModel = new GHPRSearchHistoryModel(GHPRListPersistentSearchHistory.getInstance(project));
        mySearchVm = new GHPRSearchPanelViewModel(myCs, project, myRepositoryDataService, dataContext.getSecurityService(), searchHistoryModel);

        Disposer.register(myCs, mySearchVm.getSearchState().addListener(search -> restartLoader())::run);
        myInteractionStateService.addUpdateListener(myCs, () -> checkIsSeenMarkers(myLoadedData.get()));
        Disposer.register(myCs, () -> {
            synchronized (this) {
                if (myLoaderUnsubscribe != null) {
                    myLoaderUnsubscribe.run();
                }
                myLoaderUnsubscribe = null;
                myLoaderCs = null;
            }
        });
        restartLoader();
    }

    private void restartLoader() {
        GHPRListSearchValue search = mySearchVm.getSearchState().get();
        AsyncIncrementalListComputer<GHPullRequestShort> loader;
        synchronized (this) {
            if (myLoaderUnsubscribe != null) {
                myLoaderUnsubscribe.run();
            }
            if (myLoaderCs != null) {
                Disposer.dispose(myLoaderCs);
            }
            Disposable loaderCs = Disposable.newDisposable("GHPRListViewModel loader");
            if (!Disposer.tryRegister(myCs, loaderCs)) {
                return;
            }
            myLoaderCs = loaderCs;
            loader = AsyncIncrementalListComputer.createIn(loaderCs, myDataContext.getListLoader(search.toQuery()));
            myLoaderUnsubscribe = loader.getState().addListener(this::onLoaderState);
        }
        myLoaderState.set(loader);
        onLoaderState(loader.getState().get());
        loader.requestMore();
    }

    private void onLoaderState(IncrementallyComputedValue<List<GHPullRequestShort>> state) {
        List<GHPullRequestShort> value = state.getValueOrNull();
        boolean loading = state.isLoading();
        Exception error = state.getExceptionOrNull();
        myLoadedData.set(value == null ? List.of() : value);
        myIsLoading.set(loading);
        myError.set(error);
        if (value != null) {
            myItems.set(ComputedResult.success(value));
        }
        else if (error != null) {
            myItems.set(ComputedResult.failure(error));
        }
        else {
            myItems.set(ComputedResult.loading());
        }
        if (!loading) {
            checkIsSeenMarkers(value == null ? List.of() : value);
        }
    }

    public Account getAccount() {
        return myAccount;
    }

    public String getRepository() {
        return myRepository;
    }

    public GHPRSearchPanelViewModel getSearchVm() {
        return mySearchVm;
    }

    public ObservableValue<List<GHPullRequestShort>> getLoadedData() {
        return myLoadedData;
    }

    public ObservableValue<Boolean> isLoading() {
        return myIsLoading;
    }

    public ObservableValue<@Nullable Throwable> getError() {
        return myError;
    }

    public ObservableValue<@Nullable Boolean> getHasUpdates() {
        return myHasUpdatesState;
    }

    public IconsProvider<String> getAvatarIconsProvider() {
        return myAvatarIconsProvider;
    }

    public void addReloadListener(Disposable parentDisposable, Runnable listener) {
        myReloadListeners.add(listener);
        Disposer.register(parentDisposable, () -> myReloadListeners.remove(listener));
    }

    public void addFocusRequestListener(Disposable parentDisposable, Runnable listener) {
        myFocusRequestListeners.add(listener);
        Disposer.register(parentDisposable, () -> myFocusRequestListeners.remove(listener));
    }

    private void checkIsSeenMarkers(List<GHPullRequestShort> items) {
        if (mySettings.isSeenMarkersEnabled()) {
            myHasUpdatesState.set(items.stream().anyMatch(it -> !myInteractionStateService.isSeen(it, myCurrentUser)));
        }
        else {
            myHasUpdatesState.set(null);
        }
    }

    @Override
    public ObservableValue<ComputedResult<List<GHPullRequestShort>>> getItems() {
        return myItems;
    }

    @Override
    public void open(GHPullRequestShort item) {
        myOpener.accept(item);
    }

    @Override
    public void refresh() {
        restartLoader();
        myReloadListeners.forEach(Runnable::run);
    }

    public void requestMore() {
        AsyncIncrementalListComputer<GHPullRequestShort> loader = myLoaderState.get();
        if (loader != null) {
            loader.requestMore();
        }
    }

    public void requestFocus() {
        myFocusRequestListeners.forEach(Runnable::run);
    }

    @Override
    public ReviewListItemPresentation getPresentation(GHPullRequestShort pr) {
        boolean isUnreadDotEnabled = mySettings.isSeenMarkersEnabled();
        List<GHPullRequestRequestedReviewer> requestedReviewers = new ArrayList<>();
        for (GHPullRequestReviewRequest request : pr.getReviewRequests()) {
            GHPullRequestRequestedReviewer reviewer = request.getRequestedReviewer();
            if (reviewer != null) {
                requestedReviewers.add(reviewer);
            }
        }
        return new SimpleReviewListItemPresentation(
            pr.getTitle(), "#" + pr.getNumber(), pr.getCreatedAt(),
            createUserPresentation(pr.getAuthor()),
            NamedCollection.create(GithubLocalize.pullRequestLabelsPopup(pr.getLabels().size()),
                pr.getLabels().stream().map(GHPRListViewModel::getLabelPresentation).toList()),
            getMergeableStatus(pr.getMergeable()),
            null,
            getStateText(pr.getState(), pr.isDraft()),
            getAssigneesPresentation(pr.getAssignees()),
            getReviewersPresentation(GHReviewersUtils.getReviewsByReviewers(pr.getAuthor(), pr.getReviews(), requestedReviewers, myGhostUser)),
            new CommentsCounter(pr.getUnresolvedReviewThreadsCount(),
                GithubLocalize.pullRequestUnresolvedComments(pr.getUnresolvedReviewThreadsCount())),
            isUnreadDotEnabled ? myInteractionStateService.isSeen(pr, myCurrentUser) : null
        );
    }

    private static TagPresentation getLabelPresentation(GHLabel label) {
        return new SimpleTagPresentation(LocalizeValue.of(label.getName()), RGBColor.decode("#" + label.getColor()));
    }

    private static @Nullable String getStateText(GHPullRequestState state, boolean isDraft) {
        if (state == GHPullRequestState.OPEN && !isDraft) {
            return null;
        }
        return GHUIUtil.getPullRequestStateText(state, isDraft).get();
    }

    private static @Nullable ReviewListItemStatus getMergeableStatus(GHPullRequestMergeableState mergeableState) {
        if (mergeableState == GHPullRequestMergeableState.CONFLICTING) {
            return new ReviewListItemStatus(ReviewIconGroup.reviewNonmergeable(), GithubLocalize.pullRequestConflictsMergeTooltip());
        }
        return null;
    }

    private @Nullable NamedCollection<UserPresentation> getAssigneesPresentation(List<GHUser> assignees) {
        return NamedCollection.create(GithubLocalize.pullRequestAssigneesPopup(assignees.size()),
            assignees.stream().map(user -> (UserPresentation) createUserPresentation(user, null)).toList());
    }

    private @Nullable NamedCollection<UserPresentation> getReviewersPresentation(Map<GHPullRequestRequestedReviewer, GHPRReviewerState> reviewsByReviewers) {
        List<UserPresentation> presentations = new ArrayList<>();
        for (GHPRReviewerState state : REVIEWER_DISPLAY_ORDER) {
            presentations.addAll(createUserPresentationByFilter(reviewsByReviewers, state));
        }
        return NamedCollection.create(GithubLocalize.pullRequestReviewersPopup(presentations.size()), presentations);
    }

    private @Nullable UserPresentation createUserPresentation(@Nullable GHActor user) {
        if (user == null) {
            return null;
        }
        return new SimpleUserPresentation(user.getLogin(), null, myAvatarIconsProvider.getIcon(user.getAvatarUrl(), AVATAR_SIZE));
    }

    private List<UserPresentation> createUserPresentationByFilter(Map<GHPullRequestRequestedReviewer, GHPRReviewerState> reviewsByReviewers,
                                                                  GHPRReviewerState reviewStateFilter) {
        List<UserPresentation> result = new ArrayList<>();
        reviewsByReviewers.forEach((reviewer, reviewState) -> {
            if (reviewState == reviewStateFilter) {
                result.add(createUserPresentation(reviewer, reviewStateFilter));
            }
        });
        return result;
    }

    private UserPresentation createUserPresentation(GHPullRequestRequestedReviewer user, @Nullable GHPRReviewerState reviewState) {
        return new SimpleUserPresentation(user.getShortName(), user.getName(), myAvatarIconsProvider.getIcon(user.getAvatarUrl(), AVATAR_SIZE));
    }
}
