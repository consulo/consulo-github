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
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.util.ComputedResult;
import consulo.review.util.FutureUtil;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.log.VcsCommitMetadata;
import consulo.versionControlSystem.review.details.model.CodeReviewChangeList;
import consulo.versionControlSystem.review.details.model.CodeReviewChangeListViewModelBase;
import consulo.versionControlSystem.review.details.model.CodeReviewChangesViewModel;
import consulo.versionControlSystem.review.details.model.CodeReviewCommitsChangesStateHandler;
import consulo.versionControlSystem.review.details.model.CodeReviewGroupingChangeListViewModel;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

public class GHPRCreateChangesViewModel implements CodeReviewChangesViewModel<VcsCommitMetadata> {
    private final Project myProject;
    private final GithubPullRequestsProjectUISettings mySettings;
    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GitRemoteBranch myBaseBranch;
    private final GitBranch myHeadBranch;
    private final List<VcsCommitMetadata> myCommits;
    private final BiConsumer<@Nullable GHPRIdentifier, Boolean> myOpenPullRequestDiff;

    private final ObservableValue<List<VcsCommitMetadata>> myReviewCommits;

    private final CodeReviewCommitsChangesStateHandler<VcsCommitMetadata, CommitChangesViewModel> myStateHandler;

    private final ObservableValue<Integer> mySelectedCommitIndex;

    GHPRCreateChangesViewModel(Project project,
                               GithubPullRequestsProjectUISettings settings,
                               Disposable parentCs,
                               GHPRDataContext dataContext,
                               GitRemoteBranch baseBranch,
                               GitBranch headBranch,
                               List<VcsCommitMetadata> commits,
                               BiConsumer<@Nullable GHPRIdentifier, Boolean> openPullRequestDiff) {
        myProject = project;
        mySettings = settings;
        myDataContext = dataContext;
        myBaseBranch = baseBranch;
        myHeadBranch = headBranch;
        myCommits = commits;
        myOpenPullRequestDiff = openPullRequestDiff;

        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);

        myReviewCommits = MutableObservableValue.of(commits);

        myStateHandler = CodeReviewCommitsChangesStateHandler.create(myCs, commits, (scope, commit) -> {
            CommitChangesViewModel vm = new CommitChangesViewModel(scope, commit);
            vm.selectChange(null);
            return vm;
        });

        mySelectedCommitIndex = myStateHandler.getSelectedCommit().map(myCommits::indexOf);
    }

    @Override
    public ObservableValue<List<VcsCommitMetadata>> getReviewCommits() {
        return myReviewCommits;
    }

    @Override
    public ObservableValue<Integer> getSelectedCommitIndex() {
        return mySelectedCommitIndex;
    }

    @Override
    public ObservableValue<@Nullable VcsCommitMetadata> getSelectedCommit() {
        return myStateHandler.getSelectedCommit();
    }

    public ObservableValue<CommitChangesViewModel> getCommitChangesVm() {
        return myStateHandler.getChangeListVm();
    }

    @Override
    public void selectCommit(int index) {
        CommitChangesViewModel vm = myStateHandler.selectCommit(index);
        if (vm != null) {
            vm.selectChange(null);
        }
    }

    @Override
    public void selectNextCommit() {
        CommitChangesViewModel vm = myStateHandler.selectNextCommit();
        if (vm != null) {
            vm.selectChange(null);
        }
    }

    @Override
    public void selectPreviousCommit() {
        CommitChangesViewModel vm = myStateHandler.selectPreviousCommit();
        if (vm != null) {
            vm.selectChange(null);
        }
    }

    @Override
    public String commitHash(VcsCommitMetadata commit) {
        return commit.getId().toShortString();
    }

    public class CommitChangesViewModel {
        private final Disposable myCommitCs;

        private final MutableObservableValue<ComputedResult<CodeReviewChangeListViewModelBase>> myChangeListVm =
            MutableObservableValue.of(ComputedResult.loading());

        private final Object myLock = new Object();
        private boolean myHasSelectionRequest;
        private @Nullable RefComparisonChange mySelectionRequest;
        private @Nullable GHPRCreateChangeListViewModel myCurrentVm;

        CommitChangesViewModel(Disposable cs, @Nullable VcsCommitMetadata commit) {
            myCommitCs = cs;

            CompletableFuture<Collection<RefComparisonChange>> loading = loadChanges(commit);
            Disposer.register(cs, () -> loading.cancel(false));
            loading.whenComplete((changes, error) -> {
                if (error != null) {
                    if (!FutureUtil.isCancellation(error)) {
                        myChangeListVm.set(ComputedResult.failure(FutureUtil.unwrap(error)));
                    }
                    return;
                }
                GHPRCreateChangeListViewModel vm;
                synchronized (myLock) {
                    if (Disposer.isDisposed(myCommitCs)) {
                        return;
                    }
                    vm = createChangesVm(commit, changes);
                    myCurrentVm = vm;
                    if (myHasSelectionRequest) {
                        vm.selectChange(mySelectionRequest);
                    }
                }
                myChangeListVm.set(ComputedResult.success(vm));
            });
        }

        public ObservableValue<ComputedResult<CodeReviewChangeListViewModelBase>> getChangeListVm() {
            return myChangeListVm;
        }

        private GHPRCreateChangeListViewModel createChangesVm(@Nullable VcsCommitMetadata commit, Collection<RefComparisonChange> changes) {
            return new GHPRCreateChangeListViewModel(myCommitCs, commit, List.copyOf(changes));
        }

        private CompletableFuture<Collection<RefComparisonChange>> loadChanges(@Nullable VcsCommitMetadata commit) {
            if (commit == null) {
                return myDataContext.getCreationService().getDiff(myBaseBranch, myHeadBranch);
            }
            else {
                return myDataContext.getCreationService().getDiff(commit);
            }
        }

        public void selectChange(@Nullable RefComparisonChange change) {
            GHPRCreateChangeListViewModel vm;
            synchronized (myLock) {
                myHasSelectionRequest = true;
                mySelectionRequest = change;
                vm = myCurrentVm;
            }
            if (vm != null) {
                vm.selectChange(change);
            }
        }
    }

    private class GHPRCreateChangeListViewModel extends CodeReviewChangeListViewModelBase implements CodeReviewGroupingChangeListViewModel {
        GHPRCreateChangeListViewModel(Disposable parentCs, @Nullable VcsCommitMetadata commit, List<RefComparisonChange> changes) {
            super(parentCs, new CodeReviewChangeList(commit == null ? null : commit.getId().asString(), changes));
        }

        @Override
        public ObservableValue<Set<String>> getGrouping() {
            return mySettings.getChangesGroupingState();
        }

        @Override
        public void setGrouping(Collection<String> grouping) {
            mySettings.setChangesGrouping(Set.copyOf(grouping));
        }

        @Override
        public Project getProject() {
            return myProject;
        }

        @Override
        public void showDiffPreview() {
            myOpenPullRequestDiff.accept(null, true);
        }

        @Override
        public void showDiff() {
            showDiffPreview(); // implement
        }
    }
}
