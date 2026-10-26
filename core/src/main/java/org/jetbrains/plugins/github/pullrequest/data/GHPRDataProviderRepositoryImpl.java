// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.pullrequest.data;

import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.proxy.EventDispatcher;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.versionControlSystem.log.Hash;
import git4idea.GitRemoteBranch;
import git4idea.remote.GitRemoteUrlCoordinates;
import git4idea.remote.hosting.GitAsyncExtensions;
import git4idea.remote.hosting.GitRemoteBranchesUtil;
import git4idea.remote.hosting.HostedGitRepositoryRemote;
import git4idea.remote.hosting.HostedGitRepositoryRemoteBranch;
import git4idea.repo.GitRepoInfo;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRBranchesRefs;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRChangesDataProviderImpl;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRCommentsDataProviderImpl;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataOperationsListener;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProviderImpl;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProviderImpl;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProviderImpl;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRTimelineDataProviderImpl;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRViewedStateDataProviderImpl;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRChangesService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRDetailsService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRFilesService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRTimelineService;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel;
import org.jetbrains.plugins.github.util.AcquirableScopedValueOwner;
import org.jspecify.annotations.Nullable;

import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class GHPRDataProviderRepositoryImpl implements GHPRDataProviderRepository {
    private final Disposable myCs;
    private final GHPRRepositoryDataService myRepositoryService;
    private final GHPRDetailsService myDetailsService;
    private final GHPRTimelineService myTimelineService;
    private final GHPRReviewService myReviewService;
    private final GHPRFilesService myFilesService;
    private final GHPRCommentService myCommentService;
    private final GHPRChangesService myChangesService;

    private final Map<GHPRIdentifier, AcquirableScopedValueOwner<GHPRDataProvider>> myCache = new HashMap<>();
    private final EventDispatcher<DetailsLoadedListener> myProviderDetailsLoadedEventDispatcher = EventDispatcher.create(DetailsLoadedListener.class);

    public GHPRDataProviderRepositoryImpl(Disposable parentCs,
                                          GHPRRepositoryDataService repositoryService,
                                          GHPRDetailsService detailsService,
                                          GHPRTimelineService timelineService,
                                          GHPRReviewService reviewService,
                                          GHPRFilesService filesService,
                                          GHPRCommentService commentService,
                                          GHPRChangesService changesService) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myRepositoryService = repositoryService;
        myDetailsService = detailsService;
        myTimelineService = timelineService;
        myReviewService = reviewService;
        myFilesService = filesService;
        myCommentService = commentService;
        myChangesService = changesService;
    }

    @RequiredUIAccess
    @Override
    public GHPRDataProvider getDataProvider(GHPRIdentifier id, Disposable hostCs) {
        return myCache.computeIfAbsent(id, it -> new AcquirableScopedValueOwner<>(myCs, cs -> createDataProvider(cs, id))).acquireValue(hostCs);
    }

    @RequiredUIAccess
    @Override
    public @Nullable GHPRDataProvider findDataProvider(GHPRIdentifier id) {
        AcquirableScopedValueOwner<GHPRDataProvider> owner = myCache.get(id);
        return owner == null ? null : owner.getValue();
    }

    private GHPRDataProvider createDataProvider(Disposable cs, GHPRIdentifier id) {
        EventDispatcher<GHPRDataOperationsListener> messageBus = EventDispatcher.create(GHPRDataOperationsListener.class);

        Disposable providerCs = Disposable.newDisposable(GHPRDataProviderImpl.class.getName());
        Disposer.register(cs, providerCs);
        GHPRDetailsDataProviderImpl detailsData = new GHPRDetailsDataProviderImpl(providerCs, myDetailsService, id, messageBus);
        Runnable unsubscribeLoaded = detailsData.getLoadedDetailsState().addListener(details -> {
            if (details != null) {
                myProviderDetailsLoadedEventDispatcher.getMulticaster().onDetailsLoaded(details);
            }
        });
        Disposer.register(providerCs, unsubscribeLoaded::run);

        launchDetailsReloadOnHeadRevChange(providerCs, detailsData, myRepositoryService.getRemoteCoordinates());

        GHPRTimelineDataProviderImpl timelineData = new GHPRTimelineDataProviderImpl(providerCs, myTimelineService, messageBus, id);
        GHPRChangesDataProviderImpl changesData = new GHPRChangesDataProviderImpl(providerCs, myChangesService,
            () -> detailsData.loadDetails().thenApply(GHPRDataProviderRepositoryImpl::getRefs), id);
        GHPRReviewDataProviderImpl reviewData = new GHPRReviewDataProviderImpl(providerCs, myReviewService, changesData, id, messageBus);
        GHPRViewedStateDataProviderImpl viewedStateData = new GHPRViewedStateDataProviderImpl(providerCs, myFilesService, id);
        GHPRCommentsDataProviderImpl commentsData = new GHPRCommentsDataProviderImpl(myCommentService, id, messageBus);

        // filterNotNull(): without it, the first details load (null -> details) itself counts as a "refs
        // changed" transition, spuriously cancelling a changes load already in flight for those same refs.
        GHPRBranchesRefs[] lastRefs = new GHPRBranchesRefs[1];
        Runnable unsubscribeRefs = detailsData.getLoadedDetailsState().addListener(details -> {
            if (details == null) {
                return;
            }
            GHPRBranchesRefs refs = getRefs(details);
            GHPRBranchesRefs previous = lastRefs[0];
            lastRefs[0] = refs;
            if (previous != null && !previous.equals(refs)) {
                changesData.signalChangesNeedReload();
                viewedStateData.signalViewedStateNeedsReload();
            }
        });
        Disposer.register(providerCs, unsubscribeRefs::run);

        messageBus.addListener(new GHPRDataOperationsListener() {
            @Override
            public void onReviewsChanged() {
                detailsData.signalDetailsNeedReload();
                detailsData.signalMergeabilityNeedsReload();
            }
        }, providerCs);

        return new GHPRDataProviderImpl(id, detailsData, timelineData, changesData, commentsData, reviewData, viewedStateData);
    }

    @Override
    public void addDetailsLoadedListener(Disposable hostCs, Consumer<GHPullRequest> listener) {
        myProviderDetailsLoadedEventDispatcher.addListener(listener::accept, hostCs);
    }

    @FunctionalInterface
    private interface DetailsLoadedListener extends EventListener {
        void onDetailsLoaded(GHPullRequest details);
    }

    /**
     * Signal details reload when PR branches hashes are changed (if there are known remote branches corresponding to PR branches)
     */
    private static void launchDetailsReloadOnHeadRevChange(Disposable cs,
                                                           GHPRDetailsDataProviderImpl detailsData,
                                                           GitRemoteUrlCoordinates gitRemoteUrlCoordinates) {
        Object lock = new Object();
        HostedGitRepositoryRemoteBranch[] descriptor = new HostedGitRepositoryRemoteBranch[1];
        GitRepoInfo[] repoInfo = new GitRepoInfo[1];
        String[] lastHash = new String[1];
        boolean[] first = {true};
        CompletableFuture<?>[] pendingReload = new CompletableFuture<?>[1];

        Runnable recompute = () -> {
            String hash;
            synchronized (lock) {
                if (descriptor[0] == null || repoInfo[0] == null) {
                    return;
                }
                GitRemoteBranch remoteBranch = GitRemoteBranchesUtil.findRemoteBranch(repoInfo[0], descriptor[0]);
                Hash branchHash = remoteBranch == null ? null : repoInfo[0].getRemoteBranchesWithHashes().get(remoteBranch);
                hash = branchHash == null ? null : branchHash.asString();
                if (hash == null || Objects.equals(hash, lastHash[0])) {
                    return;
                }
                lastHash[0] = hash;
                if (first[0]) {
                    first[0] = false;
                    return;
                }
                if (pendingReload[0] != null) {
                    pendingReload[0].cancel(false);
                }
                // some delay to let the server consume changes
                pendingReload[0] = CompletableFuture.runAsync(detailsData::signalDetailsNeedReload,
                    CompletableFuture.delayedExecutor(2000, TimeUnit.MILLISECONDS, AppExecutorUtil.getAppExecutorService()));
            }
        };

        Runnable unsubscribe = detailsData.getLoadedDetailsState().addListener(details -> {
            if (details == null) {
                return;
            }
            HostedGitRepositoryRemote remote = GHPRBranchesViewModel.getHeadRemoteDescriptor(details, gitRemoteUrlCoordinates);
            if (remote == null) {
                return;
            }
            HostedGitRepositoryRemoteBranch branch = new HostedGitRepositoryRemoteBranch(remote, details.getHeadRefName());
            synchronized (lock) {
                if (branch.equals(descriptor[0])) {
                    return;
                }
                descriptor[0] = branch;
            }
            recompute.run();
        });
        Disposer.register(cs, unsubscribe::run);

        GitAsyncExtensions.infoFlow(gitRemoteUrlCoordinates.repository(), cs, info -> {
            synchronized (lock) {
                repoInfo[0] = info;
            }
            recompute.run();
        });

        Disposer.register(cs, () -> {
            synchronized (lock) {
                if (pendingReload[0] != null) {
                    pendingReload[0].cancel(false);
                }
            }
        });
    }

    private static GHPRBranchesRefs getRefs(GHPullRequest details) {
        return new GHPRBranchesRefs(details.getBaseRefOid(), details.getHeadRefOid());
    }
}
