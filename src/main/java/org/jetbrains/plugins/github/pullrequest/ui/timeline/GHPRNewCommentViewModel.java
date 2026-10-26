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
package org.jetbrains.plugins.github.pullrequest.ui.timeline;

import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.review.comment.CodeReviewNewCommentViewModel;
import consulo.review.comment.CodeReviewSubmittableTextViewModelBase;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelMentionCompletion;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion;

import java.util.function.Consumer;

public class GHPRNewCommentViewModel extends CodeReviewSubmittableTextViewModelBase
    implements CodeReviewNewCommentViewModel, GHViewModelWithTextCompletion {
    private final GHPRCommentsDataProvider myCommentsDataProvider;
    private final GHViewModelWithTextCompletion myViewModelWithTextCompletion;

    public GHPRNewCommentViewModel(Project project,
                                   Disposable parentCs,
                                   GHPRCommentsDataProvider commentsDataProvider,
                                   GHViewModelWithTextCompletion viewModelWithTextCompletion) {
        super(project, parentCs, "");
        myCommentsDataProvider = commentsDataProvider;
        myViewModelWithTextCompletion = viewModelWithTextCompletion;
    }

    @Override
    public void withMentionCompletionModel(Consumer<GHViewModelMentionCompletion> consumer) {
        myViewModelWithTextCompletion.withMentionCompletionModel(consumer);
    }

    @Override
    public void submit() {
        submit(text -> myCommentsDataProvider.addComment(text).thenRun(() -> getText().set("")));
    }
}
