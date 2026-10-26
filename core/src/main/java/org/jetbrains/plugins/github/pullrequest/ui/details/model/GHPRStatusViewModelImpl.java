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
package org.jetbrains.plugins.github.pullrequest.ui.details.model;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.details.data.CodeReviewCIJob;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import git4idea.remote.GitRemoteUrlCoordinates;
import git4idea.remote.hosting.ui.ResolveConflictsLocallyViewModel;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GHPRStatusViewModelImpl implements GHPRStatusViewModel {
    private final Disposable myCs;
    private final boolean myViewerDidAuthor;
    private final ObservableValue<Boolean> myIsDraft;
    private final MutableObservableValue<@Nullable GHPRMergeabilityState> myMergeabilityState = MutableObservableValue.of(null);
    private final ObservableValue<List<CodeReviewCIJob>> myCiJobs;
    private final ObservableValue<Boolean> myIsRestricted;
    private final ObservableValue<Integer> myRequiredApprovingReviewsCount;
    // TODO: Implement after switching to version 3.2
    private final ObservableValue<Boolean> myRequiredConversationsResolved = ObservableValue.of(false);
    private final List<Consumer<List<CodeReviewCIJob>>> myShowJobsDetailsRequests = new CopyOnWriteArrayList<>();
    private final ResolveConflictsLocallyViewModel<GHPRResolveConflictsLocallyError> myResolveConflictsVm;

    public GHPRStatusViewModelImpl(Disposable parentCs,
                                   Project project,
                                   GitRemoteUrlCoordinates remoteUrlCoordinates,
                                   GHPRDetailsDataProvider detailsData,
                                   ObservableValue<GHPullRequest> detailsState) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);

        myViewerDidAuthor = detailsState.get().getViewerDidAuthor();
        myIsDraft = detailsState.map(GHPullRequest::isDraft);

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

        myCiJobs = myMergeabilityState.map(it -> it == null ? List.of() : it.getCiJobs());
        myIsRestricted = myMergeabilityState.map(it -> it != null && it.isRestricted());
        myRequiredApprovingReviewsCount = myMergeabilityState.map(it -> it == null ? 0 : it.getRequiredApprovingReviewsCount());

        myResolveConflictsVm = GHPRResolveConflictsLocallyViewModelImpl.create(myCs, project, remoteUrlCoordinates, detailsData);
    }

    @Override
    public boolean getViewerDidAuthor() {
        return myViewerDidAuthor;
    }

    @Override
    public ObservableValue<Boolean> isDraft() {
        return myIsDraft;
    }

    @Override
    public ObservableValue<@Nullable GHPRMergeabilityState> getMergeabilityState() {
        return myMergeabilityState;
    }

    @Override
    public ObservableValue<@Nullable Boolean> getHasConflicts() {
        return myResolveConflictsVm.getHasConflicts();
    }

    @Override
    public ObservableValue<List<CodeReviewCIJob>> getCiJobs() {
        return myCiJobs;
    }

    @Override
    public ObservableValue<Boolean> isRestricted() {
        return myIsRestricted;
    }

    @Override
    public ObservableValue<Integer> getRequiredApprovingReviewsCount() {
        return myRequiredApprovingReviewsCount;
    }

    @Override
    public ObservableValue<Boolean> getRequiredConversationsResolved() {
        return myRequiredConversationsResolved;
    }

    @Override
    public void addShowJobsDetailsRequestListener(Disposable parentDisposable, Consumer<List<CodeReviewCIJob>> listener) {
        myShowJobsDetailsRequests.add(listener);
        Disposer.register(parentDisposable, () -> myShowJobsDetailsRequests.remove(listener));
    }

    @Override
    public void showJobsDetails() {
        List<CodeReviewCIJob> jobs = myCiJobs.get();
        myShowJobsDetailsRequests.forEach(it -> it.accept(jobs));
    }

    @Override
    public ResolveConflictsLocallyViewModel<GHPRResolveConflictsLocallyError> getResolveConflictsVm() {
        return myResolveConflictsVm;
    }
}
