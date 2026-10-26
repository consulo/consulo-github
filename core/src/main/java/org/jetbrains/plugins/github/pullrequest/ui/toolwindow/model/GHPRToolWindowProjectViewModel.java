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
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.review.toolwindow.ReviewToolwindowProjectViewModel;
import consulo.review.toolwindow.ReviewToolwindowTabs;
import consulo.review.toolwindow.ReviewToolwindowTabsStateHolder;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.GHRepositoryConnection;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.pullrequest.data.GHPRFilesManager;
import org.jetbrains.plugins.github.pullrequest.data.GHPRFilesManagerImpl;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModelBase;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowTab;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModelImpl;
import org.jetbrains.plugins.github.ui.util.GHUIUtil;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class GHPRToolWindowProjectViewModel extends GHPRConnectedProjectViewModelBase
    implements ReviewToolwindowProjectViewModel<GHPRToolWindowTab, GHPRToolWindowTabViewModel> {

    private final Runnable myActivate;
    private final GHHostedRepositoriesManager myRepoManager;
    private final GHRepositoryConnection myConnection;
    private final GHPRFilesManager myFilesManager;
    private final LocalizeValue myProjectName;

    private @Nullable GHPRCreateViewModelImpl myCreateVm;

    private final ReviewToolwindowTabsStateHolder<GHPRToolWindowTab, GHPRToolWindowTabViewModel> myTabsHelper = new ReviewToolwindowTabsStateHolder<>();

    public GHPRToolWindowProjectViewModel(Project project, Disposable parentCs, GHRepositoryConnection connection, Runnable activate) {
        super(project, parentCs, connection);
        myActivate = activate;
        myConnection = connection;

        myRepoManager = GHHostedRepositoriesManager.getInstance(project);
        myFilesManager = new GHPRFilesManagerImpl(project, myCs, getRepository());
        List<GHRepositoryCoordinates> allRepos = myRepoManager.getKnownRepositories().stream().map(GHGitRepositoryMapping::getRepository).toList();
        myProjectName = LocalizeValue.of(GHUIUtil.getRepositoryDisplayName(allRepos, getRepository()));

        getDataContext().getDataProviderRepository().addDetailsLoadedListener(myCs,
            details -> project.getUIAccess().give(() -> myFilesManager.updateTimelineFilePresentation(details.getPrId())));

        Disposer.register(myCs, () -> project.getUIAccess().give(myFilesManager::closeAllFiles));
    }

    private synchronized GHPRCreateViewModelImpl getOrCreateCreateVm() {
        if (myCreateVm == null) {
            myCreateVm = new GHPRCreateViewModelImpl(myProject, myCs, myRepoManager, GithubPullRequestsProjectUISettings.getInstance(myProject),
                myConnection.getDataContext(), id -> viewPullRequest(id, true), this::closeNewPullRequest, this::openPullRequestDiff,
                this::refreshPrOnCurrentBranch);
        }
        return myCreateVm;
    }

    @Override
    public synchronized @Nullable GHPRCreateViewModel getCreateVmOrNull() {
        return myCreateVm;
    }

    @Override
    public LocalizeValue getProjectName() {
        return myProjectName;
    }

    @Override
    public ObservableValue<ReviewToolwindowTabs<GHPRToolWindowTab, GHPRToolWindowTabViewModel>> getTabs() {
        return myTabsHelper.getTabs();
    }

    private GHPRToolWindowTabViewModel.PullRequest createVm(GHPRToolWindowTab.PullRequest tab) {
        return new GHPRToolWindowTabViewModel.PullRequest(myCs, this, tab.prId());
    }

    @Override
    public void selectTab(@Nullable GHPRToolWindowTab tab) {
        myTabsHelper.select(tab);
    }

    @Override
    public void closeTab(GHPRToolWindowTab tab) {
        myTabsHelper.close(tab);
    }

    @Override
    public void closeNewPullRequest() {
        GHPRCreateViewModelImpl createVm;
        synchronized (this) {
            myTabsHelper.close(GHPRToolWindowTab.NewPullRequest.INSTANCE);
            createVm = myCreateVm;
            myCreateVm = null;
        }
        if (createVm != null) {
            Disposer.dispose(createVm);
        }
        myProject.getUIAccess().give(myFilesManager::closeNewPrFile);
    }

    @Override
    public void createPullRequest(boolean requestFocus) {
        synchronized (this) {
            myTabsHelper.showTab(GHPRToolWindowTab.NewPullRequest.INSTANCE, GHPRToolWindowTabViewModel.NewPullRequest.class,
                tab -> new GHPRToolWindowTabViewModel.NewPullRequest(getOrCreateCreateVm()), vm -> {
                    if (requestFocus) {
                        vm.requestFocus();
                    }
                });
        }
    }

    @Override
    public void viewList(boolean requestFocus) {
        selectTab(null);
        if (requestFocus) {
            getListVm().requestFocus();
        }
    }

    @Override
    public void viewPullRequest(GHPRIdentifier id, boolean requestFocus) {
        if (requestFocus) {
            myActivate.run();
        }
        reloadOpenedPullRequestDetails(id);
        myTabsHelper.showTab(new GHPRToolWindowTab.PullRequest(id), GHPRToolWindowTabViewModel.PullRequest.class, this::createVm, vm -> {
            if (requestFocus) {
                vm.requestFocus();
            }
        });
    }

    @Override
    public void viewPullRequest(GHPRIdentifier id, String commitOid) {
        myActivate.run();
        reloadOpenedPullRequestDetails(id);
        myTabsHelper.showTab(new GHPRToolWindowTab.PullRequest(id), GHPRToolWindowTabViewModel.PullRequest.class, this::createVm,
            vm -> vm.selectCommit(commitOid));
    }

    private void reloadOpenedPullRequestDetails(GHPRIdentifier id) {
        GHPRDataProvider provider = getDataContext().getDataProviderRepository().findDataProvider(id);
        if (provider == null) {
            return;
        }
        GHPRDetailsDataProvider detailsData = provider.getDetailsData();
        if (detailsData.getLoadedDetails() == null) {
            return;
        }
        detailsData.signalDetailsNeedReload();
        detailsData.signalMergeabilityNeedsReload();
    }

    @Override
    public void openPullRequestTimeline(GHPRIdentifier id, boolean requestFocus) {
        myProject.getUIAccess().give(() -> myFilesManager.createAndOpenTimelineFile(id, requestFocus));
    }

    @Override
    public void openPullRequestDiff(@Nullable GHPRIdentifier id, boolean requestFocus) {
        myProject.getUIAccess().give(() -> myFilesManager.createAndOpenDiffFile(id, requestFocus));
    }
}
