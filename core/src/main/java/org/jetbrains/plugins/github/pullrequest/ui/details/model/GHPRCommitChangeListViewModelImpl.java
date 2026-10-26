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
import consulo.project.Project;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.details.model.CodeReviewChangeDetails;
import consulo.versionControlSystem.review.details.model.CodeReviewChangeList;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class GHPRCommitChangeListViewModelImpl extends GHPRChangeListViewModelBase {
    private final ObservableValue<Map<RefComparisonChange, CodeReviewChangeDetails>> myDetailsByChange;

    public GHPRCommitChangeListViewModelImpl(Disposable parentCs,
                                             Project project,
                                             GHPRDataContext dataContext,
                                             GHPRDataProvider dataProvider,
                                             CodeReviewChangeList changeList,
                                             BiConsumer<@Nullable GHPRIdentifier, Boolean> openPullRequestDiff) {
        super(parentCs, project, dataContext, dataProvider, changeList, openPullRequestDiff);
        myDetailsByChange = myUnresolvedThreadsCountByChange.map(threadsCount -> {
            Map<RefComparisonChange, CodeReviewChangeDetails> result = new HashMap<>();
            if (threadsCount != null) {
                threadsCount.forEach((change, discussions) -> result.put(change, new CodeReviewChangeDetails(true, discussions)));
            }
            return result;
        });
    }

    @Override
    public ObservableValue<Map<RefComparisonChange, CodeReviewChangeDetails>> getDetailsByChange() {
        return myDetailsByChange;
    }
}
