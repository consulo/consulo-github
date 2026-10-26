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
package org.jetbrains.plugins.github.pullrequest.ui;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.review.async.ComputationState;
import consulo.review.util.ComputedResult;
import consulo.review.util.FutureUtil;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import git4idea.remote.hosting.GitAsyncExtensions;
import git4idea.repo.GitRepository;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GHPRReviewBranchStateSharedViewModel {
    private static final Logger LOG = Logger.getInstance(GHPRReviewBranchStateSharedViewModel.class);

    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;

    private final GitRepository myRepository;

    private final MutableObservableValue<Boolean> myUpdateRequired = MutableObservableValue.of(false);
    private @Nullable MutableObservableValue<String> myHeadRevState;

    private final List<Consumer<Exception>> myUpdateErrorListeners = new CopyOnWriteArrayList<>();

    public GHPRReviewBranchStateSharedViewModel(Disposable parentCs, GHPRDataContext dataContext, GHPRDataProvider dataProvider) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myDataContext = dataContext;
        myDataProvider = dataProvider;

        myRepository = dataContext.getRepositoryDataService().getRemoteCoordinates().repository();

        ComputationState<GHPullRequest> detailsState = dataProvider.getDetailsData().detailsComputationFlow(myCs);
        Disposer.register(myCs, detailsState.addListener(this::onDetails)::run);
        onDetails(detailsState.get());
    }

    private void onDetails(@Nullable ComputedResult<GHPullRequest> result) {
        GHPullRequest details = result == null ? null : result.getOrNull();
        if (details == null) {
            return;
        }
        String headRefOid = details.getHeadRefOid();
        MutableObservableValue<String> headRevState;
        synchronized (this) {
            if (myHeadRevState != null) {
                myHeadRevState.set(headRefOid);
                return;
            }
            if (Disposer.isDisposed(myCs)) {
                return;
            }
            headRevState = MutableObservableValue.of(headRefOid);
            myHeadRevState = headRevState;
        }
        ObservableValue<@Nullable Boolean> isInCurrentHistory = GitAsyncExtensions.isInCurrentHistory(myRepository, headRevState, myCs);
        Disposer.register(myCs, isInCurrentHistory.addListener(this::updateRequired)::run);
        updateRequired(isInCurrentHistory.get());
    }

    private void updateRequired(@Nullable Boolean isInCurrentHistory) {
        myUpdateRequired.set(isInCurrentHistory != null && !isInCurrentHistory);
    }

    public ObservableValue<Boolean> getUpdateRequired() {
        return myUpdateRequired;
    }

    public void addUpdateErrorsListener(Disposable parentDisposable, Consumer<Exception> listener) {
        myUpdateErrorListeners.add(listener);
        Disposer.register(parentDisposable, () -> myUpdateErrorListeners.remove(listener));
    }

    public void updateBranch() {
        doUpdateBranch();
    }

    private void doUpdateBranch() {
        GHPRDetailsDataProvider detailsData = myDataProvider.getDetailsData();
        detailsData.signalDetailsNeedReload();
        detailsData.loadDetails().whenComplete((details, error) -> {
            if (Disposer.isDisposed(myCs)) {
                return;
            }
            if (error != null) {
                if (FutureUtil.isCancellation(error)) {
                    return;
                }
                Throwable cause = FutureUtil.unwrap(error);
                LOG.warn("Pull request branch update failed", cause);
                Exception exception = cause instanceof Exception e ? e : new RuntimeException(cause);
                for (Consumer<Exception> listener : myUpdateErrorListeners) {
                    listener.accept(exception);
                }
                return;
            }
            GHPRBranchesViewModel.fetchAndCheckoutBranch(myDataContext.getRepositoryDataService().getRemoteCoordinates(), details);
        });
    }
}
