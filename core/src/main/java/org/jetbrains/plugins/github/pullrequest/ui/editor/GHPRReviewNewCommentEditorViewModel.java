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
package org.jetbrains.plugins.github.pullrequest.ui.editor;

import consulo.codeEditor.Editor;
import consulo.review.comment.CodeReviewNewCommentViewModel;
import consulo.review.icon.IconsProvider;
import consulo.review.list.UserPresentation;
import consulo.review.timeline.thread.CodeReviewTrackableItemViewModel;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.review.diff.DiffLineLocation;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineUIUtil;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

public interface GHPRReviewNewCommentEditorViewModel extends CodeReviewNewCommentViewModel, CodeReviewTrackableItemViewModel, GHViewModelWithTextCompletion {
    ObservableValue<GHPRReviewCommentPosition> getPosition();

    GHActor getCurrentUser();

    IconsProvider<String> getAvatarIconsProvider();

    ObservableValue<List<SubmitAction>> getSubmitActions();

    Date getCreatedAt();

    void cancel();

    void updateLineRange(@Nullable DiffLineLocation newStartLocation, @Nullable DiffLineLocation newEndLocation);

    @Override
    default Consumer<Editor> getEditorSetup() {
        return GHViewModelWithTextCompletion.editorSetup(this);
    }

    @Override
    default UserPresentation getCurrentUserPresentation() {
        return GHPRTimelineUIUtil.createUserPresentation(getCurrentUser(), getAvatarIconsProvider());
    }

    sealed interface SubmitAction extends Runnable permits SubmitAction.CreateSingleComment, SubmitAction.CreateReview, SubmitAction.CreateReviewComment {
        @FunctionalInterface
        non-sealed interface CreateSingleComment extends SubmitAction {
        }

        @FunctionalInterface
        non-sealed interface CreateReview extends SubmitAction {
        }

        @FunctionalInterface
        non-sealed interface CreateReviewComment extends SubmitAction {
        }
    }
}
