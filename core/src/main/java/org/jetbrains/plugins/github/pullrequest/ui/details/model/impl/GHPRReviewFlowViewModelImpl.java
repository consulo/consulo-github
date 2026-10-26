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
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.details.data.ReviewRole;
import consulo.review.details.data.ReviewState;
import consulo.review.list.search.ChooserPopupUtil;
import consulo.review.list.search.PopupConfig;
import consulo.review.list.search.ShowDirection;
import consulo.review.localize.ReviewLocalize;
import consulo.review.util.CollectionDelta;
import consulo.review.util.ComputedResult;
import consulo.review.util.FutureUtil;
import consulo.review.util.IncrementallyComputedValue;
import consulo.review.util.SingleCoroutineLauncher;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.RelativePoint2D;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.ex.dialog.DialogValue;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.review.action.ReviewMergeCommitMessageDialog;
import consulo.versionControlSystem.review.commit.CommitsUtil;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHCommit;
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewRequest;
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState;
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRChangesDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataServiceExtensions;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRReviewerState;
import org.jetbrains.plugins.github.pullrequest.ui.GHReviewersUtils;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.RepositoryRestrictions;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRSubmitReviewViewModel;
import org.jetbrains.plugins.github.ui.util.GHUIUtil;
import consulo.review.icon.IconsProvider;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

public class GHPRReviewFlowViewModelImpl implements GHPRReviewFlowViewModel {
    private static final Logger LOG = Logger.getInstance(GHPRReviewFlowViewModel.class);

    private final Disposable myCs;
    private final Project myProject;
    private final ObservableValue<GHPullRequest> myDetailsState;
    private final GHPRRepositoryDataService myRepositoryDataService;
    private final GHPRSecurityService mySecurityService;
    private final IconsProvider<String> myAvatarIconsProvider;
    private final GHPRDetailsDataProvider myDetailsData;
    private final GHPRChangesDataProvider myChangesData;
    private final GHPRReviewViewModelHelper myReviewVmHelper;

    private final SingleCoroutineLauncher myTaskLauncher = new SingleCoroutineLauncher();

    private final GHUser myCurrentUser;
    private final GHUser myGhostUser;

    // TODO: handle error
    private final MutableObservableValue<@Nullable GHPRMergeabilityState> myMergeabilityState = MutableObservableValue.of(null);

    private final ObservableValue<List<GHPullRequestRequestedReviewer>> myRequestedReviewers;
    private final ObservableValue<Map<GHPullRequestRequestedReviewer, GHPRReviewerState>> myReviewerReviewStates;
    private final ObservableValue<Map<GHPullRequestRequestedReviewer, ReviewState>> myReviewerReviews;
    private final ObservableValue<@Nullable GHPRReviewerState> myCurrentUserReviewState;
    private final ObservableValue<Boolean> myIsApproved;
    private final ObservableValue<ReviewState> myReviewState;
    private final ObservableValue<ReviewRole> myRole;
    private final ObservableValue<ComputedResult<@Nullable GHPullRequestPendingReview>> myPendingReview;
    private final ObservableValue<Integer> myPendingComments;

    private final RepositoryRestrictions myRepositoryRestrictions;
    private final boolean myUserCanManageReview;
    private final boolean myUserCanMergeReview;

    private final ObservableValue<Boolean> myIsMergeEnabled;
    private final ObservableValue<Boolean> myIsSquashMergeEnabled;
    private final ObservableValue<Boolean> myIsRebaseEnabled;

    private volatile @Nullable Function<GHPRSubmitReviewViewModel, CompletableFuture<?>> mySubmitReviewInputHandler;

    public GHPRReviewFlowViewModelImpl(Disposable parentCs,
                                       Project project,
                                       ObservableValue<GHPullRequest> detailsState,
                                       GHPRRepositoryDataService repositoryDataService,
                                       GHPRSecurityService securityService,
                                       IconsProvider<String> avatarIconsProvider,
                                       GHPRDetailsDataProvider detailsData,
                                       GHPRChangesDataProvider changesData,
                                       GHPRReviewViewModelHelper reviewVmHelper) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myProject = project;
        myDetailsState = detailsState;
        myRepositoryDataService = repositoryDataService;
        mySecurityService = securityService;
        myAvatarIconsProvider = avatarIconsProvider;
        myDetailsData = detailsData;
        myChangesData = changesData;
        myReviewVmHelper = reviewVmHelper;

        myCurrentUser = securityService.getCurrentUser();
        myGhostUser = securityService.getGhostUser();

        ComputationState<GHPRMergeabilityState> mergeability = detailsData.mergeabilityStateComputationFlow(myCs);
        Runnable updateMergeability = () -> {
            ComputedResult<GHPRMergeabilityState> result = mergeability.get();
            GHPRMergeabilityState state = result == null ? null : result.getOrNull();
            if (state != null) {
                myMergeabilityState.set(state);
            }
        };
        Disposer.register(myCs, mergeability.addListener(it -> updateMergeability.run())::run);
        updateMergeability.run();

