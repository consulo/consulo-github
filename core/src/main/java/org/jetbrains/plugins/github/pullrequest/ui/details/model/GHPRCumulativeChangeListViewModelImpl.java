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

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.util.ComputedResult;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.details.model.CodeReviewChangeDetails;
import consulo.versionControlSystem.review.details.model.CodeReviewChangeList;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestFileViewedState;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRViewedStateDataProvider;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class GHPRCumulativeChangeListViewModelImpl extends GHPRChangeListViewModelBase implements GHPRCumulativeChangeListViewModel {
    private final GHPRViewedStateDataProvider myViewedStateData;
    private final MutableObservableValue<Map<RefComparisonChange, CodeReviewChangeDetails>> myDetailsByChange = MutableObservableValue.of(Map.of());
    private @Nullable Map<String, GHPullRequestFileViewedState> myViewedStateByPath;

    public GHPRCumulativeChangeListViewModelImpl(Disposable parentCs,
                                                 Project project,
                                                 GHPRDataContext dataContext,
                                                 GHPRDataProvider dataProvider,
                                                 CodeReviewChangeList changeList,
                                                 BiConsumer<@Nullable GHPRIdentifier, Boolean> openPullRequestDiff) {
        super(parentCs, project, dataContext, dataProvider, changeList, openPullRequestDiff);
        myViewedStateData = dataProvider.getViewedStateData();

        ComputationState<Map<String, GHPullRequestFileViewedState>> viewedState = myViewedStateData.viewedStateComputationState(cs);
        Runnable updateViewed = () -> {
            ComputedResult<Map<String, GHPullRequestFileViewedState>> result = viewedState.get();
            if (result == null || result.isInProgress()) {
                return;
            }
            Map<String, GHPullRequestFileViewedState> loaded = result.getOrNull();
            synchronized (this) {
                myViewedStateByPath = loaded == null ? Map.of() : loaded;
            }
            updateDetails();
        };
        Disposer.register(cs, viewedState.addListener(it -> updateViewed.run())::run);
        Disposer.register(cs, myUnresolvedThreadsCountByChange.addListener(it -> updateDetails())::run);
        updateViewed.run();
    }

    private void updateDetails() {
        Map<RefComparisonChange, Integer> threadsCount = myUnresolvedThreadsCountByChange.get();
        Map<String, GHPullRequestFileViewedState> viewedStateByPath;
        synchronized (this) {
            viewedStateByPath = myViewedStateByPath;
        }
        if (threadsCount == null || viewedStateByPath == null) {
            return;
        }
        Map<RefComparisonChange, CodeReviewChangeDetails> result = new HashMap<>();
        for (RefComparisonChange change : getChanges()) {
            GHPullRequestFileViewedState state = viewedStateByPath.get(relativePath(change));
            boolean isRead = state == null || state.isViewed();
            result.put(change, new CodeReviewChangeDetails(isRead, threadsCount.getOrDefault(change, 0)));
        }
        myDetailsByChange.set(result);
    }

    @Override
    public ObservableValue<Map<RefComparisonChange, CodeReviewChangeDetails>> getDetailsByChange() {
        return myDetailsByChange;
    }

    @RequiredUIAccess
    @Override
    public void setViewedState(Iterable<RefComparisonChange> changes, boolean viewed) {
        List<String> paths = new ArrayList<>();
        for (RefComparisonChange change : changes) {
            paths.add(relativePath(change));
        }
        // TODO: handle error
        myViewedStateData.updateViewedState(paths, viewed);
    }
}
