// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.review;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import git4idea.changes.GitBranchComparisonResult;
import git4idea.ui.branch.GitCurrentBranchPresentationUpdatedListener;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRReviewBranchStateSharedViewModel;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public class GHPRBranchWidgetViewModelImpl implements GHPRBranchWidgetViewModel {
    private final Disposable myCs;
    private final GithubPullRequestsProjectUISettings mySettings;
    private final GHPRReviewBranchStateSharedViewModel mySharedBranchVm;
    private final GHPRReviewViewModelHelper myReviewVmHelper;
    private final GHPRIdentifier myId;
    private final Consumer<GHPRIdentifier> myViewPullRequest;
    private final GHPRReviewViewModel myReviewVm;

    private final MutableObservableValue<ComputedResult<?>> myDataLoadingState = MutableObservableValue.of(ComputedResult.loading());

    public GHPRBranchWidgetViewModelImpl(Project project,
                                         Disposable parentCs,
                                         GithubPullRequestsProjectUISettings settings,
                                         GHPRDataProvider dataProvider,
                                         GHPRReviewBranchStateSharedViewModel sharedBranchVm,
                                         GHPRReviewViewModelHelper reviewVmHelper,
                                         GHPRIdentifier id,
                                         Consumer<GHPRIdentifier> viewPullRequest) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        mySettings = settings;
        mySharedBranchVm = sharedBranchVm;
        myReviewVmHelper = reviewVmHelper;
        myId = id;
        myViewPullRequest = viewPullRequest;
        myReviewVm = new DelegatingGHPRReviewViewModel(reviewVmHelper);

        ComputationState<GitBranchComparisonResult> changesState = dataProvider.getChangesData().changesComputationState(myCs);
        Consumer<@Nullable ComputedResult<GitBranchComparisonResult>> onChanges = result -> {
            myDataLoadingState.set(result == null ? ComputedResult.loading() : result);
            if (!project.isDisposed()) {
                project.getMessageBus().syncPublisher(GitCurrentBranchPresentationUpdatedListener.class).presentationUpdated();
            }
        };
        Disposer.register(myCs, changesState.addListener(onChanges)::run);
        onChanges.accept(changesState.get());
    }

    @Override
    public GHPRIdentifier getId() {
        return myId;
    }

    @Override
    public ObservableValue<ComputedResult<?>> getDataLoadingState() {
        return myDataLoadingState;
    }

    @Override
    public ObservableValue<Boolean> getUpdateRequired() {
        return mySharedBranchVm.getUpdateRequired();
    }

    @Override
    public ObservableValue<Boolean> getEditorReviewEnabled() {
        return mySettings.getEditorReviewEnabledState();
    }

    @Override
    public void addUpdateErrorsListener(Disposable parentDisposable, Consumer<Exception> listener) {
        mySharedBranchVm.addUpdateErrorsListener(parentDisposable, listener);
    }

    @Override
    public void showPullRequest() {
        myViewPullRequest.accept(myId);
    }

    @Override
    public void updateBranch() {
        mySharedBranchVm.updateBranch();
    }

    @Override
    public void toggleEditorReview(boolean enabled) {
        mySettings.setEditorReviewEnabled(enabled);
    }

    @Override
    public ObservableValue<ComputedResult<@Nullable GHPullRequestPendingReview>> getPendingReview() {
        return myReviewVm.getPendingReview();
    }

    @Override
    public @Nullable Function<GHPRSubmitReviewViewModel, CompletableFuture<?>> getSubmitReviewInputHandler() {
        return myReviewVm.getSubmitReviewInputHandler();
    }

    @Override
    public void setSubmitReviewInputHandler(@Nullable Function<GHPRSubmitReviewViewModel, CompletableFuture<?>> handler) {
        myReviewVm.setSubmitReviewInputHandler(handler);
    }

    @Override
    public void submitReview() {
        myReviewVm.submitReview();
    }
}
