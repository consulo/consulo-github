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
package org.jetbrains.plugins.github.pullrequest.ui.review;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class GHPRReviewViewModelHelper {
    private static final Logger LOG = Logger.getInstance(GHPRReviewViewModelHelper.class);

    private final Disposable myCs;
    private final Project myProject;
    private final GHPRDataProvider myDataProvider;
    private final GHPRReviewDataProvider myReviewData;

    private final ObservableValue<ComputedResult<@Nullable GHPullRequestPendingReview>> myPendingReviewState;

    public GHPRReviewViewModelHelper(Disposable parentCs, Project project, GHPRDataProvider dataProvider) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myProject = project;
        myDataProvider = dataProvider;
        myReviewData = dataProvider.getReviewData();

        ComputationState<@Nullable GHPullRequestPendingReview> state = myReviewData.pendingReviewComputationFlow(myCs);
        myPendingReviewState = state.map(it -> it == null ? ComputedResult.loading() : it);

        myReviewData.signalPendingReviewNeedsReload();
    }

    public ObservableValue<ComputedResult<@Nullable GHPullRequestPendingReview>> getPendingReviewState() {
        return myPendingReviewState;
    }

    public void submitReview(Function<GHPRSubmitReviewViewModel, CompletableFuture<?>> handler) {
        ComputedResult<@Nullable GHPullRequestPendingReview> pendingReviewResult = myPendingReviewState.get();
        if (pendingReviewResult.isInProgress()) {
            return;
        }
        myDataProvider.getDetailsData().loadDetails().whenComplete((details, error) -> {
            if (error != null) {
                LOG.info("Details loading failed", error);
                return;
            }
            Disposable vmCs = Disposable.newDisposable("GHPRSubmitReviewViewModel");
            Disposer.register(myCs, vmCs);
            CompletableFuture<?>[] handlerFuture = new CompletableFuture<?>[1];
            GHPRSubmitReviewViewModelImpl vm = new GHPRSubmitReviewViewModelImpl(vmCs, myProject, myReviewData, details.getViewerDidAuthor(),
                pendingReviewResult.getOrNull(), () -> {
                myDataProvider.getDetailsData().signalDetailsNeedReload();
                CompletableFuture<?> future = handlerFuture[0];
                if (future != null) {
                    future.cancel(false);
                }
                Disposer.dispose(vmCs);
            });
            handlerFuture[0] = handler.apply(vm);
            handlerFuture[0].whenComplete((r, e) -> Disposer.dispose(vmCs));
        });
    }
}
