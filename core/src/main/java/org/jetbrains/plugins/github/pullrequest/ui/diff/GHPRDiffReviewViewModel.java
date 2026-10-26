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
package org.jetbrains.plugins.github.pullrequest.ui.diff;

import consulo.diff.util.Range;
import consulo.diff.util.Side;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.dataholder.Key;
import consulo.versionControlSystem.review.diff.DiffLineLocation;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface GHPRDiffReviewViewModel {
    Key<GHPRDiffReviewViewModel> KEY = Key.create(GHPRDiffReviewViewModel.class.getName());

    boolean canComment();

    boolean canNavigate();

    List<Range> getCommentableRanges();

    List<Range> getChangedRanges();

    ObservableValue<Collection<GHPRReviewThreadDiffViewModel>> getThreads();

    ObservableValue<Collection<GHPRNewCommentDiffViewModel>> getNewComments();

    ObservableValue<Set<DiffLineLocation>> getLocationsWithDiscussions();

    void requestNewComment(GHPRReviewCommentLocation location, boolean focus);

    void cancelNewComment(Side side, int lineIdx);

    ObservableValue<ComputedResult<Boolean>> isViewedState();

    void setViewedState(boolean isViewed);
}
