// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.details.model.CodeReviewChangeListViewModelBase;
import consulo.versionControlSystem.review.util.ChangesSelection;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModelsImpl;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletionImpl;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModelBase;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffViewModelImpl;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewInEditorViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewInEditorViewModelImpl;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRBranchWidgetViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRBranchWidgetViewModelImpl;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper;
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineViewModelImpl;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRInfoViewModel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class GHPRViewModelContainerImpl implements GHPRViewModelContainer {
    private final Project myProject;
    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GHPRIdentifier myPullRequestId;
    private final Consumer<GHPRIdentifier> myViewPullRequest;
    private final BiConsumer<GHPRIdentifier, String> myViewPullRequestOnCommit;
    private final BiConsumer<@Nullable GHPRIdentifier, Boolean> myOpenPullRequestDiff;
    private final Runnable myRefreshPrOnCurrentBranch;

    private final GHPRDataProvider myDataProvider;
    private final GithubPullRequestsProjectUISettings mySettings;

    private @Nullable GHPRInfoViewModel myInfoVm;
    private @Nullable GHPRReviewViewModelHelper myReviewVmHelper;
    private @Nullable GHViewModelWithTextCompletion myCommentTextCompletionVm;
    private @Nullable GHPRTimelineViewModelImpl myTimelineVm;
    private @Nullable GHPRThreadsViewModelsImpl myThreadsVms;
    private @Nullable GHPRDiffViewModelImpl myDiffVm;
    private @Nullable ChangesSelection myLastDiffSelectionRequest;
    private @Nullable GHPRReviewBranchStateSharedViewModel myBranchStateVm;
    private @Nullable GHPRBranchWidgetViewModelImpl myBranchWidgetVm;
    private @Nullable GHPRReviewInEditorViewModelImpl myEditorVm;

    GHPRViewModelContainerImpl(Project project,
                               Disposable parentCs,
                               GHPRDataContext dataContext,
                               GHPRIdentifier pullRequestId,
                               Consumer<GHPRIdentifier> viewPullRequest,
                               BiConsumer<GHPRIdentifier, String> viewPullRequestOnCommit,
                               BiConsumer<@Nullable GHPRIdentifier, Boolean> openPullRequestDiff,
                               Runnable refreshPrOnCurrentBranch) {
        myProject = project;
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myDataContext = dataContext;
        myPullRequestId = pullRequestId;
        myViewPullRequest = viewPullRequest;
        myViewPullRequestOnCommit = viewPullRequestOnCommit;
        myOpenPullRequestDiff = openPullRequestDiff;
        myRefreshPrOnCurrentBranch = refreshPrOnCurrentBranch;

        myDataProvider = dataContext.getDataProviderRepository().getDataProvider(pullRequestId, myCs);
        mySettings = GithubPullRequestsProjectUISettings.getInstance(project);

        myDataProvider.getDetailsData().addStateChangeListener(myCs, refreshPrOnCurrentBranch);
    }

    @Override
    public synchronized GHPRInfoViewModel getInfoVm() {
        if (myInfoVm == null) {
            myInfoVm = new GHPRInfoViewModel(myProject, myCs, myDataContext, myDataProvider, myOpenPullRequestDiff);
            setupInfoVm(myInfoVm);
        }
        return myInfoVm;
    }

    synchronized GHPRReviewViewModelHelper getReviewVmHelper() {
        if (myReviewVmHelper == null) {
            myReviewVmHelper = new GHPRReviewViewModelHelper(myCs, myProject, myDataProvider);
        }
        return myReviewVmHelper;
    }

    synchronized GHViewModelWithTextCompletion getCommentTextCompletionVm() {
        if (myCommentTextCompletionVm == null) {
            myCommentTextCompletionVm = new GHViewModelWithTextCompletionImpl(myCs,
                myDataContext.getMentionableUsersProvider(),
                myDataProvider.getReviewData(),
                myDataContext.getAvatarIconsProvider());
        }
        return myCommentTextCompletionVm;
    }

    @Override
    public synchronized GHPRTimelineViewModel getTimelineVm() {
        if (myTimelineVm == null) {
            GHPRTimelineViewModelImpl vm = new GHPRTimelineViewModelImpl(myProject, myCs, myDataContext, myDataProvider, getCommentTextCompletionVm());
            vm.addShowCommitRequestListener(myCs, oid -> myViewPullRequestOnCommit.accept(myPullRequestId, oid));
            vm.addShowDiffRequestListener(myCs, selection -> {
                getDiffVm().showDiffFor(selection);
                myOpenPullRequestDiff.accept(myPullRequestId, true);
            });
            myTimelineVm = vm;
        }
        return myTimelineVm;
    }

    private synchronized GHPRReviewBranchStateSharedViewModel getBranchStateVm() {
        if (myBranchStateVm == null) {
            myBranchStateVm = new GHPRReviewBranchStateSharedViewModel(myCs, myDataContext, myDataProvider);
        }
        return myBranchStateVm;
    }

    @Override
    public synchronized GHPRBranchWidgetViewModel getBranchWidgetVm() {
        if (myBranchWidgetVm == null) {
            myBranchWidgetVm = new GHPRBranchWidgetViewModelImpl(myProject, myCs, mySettings, myDataProvider, getBranchStateVm(), getReviewVmHelper(),
                myPullRequestId, myViewPullRequest);
        }
        return myBranchWidgetVm;
    }

    @Override
    public synchronized GHPRReviewInEditorViewModel getEditorVm() {
        if (myEditorVm == null) {
            myEditorVm = new GHPRReviewInEditorViewModelImpl(myProject, myCs, mySettings, myDataContext, myDataProvider, getBranchStateVm(),
                getThreadsVms(), selection -> {
                emitDiffSelectionRequest(selection);
                myOpenPullRequestDiff.accept(myPullRequestId, true);
            });
        }
        return myEditorVm;
    }

    synchronized GHPRThreadsViewModelsImpl getThreadsVms() {
        if (myThreadsVms == null) {
            myThreadsVms = new GHPRThreadsViewModelsImpl(myProject, myCs, myDataContext, myDataProvider, getCommentTextCompletionVm());
        }
        return myThreadsVms;
    }

    @Override
    public synchronized GHPRDiffViewModel getDiffVm() {
        if (myDiffVm == null) {
            GHPRDiffViewModelImpl vm = new GHPRDiffViewModelImpl(myProject, myCs, myDataContext, myDataProvider, getReviewVmHelper(), getThreadsVms());
            myDiffVm = vm;
            setupDiffVm(vm);
        }
        return myDiffVm;
    }

    private void emitDiffSelectionRequest(ChangesSelection selection) {
        GHPRDiffViewModelImpl diffVm;
        synchronized (this) {
            myLastDiffSelectionRequest = selection;
            diffVm = myDiffVm;
        }
        if (diffVm != null) {
            diffVm.showDiffFor(selection);
        }
    }

    private void setupInfoVm(GHPRInfoViewModel infoVm) {
        AtomicReference<@Nullable Disposable> detailsScope = new AtomicReference<>();
        AtomicReference<@Nullable Disposable> changeListScope = new AtomicReference<>();
        Consumer<ComputedResult<GHPRDetailsViewModel>> onDetails = detailsVmResult -> {
            disposeScope(changeListScope);
            disposeScope(detailsScope);
            GHPRDetailsViewModel detailsVm = detailsVmResult.getOrNull();
            if (detailsVm == null) {
                return;
            }
            Disposable cs = newScope(detailsScope, "GHPRViewModelContainerImpl.details");
            ObservableValue<ComputedResult<GHPRChangeListViewModelBase>> changeListVm = detailsVm.getChangesVm().getChangeListVm();
            Consumer<ComputedResult<GHPRChangeListViewModelBase>> onChangeList = result -> {
                disposeScope(changeListScope);
                CodeReviewChangeListViewModelBase vm = result.getOrNull();
                if (vm == null) {
                    return;
                }
                Disposable listCs = newScope(changeListScope, "GHPRViewModelContainerImpl.changeList");
                vm.handleSelection(listCs, it -> {
                    if (it != null) {
                        emitDiffSelectionRequest(it);
                    }
                });
            };
            Disposer.register(cs, changeListVm.addListener(onChangeList)::run);
            onChangeList.accept(changeListVm.get());
        };
        Disposer.register(myCs, infoVm.getDetailsVm().addListener(onDetails)::run);
        onDetails.accept(infoVm.getDetailsVm().get());
    }

    private void setupDiffVm(GHPRDiffViewModelImpl diffVm) {
        ChangesSelection pending;
        synchronized (this) {
            pending = myLastDiffSelectionRequest;
        }
        if (pending != null) {
            diffVm.showDiffFor(pending);
        }

        diffVm.handleSelection(myCs, it -> {
            List<RefComparisonChange> list = it.getList();
            int index = it.getSelectedIndex();
            if (index < 0 || index >= list.size()) {
                return;
            }
            RefComparisonChange change = list.get(index);
            GHPRInfoViewModel infoVm;
            synchronized (this) {
                infoVm = myInfoVm;
            }
            if (infoVm != null) {
                GHPRDetailsViewModel detailsVm = infoVm.getDetailsVm().get().getOrNull();
                if (detailsVm != null) {
                    detailsVm.getChangesVm().selectChange(change);
                }
            }
        });
    }

    private Disposable newScope(AtomicReference<@Nullable Disposable> holder, String name) {
        Disposable cs = Disposable.newDisposable(name);
        Disposer.register(myCs, cs);
        holder.set(cs);
        return cs;
    }

    private static void disposeScope(AtomicReference<@Nullable Disposable> holder) {
        Disposable previous = holder.getAndSet(null);
        if (previous != null) {
            Disposer.dispose(previous);
        }
    }
}