        myRequestedReviewers = detailsState.map(GHPRReviewFlowViewModelImpl::requestedReviewers);
        myReviewerReviewStates = detailsState.map(details ->
            GHReviewersUtils.getReviewsByReviewers(details.getAuthor(), details.getReviews(), requestedReviewers(details), myGhostUser));
        myReviewerReviews = myReviewerReviewStates.map(states -> {
            Map<GHPullRequestRequestedReviewer, ReviewState> result = new LinkedHashMap<>();
            states.forEach((reviewer, state) -> result.put(reviewer, state.toReviewState()));
            return result;
        });
        myCurrentUserReviewState = myReviewerReviewStates.map(states -> states.get(myCurrentUser));

        myIsApproved = ObservableValue.combine(myReviewerReviewStates, myMergeabilityState, (reviews, state) -> {
            int requiredApprovingReviewsCount = state == null ? 0 : state.getRequiredApprovingReviewsCount();
            long approvedReviews = reviews.values().stream().filter(it -> it == GHPRReviewerState.APPROVED).count();
            return requiredApprovingReviewsCount == 0 ? approvedReviews > 0 : approvedReviews >= requiredApprovingReviewsCount;
        });

        myReviewState = ObservableValue.combine(detailsState, myIsApproved, (details, isApproved) -> {
            if (isApproved) {
                return ReviewState.ACCEPTED;
            }
            if (details.getReviewDecision() == null) {
                return ReviewState.NEED_REVIEW;
            }
            return switch (details.getReviewDecision()) {
                case APPROVED -> ReviewState.ACCEPTED;
                case CHANGES_REQUESTED -> ReviewState.WAIT_FOR_UPDATES;
                case REVIEW_REQUIRED -> ReviewState.NEED_REVIEW;
            };
        });

        myRole = detailsState.map(details -> {
            if (isAuthor(details, myCurrentUser)) {
                return ReviewRole.AUTHOR;
            }
            if (isReviewer(details, myCurrentUser)) {
                return ReviewRole.REVIEWER;
            }
            return ReviewRole.GUEST;
        });

        myPendingReview = reviewVmHelper.getPendingReviewState();
        myPendingComments = myPendingReview.map(it -> {
            GHPullRequestPendingReview review = it.getOrNull();
            return review == null ? 0 : review.commentsCount();
        });

