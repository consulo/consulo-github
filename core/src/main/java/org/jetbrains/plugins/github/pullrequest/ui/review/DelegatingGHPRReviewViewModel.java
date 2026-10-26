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

import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class DelegatingGHPRReviewViewModel implements GHPRReviewViewModel {
    private final GHPRReviewViewModelHelper myHelper;
    private volatile @Nullable Function<GHPRSubmitReviewViewModel, CompletableFuture<?>> mySubmitReviewInputHandler;

    public DelegatingGHPRReviewViewModel(GHPRReviewViewModelHelper helper) {
        myHelper = helper;
    }

    @Override
    public ObservableValue<ComputedResult<@Nullable GHPullRequestPendingReview>> getPendingReview() {
        return myHelper.getPendingReviewState();
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
        myHelper.submitReview(handler);
    }
}
