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

import consulo.diff.util.LineRange;
import consulo.diff.util.Range;
import consulo.review.icon.IconsProvider;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.change.RefComparisonChange;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

public interface GHPRReviewFileEditorViewModel {
    Key<GHPRReviewFileEditorViewModel> KEY = Key.create(GHPRReviewFileEditorViewModel.class.getName());

    RefComparisonChange getChange();

    ObservableValue<Boolean> isUpdateRequired();

    IconsProvider<String> getIconProvider();

    GHUser getCurrentUser();

    ObservableValue<@Nullable ComputedResult<CharSequence>> getOriginalContent();

    List<Range> getChangedRanges();

    @Nullable
    String getBaseContent(LineRange lines);

    boolean getCanComment();

    List<Range> getCommentableRanges();

    ObservableValue<List<GHPRReviewFileEditorThreadViewModel>> getThreads();

    ObservableValue<List<GHPRReviewFileEditorNewCommentViewModel>> getNewComments();

    ObservableValue<Set<Integer>> getLinesWithComments();

    @Nullable
    String lookupNextComment(String threadId);

    @Nullable
    String lookupNextComment(int line);

    @Nullable
    String lookupPreviousComment(String threadId);

    @Nullable
    String lookupPreviousComment(int line);

    @Nullable
    Pair<RefComparisonChange, Integer> getThreadPosition(String threadId);

    void requestThreadFocus(String threadId);

    void requestNewComment(int lineIdx, boolean focus);

    void cancelNewComment(int lineIdx);

    void requestNewComment(LineRange lineRange, boolean focus);

    void showDiff(@Nullable Integer lineIdx);

    ObservableValue<ComputedResult<Boolean>> isViewedState();

    void setViewedState(boolean isViewed);
}