        myRepositoryRestrictions = new RepositoryRestrictions(securityService);
        myUserCanManageReview = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) ||
            detailsState.get().getViewerDidAuthor();
        myUserCanMergeReview = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE) &&
            !securityService.isMergeForbiddenForProject();

        myIsMergeEnabled = myMergeabilityState.map(it ->
            it != null && !it.isRestricted() && myRepositoryRestrictions.isMergeAllowed() && it.getCanBeMerged() && myUserCanMergeReview);
        myIsSquashMergeEnabled = myMergeabilityState.map(it ->
            it != null && !it.isRestricted() && myRepositoryRestrictions.isSquashMergeAllowed() && it.getCanBeMerged() && myUserCanMergeReview);
        myIsRebaseEnabled = myMergeabilityState.map(it ->
            it != null && !it.isRestricted() && myRepositoryRestrictions.isRebaseAllowed() && it.getCanBeRebased() && myUserCanMergeReview);
    }

    private static List<GHPullRequestRequestedReviewer> requestedReviewers(GHPullRequest details) {
        List<GHPullRequestRequestedReviewer> result = new ArrayList<>();
        for (GHPullRequestReviewRequest request : details.getReviewRequests()) {
            GHPullRequestRequestedReviewer reviewer = request.getRequestedReviewer();
            if (reviewer != null) {
                result.add(reviewer);
            }
        }
        return result;
    }

    @Override
    public ObservableValue<Boolean> isBusy() {
        return myTaskLauncher.getBusy();
    }

    @Override
    public ObservableValue<List<GHPullRequestRequestedReviewer>> getRequestedReviewers() {
        return myRequestedReviewers;
    }

    @Override
    public ObservableValue<Map<GHPullRequestRequestedReviewer, GHPRReviewerState>> getReviewerReviewStates() {
        return myReviewerReviewStates;
    }

    @Override
    public ObservableValue<Map<GHPullRequestRequestedReviewer, ReviewState>> getReviewerReviews() {
        return myReviewerReviews;
    }

    @Override
    public ObservableValue<@Nullable GHPRReviewerState> getCurrentUserReviewState() {
        return myCurrentUserReviewState;
    }

    @Override
    public ObservableValue<ReviewState> getReviewState() {
        return myReviewState;
    }

    @Override
    public ObservableValue<ReviewRole> getRole() {
        return myRole;
    }

    @Override
    public ObservableValue<ComputedResult<@Nullable GHPullRequestPendingReview>> getPendingReview() {
        return myPendingReview;
    }

    @Override
    public ObservableValue<Integer> getPendingComments() {
        return myPendingComments;
    }

    @Override
    public RepositoryRestrictions getRepositoryRestrictions() {
        return myRepositoryRestrictions;
    }

    @Override
    public boolean getUserCanManageReview() {
        return myUserCanManageReview;
    }

    @Override
    public boolean getUserCanMergeReview() {
        return myUserCanMergeReview;
    }

    @Override
    public ObservableValue<Boolean> isMergeEnabled() {
        return myIsMergeEnabled;
    }

    @Override
    public ObservableValue<Boolean> isSquashMergeEnabled() {
        return myIsSquashMergeEnabled;
    }

    @Override
    public ObservableValue<Boolean> isRebaseEnabled() {
        return myIsRebaseEnabled;
    }

    @Override
    public @Nullable Function<GHPRSubmitReviewViewModel, CompletableFuture<?>> getSubmitReviewInputHandler() {
        return mySubmitReviewInputHandler;
    }

    @Override
    public void setSubmitReviewInputHandler(@Nullable Function<GHPRSubmitReviewViewModel, CompletableFuture<?>> handler) {
        mySubmitReviewInputHandler = handler;
    }

    @Override
    public void submitReview() {
        Function<GHPRSubmitReviewViewModel, CompletableFuture<?>> handler =
            Objects.requireNonNull(mySubmitReviewInputHandler, "UI handler was not set");
        myReviewVmHelper.submitReview(handler);
    }

    @Override
    public void mergeReview() {
        runAction(() -> {
            GHPullRequest details = myDetailsState.get();
            return showMergeDialog(ReviewLocalize.reviewMergeCommitDialogTitle().get(), details, details.getTitle())
                .thenCompose(message -> message == null
                    ? CompletableFuture.completedFuture(null)
                    : myDetailsData.merge(CommitsUtil.splitCommitMessage(message), details.getHeadRefOid()));
        });
    }

    @Override
    public void rebaseReview() {
        runAction(() -> myDetailsData.rebaseMerge(myDetailsState.get().getHeadRefOid()));
    }

    @Override
    public void squashAndMergeReview() {
        runAction(() -> {
            GHPullRequest details = myDetailsState.get();
            return myChangesData.loadCommits().thenCompose(commits -> {
                String body = "* " + StringUtil.join(commits, GHCommit::getMessageHeadline, "\n\n* ");
                return showMergeDialog(ReviewLocalize.reviewMergeCommitDialogTitleWithSquash().get(), details, body);
            }).thenCompose(message -> message == null
                ? CompletableFuture.completedFuture(null)
                : myDetailsData.squashMerge(CommitsUtil.splitCommitMessage(message), details.getHeadRefOid()));
        });
    }

    private CompletableFuture<@Nullable String> showMergeDialog(String title, GHPullRequest details, String body) {
        CompletableFuture<@Nullable String> result = new CompletableFuture<>();
        myProject.getUIAccess().give(() -> {
            ReviewMergeCommitMessageDialog dialog = new ReviewMergeCommitMessageDialog(myProject,
                LocalizeValue.of(title),
                GithubLocalize.pullRequestMergePullRequest(details.getNumber()).get(),
                body);
            myProject.getApplication().getInstance(DialogService.class).build(dialog).showAsync().whenComplete((value, error) ->
                result.complete(value == DialogValue.OK_VALUE ? dialog.getMessage() : null));
        });
        return result;
    }

    @Override
    public void closeReview() {
        runAction(myDetailsData::close);
    }

    @Override
    public void reopenReview() {
        runAction(myDetailsData::reopen);
    }

    @Override
    public void postDraftedReview() {
        runAction(myDetailsData::markReadyForReview);
    }

    @Override
    public void removeReviewer(GHPullRequestRequestedReviewer reviewer) {
        runAction(() -> {
            List<GHPullRequestRequestedReviewer> reviewers = myRequestedReviewers.get();
            List<GHPullRequestRequestedReviewer> newReviewers = new ArrayList<>(reviewers);
            newReviewers.remove(reviewer);
            return myDetailsData.adjustReviewers(new CollectionDelta<>(reviewers, newReviewers));
        });
    }

    @Override
    public void requestReview(RelativePoint2D point) {
        runAction(() -> {
            Set<GHPullRequestRequestedReviewer> reviewersSet = new LinkedHashSet<>(myRequestedReviewers.get());
            reviewersSet.addAll(myReviewerReviews.get().keySet());
            List<GHPullRequestRequestedReviewer> reviewers = new ArrayList<>(reviewersSet);

            GHActor author = myDetailsState.get().getAuthor();
            MutableObservableValue<IncrementallyComputedValue<List<GHPullRequestRequestedReviewer>>> potentialReviewers =
                MutableObservableValue.of(IncrementallyComputedValue.loading());
            Disposable loadingCs = Disposable.newDisposable("GHPR potential reviewers");
            Disposer.register(myCs, loadingCs);
            CompletableFuture<?>[] loading = new CompletableFuture<?>[1];
            Runnable reload = () -> {
                if (loading[0] != null) {
                    loading[0].cancel(false);
                }
                loading[0] = IncrementallyComputedValue.collectIncrementallyTo(
                    consumer -> GHPRRepositoryDataServiceExtensions.getBatchedPotentialReviewers(myRepositoryDataService, mySecurityService,
                        batch -> consumer.accept(batch.stream().filter(it -> author == null || !Objects.equals(it.getId(), author.getId())).toList())),
                    potentialReviewers::set);
            };
            myRepositoryDataService.addDataReloadListener(loadingCs, reload);
            reload.run();

            CompletableFuture<List<GHPullRequestRequestedReviewer>> selected = new CompletableFuture<>();
            myProject.getUIAccess().give(() -> ChooserPopupUtil.showMultipleChooserPopupWithIncrementalLoading(point,
                    reviewers,
                    potentialReviewers,
                    GHUIUtil.SelectionPresenters.PRReviewers(myAvatarIconsProvider),
                    PopupConfig.DEFAULT.withShowDirection(ShowDirection.ABOVE))
                .whenComplete((value, error) -> {
                    if (error != null) {
                        selected.completeExceptionally(error);
                    }
                    else {
                        selected.complete(value);
                    }
                }));
            return selected.whenComplete((r, e) -> Disposer.dispose(loadingCs)).thenCompose(selectedReviewers -> {
                CollectionDelta<GHPullRequestRequestedReviewer> delta = new CollectionDelta<>(reviewers, selectedReviewers);
                if (delta.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                }
                return myDetailsData.adjustReviewers(delta);
            });
        });
    }

    @Override
    public void reRequestReview() {
        runAction(() -> {
            List<GHPullRequestRequestedReviewer> reviewers = myRequestedReviewers.get();
            Set<GHPullRequestRequestedReviewer> newReviewers = new LinkedHashSet<>(reviewers);
            newReviewers.addAll(myReviewerReviews.get().keySet());
            return myDetailsData.adjustReviewers(new CollectionDelta<>(reviewers, newReviewers));
        });
    }

    @Override
    public void reRequestReview(GHPullRequestRequestedReviewer reviewer) {
        runAction(() -> {
            List<GHPullRequestRequestedReviewer> reviewers = myRequestedReviewers.get();
            Set<GHPullRequestRequestedReviewer> newReviewers = new LinkedHashSet<>(reviewers);
            newReviewers.add(reviewer);
            CollectionDelta<GHPullRequestRequestedReviewer> delta = new CollectionDelta<>(reviewers, newReviewers);
            if (delta.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return myDetailsData.adjustReviewers(delta);
        });
    }

    @Override
    public void setMyselfAsReviewer() {
        runAction(() -> {
            List<GHPullRequestRequestedReviewer> reviewers = myRequestedReviewers.get();
            Set<GHPullRequestRequestedReviewer> newReviewers = new LinkedHashSet<>(reviewers);
            newReviewers.add(mySecurityService.getCurrentUser());
            return myDetailsData.adjustReviewers(new CollectionDelta<>(reviewers, newReviewers));
        });
    }

    private void runAction(Supplier<? extends CompletableFuture<?>> action) {
        myTaskLauncher.launch(() -> {
            CompletableFuture<?> future;
            try {
                future = action.get();
            }
            catch (Exception e) {
                future = CompletableFuture.failedFuture(e);
            }
            return future.whenComplete((r, e) -> {
                if (e != null && !FutureUtil.isCancellation(e)) {
                    //TODO: show error in UI
                    LOG.warn(FutureUtil.unwrap(e));
                }
            });
        });
    }

    private static boolean isAuthor(GHPullRequest details, GHUser user) {
        GHActor author = details.getAuthor();
        return author != null && author.getId().equals(user.getId());
    }

    private static boolean isReviewer(GHPullRequest details, GHUser user) {
        for (GHPullRequestReviewRequest request : details.getReviewRequests()) {
            GHPullRequestRequestedReviewer reviewer = request.getRequestedReviewer();
            if (reviewer != null && reviewer.getId().equals(user.getId())) {
                return true;
            }
        }
        for (GHPullRequestReview review : details.getReviews()) {
            GHActor author = review.getAuthor();
            if (author != null && author.getId().equals(user.getId())) {
                return true;
            }
        }
        return false;
    }
}
