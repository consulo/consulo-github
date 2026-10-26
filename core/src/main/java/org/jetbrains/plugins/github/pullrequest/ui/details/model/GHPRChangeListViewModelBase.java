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
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.details.model.CodeReviewChangeList;
import consulo.versionControlSystem.review.details.model.CodeReviewChangeListViewModelBase;
import consulo.versionControlSystem.review.util.RefComparisonChangeUtil;
import consulo.versionControlSystem.util.VcsFileUtil;
import git4idea.repo.GitRepository;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public abstract class GHPRChangeListViewModelBase extends CodeReviewChangeListViewModelBase implements GHPRChangeListViewModel {
    private final Project myProject;
    private final GHPRDataContext myDataContext;
    protected final GHPRDataProvider myDataProvider;
    private final BiConsumer<@Nullable GHPRIdentifier, Boolean> myOpenPullRequestDiff;
    private final GithubPullRequestsProjectUISettings myPreferences;

    protected final MutableObservableValue<@Nullable Map<RefComparisonChange, Integer>> myUnresolvedThreadsCountByChange = MutableObservableValue.of(null);

    protected GHPRChangeListViewModelBase(Disposable parentCs,
                                          Project project,
                                          GHPRDataContext dataContext,
                                          GHPRDataProvider dataProvider,
                                          CodeReviewChangeList changeList,
                                          BiConsumer<@Nullable GHPRIdentifier, Boolean> openPullRequestDiff) {
        super(parentCs, changeList);
        myProject = project;
        myDataContext = dataContext;
        myDataProvider = dataProvider;
        myOpenPullRequestDiff = openPullRequestDiff;
        myPreferences = GithubPullRequestsProjectUISettings.getInstance(project);

        ComputationState<List<GHPullRequestReviewThread>> threads = dataProvider.getReviewData().threadsComputationFlow(cs);
        Runnable update = () -> {
            ComputedResult<List<GHPullRequestReviewThread>> result = threads.get();
            if (result == null || result.isInProgress()) {
                return;
            }
            List<GHPullRequestReviewThread> loaded = result.getOrNull();
            Map<String, Integer> unresolvedThreadsByPath = new HashMap<>();
            if (loaded != null) {
                for (GHPullRequestReviewThread thread : loaded) {
                    if (!thread.isResolved()) {
                        unresolvedThreadsByPath.merge(thread.getPath(), 1, Integer::sum);
                    }
                }
            }
            Map<RefComparisonChange, Integer> byChange = new HashMap<>();
            for (RefComparisonChange change : getChanges()) {
                byChange.put(change, unresolvedThreadsByPath.getOrDefault(relativePath(change), 0));
            }
            myUnresolvedThreadsCountByChange.set(byChange);
        };
        Disposer.register(cs, threads.addListener(it -> update.run())::run);
        update.run();
    }

    protected GitRepository getRepository() {
        return myDataContext.getRepositoryDataService().getRemoteCoordinates().repository();
    }

    protected String relativePath(RefComparisonChange change) {
        return VcsFileUtil.relativePath(getRepository().getRoot(), RefComparisonChangeUtil.getFilePath(change));
    }

    @Override
    public Project getProject() {
        return myProject;
    }

    @Override
    public ObservableValue<Set<String>> getGrouping() {
        return myPreferences.getChangesGroupingState();
    }

    @Override
    public void showDiffPreview() {
        myOpenPullRequestDiff.accept(myDataProvider.getId(), true);
    }

    @Override
    public void showDiff() {
        // TODO: show standalone
        showDiffPreview();
    }

    @Override
    public void setGrouping(Collection<String> grouping) {
        myPreferences.setChangesGrouping(new LinkedHashSet<>(grouping));
    }
}
