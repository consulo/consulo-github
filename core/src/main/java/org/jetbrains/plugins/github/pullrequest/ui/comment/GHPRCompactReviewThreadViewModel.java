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

import consulo.disposer.Disposable;
import consulo.review.timeline.thread.CodeReviewResolvableItemViewModel;
import consulo.review.timeline.thread.CodeReviewThreadViewModel;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRNewThreadCommentViewModel;
import org.jetbrains.plugins.github.ui.util.GHHtmlPaneUtil;

import java.util.List;

/**
 * A viewmodel for a compact foldable review thread view
 */
public interface GHPRCompactReviewThreadViewModel extends GHPRReviewThreadViewModel,
    CodeReviewResolvableItemViewModel,
    CodeReviewThreadViewModel {
    @Override
    ObservableValue<Boolean> isBusy();

    ObservableValue<List<CommentItem>> getCommentItems();

    @Override
    ObservableValue<Boolean> getCanCreateReplies();

    ObservableValue<Boolean> isWritingReply();

    @Override
    GHPRNewThreadCommentViewModel getNewReplyVm();

    void startWritingReply();

    void stopWritingReply();

    @Override
    ObservableValue<Boolean> isResolved();

    @Override
    ObservableValue<Boolean> getCanChangeResolvedState();

    @Override
    void changeResolvedState();

    void addFocusRequestListener(Disposable parentDisposable, Runnable listener);

    void requestFocus();

    @RequiredUIAccess
    @Override
    default boolean openLink(String href) {
        return GHHtmlPaneUtil.handleGithubHyperlink(href, number -> {
            for (CommentItem item : getCommentItems().get()) {
                if (item instanceof CommentItem.Comment comment) {
                    comment.vm().getBodyVm().openPullRequestInfoAndTimeline(number);
                    return;
                }
            }
        });
    }

    sealed interface CommentItem permits CommentItem.Comment, CommentItem.Expander {
        record Comment(GHPRReviewThreadCommentViewModel vm) implements CommentItem {
        }

        record Expander(int collapsedCount, Runnable expand) implements CommentItem {
        }
    }
}
