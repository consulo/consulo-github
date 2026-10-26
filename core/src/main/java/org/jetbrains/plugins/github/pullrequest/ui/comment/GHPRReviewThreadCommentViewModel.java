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
package org.jetbrains.plugins.github.pullrequest.ui.comment;

import consulo.review.comment.CodeReviewTextEditingViewModel;
import consulo.review.icon.IconsProvider;
import consulo.review.timeline.CodeReviewCommentViewModel;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRReviewCommentBodyViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsViewModel;
import org.jspecify.annotations.Nullable;

import java.util.Date;

public interface GHPRReviewThreadCommentViewModel extends GHViewModelWithTextCompletion, CodeReviewCommentViewModel {
    IconsProvider<String> getAvatarIconsProvider();

    GHActor getActor();

    @Override
    Date getCreatedAt();

    ObservableValue<Boolean> isPending();

    ObservableValue<Boolean> isFirstInResolvedThread();

    GHPRReviewCommentBodyViewModel getBodyVm();

    ObservableValue<Boolean> isBusy();

    @Override
    boolean canEdit();

    @Override
    void editBody();

    @Override
    ObservableValue<@Nullable CodeReviewTextEditingViewModel> getEditVm();

    boolean canReact();

    GHReactionsViewModel getReactionsVm();

    @Override
    boolean canDelete();

    @Override
    void delete();
}
