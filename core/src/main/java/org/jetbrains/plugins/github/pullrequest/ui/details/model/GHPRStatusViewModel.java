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

import consulo.review.details.model.CodeReviewStatusViewModel;
import consulo.util.concurrent.coroutine.ObservableValue;
import git4idea.remote.hosting.ui.ResolveConflictsLocallyViewModel;
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState;
import org.jspecify.annotations.Nullable;

public interface GHPRStatusViewModel extends CodeReviewStatusViewModel {
    boolean getViewerDidAuthor();

    ObservableValue<Boolean> isDraft();

    ObservableValue<@Nullable GHPRMergeabilityState> getMergeabilityState();

    ObservableValue<Boolean> isRestricted();

    ObservableValue<Integer> getRequiredApprovingReviewsCount();

    ObservableValue<Boolean> getRequiredConversationsResolved();

    ResolveConflictsLocallyViewModel<GHPRResolveConflictsLocallyError> getResolveConflictsVm();
}
