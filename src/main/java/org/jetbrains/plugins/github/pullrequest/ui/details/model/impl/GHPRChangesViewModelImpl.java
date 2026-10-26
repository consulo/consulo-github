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
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.util.ComputedResult;
import consulo.review.util.FutureUtil;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.details.model.CodeReviewChangesContainer;
import consulo.versionControlSystem.review.details.model.CodeReviewChangesViewModelDelegate;
import git4idea.changes.GitBranchComparisonResult;
import git4idea.changes.GitCommitShaWithPatches;
import org.jetbrains.plugins.github.api.data.GHCommit;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHCommitModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModelBase;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRCommitChangeListViewModelImpl;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRCumulativeChangeListViewModelImpl;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class GHPRChangesViewModelImpl implements GHPRChangesViewModel {
    private final Disposable myCs;
    private final Project myProject;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;
    private final BiConsumer<@Nullable GHPRIdentifier, Boolean> myOpenPullRequestDiff;

    private final GHApiLoadingErrorHandler myChangesLoadingErrorHandler;

    private final MutableObservableValue<List<GHCommitModel>> myReviewCommits = MutableObservableValue.of(List.of());
    private final MutableObservableValue<@Nullable ComputedResult<CodeReviewChangesContainer>> myChangesContainer = MutableObservableValue.of(null);
    private final CodeReviewChangesViewModelDelegate<GHPRChangeListViewModelBase> myDelegate;
    private final ObservableValue<Integer> mySelectedCommitIndex;
    private final ObservableValue<@Nullable GHCommitModel> mySelectedCommit;

    private @Nullable CompletableFuture<?> myCommitsRequest;
    private @Nullable CompletableFuture<?> myChangesRequest;

    public GHPRChangesViewModelImpl(Disposable parentCs,
                                    Project project,
                                    GHPRDataContext dataContext,
                                    GHPRDataProvider dataProvider,
                                    BiConsumer<@Nullable GHPRIdentifier, Boolean> openPullRequestDiff) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myProject = project;
        myDataContext = dataContext;
        myDataProvider = dataProvider;
        myOpenPullRequestDiff = openPullRequestDiff;

        myChangesLoadingErrorHandler = new GHApiLoadingErrorHandler(project, dataContext.getSecurityService().getAccount(),
            () -> dataProvider.getChangesData().signalChangesNeedReload());

        myDelegate = CodeReviewChangesViewModelDelegate.create(myCs, myChangesContainer, (cs, changes, changeList) -> {
            // a single-commit pull request has no separate cumulative diff, so its only change list is the cumulative one
            if (changeList.commitSha() == null || changes.getCommits().size() == 1) {
                return new GHPRCumulativeChangeListViewModelImpl(cs, myProject, myDataContext, myDataProvider, changeList, myOpenPullRequestDiff);
            }
            else {
                return new GHPRCommitChangeListViewModelImpl(cs, myProject, myDataContext, myDataProvider, changeList, myOpenPullRequestDiff);
            }
        });

        mySelectedCommitIndex = ObservableValue.combine(myReviewCommits, myDelegate.getSelectedCommit(), (commits, sha) -> {
            if (sha == null) {
                return -1;
            }
            for (int i = 0; i < commits.size(); i++) {
                if (commits.get(i).getOid().startsWith(sha)) {
                    return i;
                }
            }
            return -1;
        });
        mySelectedCommit = ObservableValue.combine(myReviewCommits, mySelectedCommitIndex,
            (commits, index) -> index >= 0 && index < commits.size() ? commits.get(index) : null);

        dataProvider.getChangesData().addChangesNeedReloadListener(myCs, this::reload);
        reload();
    }

    private void reload() {
        synchronized (this) {
            if (myCommitsRequest != null) {
                myCommitsRequest.cancel(false);
            }
            if (myChangesRequest != null) {
                myChangesRequest.cancel(false);
            }
        }

        CompletableFuture<List<GHCommit>> commitsRequest = myDataProvider.getChangesData().loadCommits();
        CompletableFuture<GitBranchComparisonResult> changesRequest = myDataProvider.getChangesData().loadChanges();
        synchronized (this) {
            myCommitsRequest = commitsRequest;
            myChangesRequest = changesRequest;
        }

        commitsRequest.whenComplete((commits, error) -> {
            if (FutureUtil.isCancellation(error)) {
                return;
            }
            List<GHCommitModel> models = new ArrayList<>();
            if (commits != null) {
                for (GHCommit commit : commits) {
                    models.add(new GHCommitModel(myProject, commit));
                }
            }
            myReviewCommits.set(models);
        });

        changesRequest.whenComplete((changes, error) -> {
            if (error != null) {
                if (!FutureUtil.isCancellation(error)) {
                    myChangesContainer.set(ComputedResult.failure(FutureUtil.unwrap(error)));
                }
                return;
            }
            List<String> commitShas = new ArrayList<>();
            for (GitCommitShaWithPatches commit : changes.getCommits()) {
                commitShas.add(commit.getSha());
            }
            myChangesContainer.set(ComputedResult.success(new CodeReviewChangesContainer(changes.getChanges(), commitShas, changes.getChangesByCommits())));
        });
    }

    @Override
    public GHApiLoadingErrorHandler getChangesLoadingErrorHandler() {
        return myChangesLoadingErrorHandler;
    }

    @Override
    public ObservableValue<List<GHCommitModel>> getReviewCommits() {
        return myReviewCommits;
    }

    @Override
    public ObservableValue<Integer> getSelectedCommitIndex() {
        return mySelectedCommitIndex;
    }

    @Override
    public ObservableValue<@Nullable GHCommitModel> getSelectedCommit() {
        return mySelectedCommit;
    }

    @Override
    public ObservableValue<ComputedResult<GHPRChangeListViewModelBase>> getChangeListVm() {
        return myDelegate.getChangeListVm();
    }

    @Override
    public void selectCommit(int index) {
        GHPRChangeListViewModelBase vm = myDelegate.selectCommit(index);
        if (vm != null) {
            vm.selectChange(null);
        }
    }

    @Override
    public void selectNextCommit() {
        GHPRChangeListViewModelBase vm = myDelegate.selectNextCommit();
        if (vm != null) {
            vm.selectChange(null);
        }
    }

    @Override
    public void selectPreviousCommit() {
        GHPRChangeListViewModelBase vm = myDelegate.selectPreviousCommit();
        if (vm != null) {
            vm.selectChange(null);
        }
    }

    @Override
    public void selectCommit(String sha) {
        GHPRChangeListViewModelBase vm = myDelegate.selectCommit(sha);
        if (vm != null) {
            vm.selectChange(null);
        }
    }

    @Override
    public void selectChange(RefComparisonChange change) {
        ComputedResult<CodeReviewChangesContainer> container = myChangesContainer.get();
        CodeReviewChangesContainer changes = container == null ? null : container.getOrNull();
        String commit = changes == null ? null : changes.getCommitsByChange().get(change);
        GHPRChangeListViewModelBase vm = myDelegate.selectCommit(commit);
        if (vm != null) {
            vm.selectChange(change);
        }
    }

    @Override
    public String commitHash(GHCommitModel commit) {
        return commit.getAbbreviatedOid();
    }
}
