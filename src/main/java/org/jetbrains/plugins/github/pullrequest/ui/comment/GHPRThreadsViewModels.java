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

import consulo.diff.util.Side;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.diff.UnifiedCodeReviewItemPosition;
import git4idea.changes.GitTextFilePatchWithHistory;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewNewCommentEditorViewModel;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

public interface GHPRThreadsViewModels {
    boolean canComment();

    ObservableValue<Collection<GHPRCompactReviewThreadViewModel>> getCompactThreads();

    ObservableValue<Collection<GHPRReviewNewCommentEditorViewModel>> getNewComments();

    ObservableValue<Map<String, ThreadMappingData>> getThreadMappingData();

    @Nullable
    String lookupNextComment(UnifiedCodeReviewItemPosition cursorLocation, Predicate<String> isVisible);

    @Nullable
    String lookupNextComment(String currentThreadId, Predicate<String> isVisible);

    @Nullable
    String lookupPreviousComment(UnifiedCodeReviewItemPosition cursorLocation, Predicate<String> isVisible);

    @Nullable
    String lookupPreviousComment(String currentThreadId, Predicate<String> isVisible);

    GHPRReviewNewCommentEditorViewModel requestNewComment(GHPRReviewCommentPosition position);

    void cancelNewComment(RefComparisonChange change, Side side, int lineIdx);

    record ThreadMappingData(GHPullRequestReviewThread threadData,
                             @Nullable RefComparisonChange change,
                             @Nullable GitTextFilePatchWithHistory diffData) {
    }
}
