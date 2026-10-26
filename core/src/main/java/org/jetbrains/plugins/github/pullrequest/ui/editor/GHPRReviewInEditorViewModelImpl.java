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

import consulo.diff.util.Side;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.diff.DiscussionsViewOption;
import consulo.review.editor.CodeReviewLineRange;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.action.VcsContextFactory;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.diff.DiffLineLocation;
import consulo.versionControlSystem.review.util.ChangesSelection;
import consulo.versionControlSystem.review.util.PreciseChangesSelection;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import git4idea.changes.GitBranchComparisonResult;
import git4idea.changes.GitTextFilePatchWithHistory;
import git4idea.repo.GitRepository;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRReviewBranchStateSharedViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRThreadsViewModels;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public class GHPRReviewInEditorViewModelImpl implements GHPRReviewInEditorViewModel {
    private static final Logger LOG = Logger.getInstance(GHPRReviewInEditorViewModelImpl.class);

    private final Project myProject;
    private final Disposable myCs;
    private final GithubPullRequestsProjectUISettings mySettings;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;
    private final GHPRReviewBranchStateSharedViewModel mySharedBranchVm;
    private final GHPRThreadsViewModels myThreadsVm;
    private final Consumer<ChangesSelection> myShowDiff;
    private final GitRepository myRepository;

    private final MutableObservableValue<DiscussionsViewOption> myDiscussionsViewOptionState =
        MutableObservableValue.of(DiscussionsViewOption.UNRESOLVED_ONLY);
    private final MutableObservableValue<DiscussionsViewOption> myDiscussionsViewOption;

    private final ComputationState<GitBranchComparisonResult> myChangesComputationState;

    /**
     * Mappings of all threads in the current review.
     * These reflect the visibility and locations of all threads in the review as applied to in-file reviews.
     */
    private final MutableObservableValue<Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData>> myMappedThreads;

    private final Map<FilePath, FileViewModelHolder> myFilesVmsMap = new HashMap<>();

    public GHPRReviewInEditorViewModelImpl(Project project,
                                           Disposable parentCs,
                                           GithubPullRequestsProjectUISettings settings,
                                           GHPRDataContext dataContext,
                                           GHPRDataProvider dataProvider,
                                           GHPRReviewBranchStateSharedViewModel sharedBranchVm,
                                           GHPRThreadsViewModels threadsVm,
                                           Consumer<ChangesSelection> showDiff) {
        myProject = project;
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        mySettings = settings;
        myDataContext = dataContext;
        myDataProvider = dataProvider;
        mySharedBranchVm = sharedBranchVm;
        myThreadsVm = threadsVm;
        myShowDiff = showDiff;
        myRepository = dataContext.getRepositoryDataService().getRepositoryMapping().getGitRepository();

        myDiscussionsViewOption = MutableObservableValue.of(calcDiscussionsViewOption());
        Disposer.register(myCs, settings.getEditorReviewEnabledState().addListener(it -> myDiscussionsViewOption.set(calcDiscussionsViewOption()))::run);
        Disposer.register(myCs, myDiscussionsViewOptionState.addListener(it -> myDiscussionsViewOption.set(calcDiscussionsViewOption()))::run);

        myChangesComputationState = dataProvider.getChangesData().changesComputationState(myCs);
        Disposer.register(myCs, myChangesComputationState.addListener(result -> {
            if (result != null) {
                result.onFailure(error -> LOG.warn("Couldn't load changes for PR " + dataProvider.getId().number(), error));
            }
        })::run);

        myMappedThreads = MutableObservableValue.of(calcMappedThreads());
        Disposer.register(myCs, threadsVm.getThreadMappingData().addListener(it -> myMappedThreads.set(calcMappedThreads()))::run);
        Disposer.register(myCs, myDiscussionsViewOption.addListener(it -> myMappedThreads.set(calcMappedThreads()))::run);
    }

    private DiscussionsViewOption calcDiscussionsViewOption() {
        boolean enabled = mySettings.getEditorReviewEnabledState().get();
        return !enabled ? DiscussionsViewOption.DONT_SHOW : myDiscussionsViewOptionState.get();
    }

    private Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData> calcMappedThreads() {
        DiscussionsViewOption viewOption = myDiscussionsViewOption.get();
        Map<String, MappedGHPRReviewEditorThreadViewModel.MappingData> result = new LinkedHashMap<>();
        for (Map.Entry<String, GHPRThreadsViewModels.ThreadMappingData> entry : myThreadsVm.getThreadMappingData().get().entrySet()) {
            GHPRThreadsViewModels.ThreadMappingData mappingData = entry.getValue();
            boolean isVisible = !mappingData.threadData().isOutdated() && GHPullRequestReviewThread.isVisible(mappingData.threadData(), viewOption);

            GitTextFilePatchWithHistory diffData = mappingData.diffData();
            if (diffData == null) {
                result.put(entry.getKey(), new MappedGHPRReviewEditorThreadViewModel.MappingData(isVisible, mappingData.change(), null));
                continue;
            }
            CodeReviewLineRange commentRange = GHPullRequestReviewThread.mapToInEditorRange(mappingData.threadData(), diffData);

            result.put(entry.getKey(), new MappedGHPRReviewEditorThreadViewModel.MappingData(isVisible, mappingData.change(), commentRange));
        }
        return Map.copyOf(result);
    }

    @Override
    public ObservableValue<DiscussionsViewOption> getDiscussionsViewOption() {
        return myDiscussionsViewOption;
    }

    @Override
    public ObservableValue<Boolean> getUpdateRequired() {
        return mySharedBranchVm.getUpdateRequired();
    }

    @Override
    public void updateBranch() {
        mySharedBranchVm.updateBranch();
    }

    /**
     * A view model for {@code file} review
     */
    @Override
    public ObservableValue<@Nullable GHPRReviewFileEditorViewModel> getViewModelFor(VirtualFile file, Disposable subscriberCs) {
        if (!file.isValid() || file.isDirectory() || !VirtualFileUtil.isAncestor(myRepository.getRoot(), file, true)) {
            return ObservableValue.of(null);
        }
        FilePath filePath = VcsContextFactory.getInstance().createFilePathOn(file);

        FileViewModelHolder holder;
        synchronized (myFilesVmsMap) {
            holder = myFilesVmsMap.computeIfAbsent(filePath, FileViewModelHolder::new);
        }
        holder.subscribe(subscriberCs);
        return holder.getState();
    }

    @Override
    public void setDiscussionsViewOption(DiscussionsViewOption viewOption) {
        if (viewOption == DiscussionsViewOption.DONT_SHOW) {
            mySettings.setEditorReviewEnabled(false);
        }
        else {
            mySettings.setEditorReviewEnabled(true);
            myDiscussionsViewOptionState.set(viewOption);
        }
    }

    private void showDiff(RefComparisonChange changeToShow, @Nullable Integer lineIdx) {
        ComputedResult<GitBranchComparisonResult> result = myChangesComputationState.get();
        GitBranchComparisonResult changesBundle = result == null ? null : result.getOrNull();
        if (changesBundle == null) {
            return;
        }
        ChangesSelection selection = createSelection(changesBundle, changeToShow, lineIdx);
        if (selection == null) {
            return;
        }
        myShowDiff.accept(selection);
    }

    private GHPRReviewFileEditorViewModelImpl createFileVm(Disposable cs, RefComparisonChange change, GitTextFilePatchWithHistory diffData) {
        return new GHPRReviewFileEditorViewModelImpl(myProject, cs, myDataContext, myDataProvider,
            this,
            change, diffData,
            myThreadsVm, myMappedThreads,
            this::showDiff);
    }

    private static @Nullable ChangesSelection createSelection(GitBranchComparisonResult changesBundle,
                                                              RefComparisonChange change,
                                                              @Nullable Integer lineIdx) {
        DiffLineLocation location = lineIdx == null ? null : new DiffLineLocation(Side.RIGHT, lineIdx);
        if (changesBundle.getChanges().contains(change)) {
            return new PreciseChangesSelection(changesBundle.getChanges(), change, location);
        }
        else {
            List<RefComparisonChange> changes = changesBundle.getChangesByCommits().get(change.revisionNumberAfter().asString());
            return changes == null ? null : new PreciseChangesSelection(changes, change, location);
        }
    }

    private final class FileViewModelHolder {
        private final FilePath myFilePath;
        private final MutableObservableValue<@Nullable GHPRReviewFileEditorViewModel> myState = MutableObservableValue.of(null);

        private int mySubscribersCount;
        private @Nullable Disposable mySubscriptionCs;
        private @Nullable Disposable myVmCs;
        private @Nullable String myChangesKey;

        FileViewModelHolder(FilePath filePath) {
            myFilePath = filePath;
        }

        ObservableValue<@Nullable GHPRReviewFileEditorViewModel> getState() {
            return myState;
        }

        void subscribe(Disposable subscriberCs) {
            Disposable subscriptionCs;
            synchronized (this) {
                mySubscribersCount++;
                if (mySubscriptionCs != null) {
                    subscriptionCs = null;
                }
                else {
                    subscriptionCs = Disposable.newDisposable("GHPRReviewFileEditorViewModel subscription");
                    if (!Disposer.tryRegister(myCs, subscriptionCs)) {
                        mySubscribersCount--;
                        return;
                    }
                    mySubscriptionCs = subscriptionCs;
                }
            }
            Disposer.register(subscriberCs, this::unsubscribe);
            if (subscriptionCs != null) {
                Disposer.register(subscriptionCs, myChangesComputationState.addListener(this::update)::run);
                update(myChangesComputationState.get());
            }
        }

        private void unsubscribe() {
            Disposable toDispose;
            synchronized (this) {
                mySubscribersCount--;
                if (mySubscribersCount > 0 || mySubscriptionCs == null) {
                    return;
                }
                toDispose = mySubscriptionCs;
                mySubscriptionCs = null;
                myVmCs = null;
                myChangesKey = null;
            }
            Disposer.dispose(toDispose);
            myState.set(null);
        }

        private void update(@Nullable ComputedResult<GitBranchComparisonResult> result) {
            GitBranchComparisonResult parsedChanges = result == null ? null : result.getOrNull();
            if (parsedChanges == null) {
                return;
            }
            String key = parsedChanges.getBaseSha() + parsedChanges.getHeadSha() + parsedChanges.getMergeBaseSha();
            Disposable previousVmCs;
            Disposable vmCs;
            synchronized (this) {
                Disposable subscriptionCs = mySubscriptionCs;
                if (subscriptionCs == null || Objects.equals(myChangesKey, key)) {
                    return;
                }
                myChangesKey = key;
                previousVmCs = myVmCs;
                vmCs = Disposable.newDisposable("GHPRReviewFileEditorViewModel");
                Disposer.register(subscriptionCs, vmCs);
                myVmCs = vmCs;
            }
            if (previousVmCs != null) {
                Disposer.dispose(previousVmCs);
            }

            RefComparisonChange change = null;
            for (RefComparisonChange candidate : parsedChanges.getChanges()) {
                if (Objects.equals(candidate.filePathAfter(), myFilePath)) {
                    change = candidate;
                    break;
                }
            }
            if (change == null) {
                myState.set(null);
                return;
            }
            GitTextFilePatchWithHistory diffData = parsedChanges.getPatchesByChange().get(change);
            if (diffData == null) {
                LOG.info("Diff data not found for change " + change);
                myState.set(null);
                return;
            }
            myState.set(createFileVm(vmCs, change, diffData));
        }
    }
}
