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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.util.ComputedResult;
import consulo.review.util.FutureUtil;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.review.util.PreciseChangesSelection;
import git4idea.GitRemoteBranch;
import git4idea.workingTrees.GitWorkingTreesService;
import org.jetbrains.plugins.github.api.GHRepositoryConnection;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort;
import org.jetbrains.plugins.github.pullrequest.GHPRListViewModel;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRChangesDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewInEditorViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRBranchWidgetViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRInfoViewModel;
import org.jetbrains.plugins.github.util.AcquirableScopedValueOwner;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public abstract class GHPRConnectedProjectViewModelBase implements GHPRConnectedProjectViewModel {
    private static final Logger LOG = Logger.getInstance(GHPRConnectedProjectViewModelBase.class);

    protected final Project myProject;
    protected final Disposable myCs;

    private final String myConnectionId;
    private final GHPRDataContext myDataContext;
    private final GHRepositoryCoordinates myRepository;
    private final GHPRListViewModel myListVm;

    private final MutableObservableValue<@Nullable ComputedResult<@Nullable GHPRIdentifier>> myPrOnCurrentBranch = MutableObservableValue.of(null);
    private final ObservableValue<@Nullable Pair<GHGitRepositoryMapping, GitRemoteBranch>> myHostedRemoteBranch;
    private @Nullable CompletableFuture<?> myPrOnCurrentBranchRequest;
    private final LoadingCache<GHPRIdentifier, AcquirableScopedValueOwner<GHPRViewModelContainer>> myPullRequestsVms;

    protected GHPRConnectedProjectViewModelBase(Project project, Disposable parentCs, GHRepositoryConnection connection) {
        myProject = project;
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);

        myConnectionId = connection.getId();
        myDataContext = connection.getDataContext();
        myRepository = myDataContext.getRepositoryDataService().getRepositoryCoordinates();
        myListVm = new GHPRListViewModel(project, myCs, myDataContext, pr -> {
            viewPullRequest(pr.getPrId(), true);
            openPullRequestTimeline(pr.getPrId(), false);
        });

        myPullRequestsVms = CacheBuilder.newBuilder().build(CacheLoader.from(id -> new AcquirableScopedValueOwner<>(myCs,
            cs -> new GHPRViewModelContainerImpl(project, cs, myDataContext, id,
                prId -> viewPullRequest(prId, true), this::viewPullRequest, this::openPullRequestDiff, this::refreshPrOnCurrentBranch))));

        GHHostedRepositoriesManager repoManager = GHHostedRepositoriesManager.getInstance(project);
        myHostedRemoteBranch = repoManager.findHostedRemoteBranchTrackedByCurrent(connection.getRepo().getGitRepository(), myCs);
        // Re-run the lookup on an explicit refresh and whenever the PR list is reloaded/refreshed, so a PR opened outside
        // the IDE (or otherwise missed by the initial lookup) is picked up on the next refresh without switching branches.
        Disposer.register(myCs, myHostedRemoteBranch.addListener(it -> refreshPrOnCurrentBranch())::run);
        myListVm.addReloadListener(myCs, this::refreshPrOnCurrentBranch);
        refreshPrOnCurrentBranch();
    }

    @Override
    public String getConnectionId() {
        return myConnectionId;
    }

    @Override
    public GHPRDataContext getDataContext() {
        return myDataContext;
    }

    @Override
    public GHRepositoryCoordinates getRepository() {
        return myRepository;
    }

    @Override
    public GHPRListViewModel getListVm() {
        return myListVm;
    }

    @Override
    public ObservableValue<@Nullable ComputedResult<@Nullable GHPRIdentifier>> getPrOnCurrentBranch() {
        return myPrOnCurrentBranch;
    }

    @Override
    public GHPRInfoViewModel acquireInfoViewModel(GHPRIdentifier id, Disposable hostCs) {
        return myPullRequestsVms.getUnchecked(id).acquireValue(hostCs).getInfoVm();
    }

    @Override
    public GHPRReviewInEditorViewModel acquireEditorReviewViewModel(GHPRIdentifier id, Disposable hostCs) {
        return myPullRequestsVms.getUnchecked(id).acquireValue(hostCs).getEditorVm();
    }

    @Override
    public GHPRBranchWidgetViewModel acquireBranchWidgetModel(GHPRIdentifier id, Disposable hostCs) {
        return myPullRequestsVms.getUnchecked(id).acquireValue(hostCs).getBranchWidgetVm();
    }

    @Override
    public GHPRDiffViewModel acquireDiffViewModel(GHPRIdentifier id, Disposable hostCs) {
        return myPullRequestsVms.getUnchecked(id).acquireValue(hostCs).getDiffVm();
    }

    @Override
    public GHPRTimelineViewModel acquireTimelineViewModel(GHPRIdentifier id, Disposable hostCs) {
        return myPullRequestsVms.getUnchecked(id).acquireValue(hostCs).getTimelineVm();
    }

    @Override
    public @Nullable GHPullRequestShort findDetails(GHPRIdentifier id) {
        for (GHPullRequestShort pr : myListVm.getLoadedData().get()) {
            if (pr.getId().equals(id.id())) {
                return pr;
            }
        }
        GHPRDataProvider provider = myDataContext.getDataProviderRepository().findDataProvider(id);
        return provider == null ? null : provider.getDetailsData().getLoadedDetails();
    }

    @Override
    public boolean isCheckedOut(GHPRIdentifier id) {
        ComputedResult<@Nullable GHPRIdentifier> result = myPrOnCurrentBranch.get();
        return result != null && Objects.equals(result.getOrNull(), id);
    }

    @Override
    public boolean getCanCheckoutInNewWorktree() {
        return GitWorkingTreesService.isWorktreeCreationSupported(
            myDataContext.getRepositoryDataService().getRepositoryMapping().getRemote().repository());
    }

    @Override
    public void checkoutPullRequest(GHPRIdentifier id) {
        myDataContext.getDataProviderRepository().getDataProvider(id, myCs).getDetailsData().loadDetails()
            .thenCompose(details -> GHPRBranchesViewModel.fetchAndCheckoutBranch(
                myDataContext.getRepositoryDataService().getRepositoryMapping().getRemote(), details));
    }

    @Override
    public void checkoutPullRequestInNewWorktree(GHPRIdentifier id) {
        myDataContext.getDataProviderRepository().getDataProvider(id, myCs).getDetailsData().loadDetails()
            .thenCompose(details -> GHPRBranchesViewModel.fetchAndCheckoutBranchInNewWorktree(
                myDataContext.getRepositoryDataService().getRepositoryMapping().getRemote(),
                details,
                Pair.create(myRepository, myDataContext.getSecurityService().getAccount())));
    }

    protected void refreshPrOnCurrentBranch() {
        Pair<GHGitRepositoryMapping, GitRemoteBranch> projectAndBranch = myHostedRemoteBranch.get();
        synchronized (this) {
            if (myPrOnCurrentBranchRequest != null) {
                myPrOnCurrentBranchRequest.cancel(false);
                myPrOnCurrentBranchRequest = null;
            }
        }
        if (projectAndBranch == null) {
            myPrOnCurrentBranch.set(ComputedResult.success(null));
            return;
        }
        GHGitRepositoryMapping targetProject = projectAndBranch.getFirst();
        GitRemoteBranch remoteBranch = projectAndBranch.getSecond();
        CompletableFuture<@Nullable GHPRIdentifier> request = myDataContext.getCreationService()
            .findOpenPullRequest(null, targetProject.getRepository().getRepositoryPath(), remoteBranch);
        synchronized (this) {
            myPrOnCurrentBranchRequest = request;
        }
        if (!request.isDone()) {
            myPrOnCurrentBranch.set(ComputedResult.loading());
        }
        request.whenComplete((id, error) -> {
            synchronized (this) {
                if (myPrOnCurrentBranchRequest != request) {
                    return;
                }
            }
            if (error == null) {
                myPrOnCurrentBranch.set(ComputedResult.success(id));
            }
            else if (!FutureUtil.isCancellation(error)) {
                LOG.warn("Could not lookup a pull request for current branch", FutureUtil.unwrap(error));
                myPrOnCurrentBranch.set(ComputedResult.failure(FutureUtil.unwrap(error)));
            }
        });
    }

    @Override
    public void openPullRequestInfoAndTimeline(long number) {
        myDataContext.getDetailsService().findPRId(number).whenCompleteAsync((prId, error) -> {
            // It's an issue ID or doesn't exist
            if (error != null || prId == null) {
                return;
            }
            viewPullRequest(prId, true);
            openPullRequestTimeline(prId, true);
        }, myProject.getUIAccess());
    }

    @Override
    public void openPullRequestInfoAndDiff(GHPRIdentifier id) {
        viewPullRequest(id, true);
        GHPRChangesDataProvider changesData = myDataContext.getDataProviderRepository().getDataProvider(id, myCs).getChangesData();
        changesData.loadChanges()
            .thenCompose(result -> changesData.ensureAllRevisionsFetched().thenApply(it -> result.getChanges()))
            .handle((changes, error) -> {
                if (error != null) {
                    Throwable cause = FutureUtil.unwrap(error);
                    // Changes loader may be cancelled by an unrelated concurrent reload; don't skip opening the diff tab
                    // for that.
                    if (!FutureUtil.isCancellation(cause)) {
                        LOG.warn("Failed to load changes for PR " + id.number() + " to open in diff", cause);
                    }
                    return null;
                }
                return changes;
            })
            .whenCompleteAsync((changes, error) -> {
                if (changes != null && !changes.isEmpty()) {
                    acquireDiffViewModel(id, myCs).showDiffFor(new PreciseChangesSelection(changes, 0));
                }
                openPullRequestDiff(id, true);
            }, myProject.getUIAccess());
    }

    public abstract void viewPullRequest(GHPRIdentifier id, String commitOid);

    public abstract void closeNewPullRequest();
}
