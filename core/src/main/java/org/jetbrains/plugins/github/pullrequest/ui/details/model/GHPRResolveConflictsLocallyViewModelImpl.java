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
package org.jetbrains.plugins.github.pullrequest.ui.details.model;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.util.ComputedResult;
import consulo.review.util.Either;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.distributed.repository.Repository;
import git4idea.GitRemoteBranch;
import git4idea.remote.GitRemoteUrlCoordinates;
import git4idea.remote.hosting.GitAsyncExtensions;
import git4idea.remote.hosting.GitRemoteBranchesUtil;
import git4idea.remote.hosting.HostedGitRepositoryRemote;
import git4idea.remote.hosting.ui.BaseOrHead;
import git4idea.remote.hosting.ui.ResolveConflictsLocallyCoordinates;
import git4idea.remote.hosting.ui.ResolveConflictsLocallyViewModel;
import git4idea.repo.GitRepoInfo;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

public final class GHPRResolveConflictsLocallyViewModelImpl {
    private static final Set<Repository.State> REPO_MERGING_STATES = Set.of(Repository.State.REBASING, Repository.State.MERGING);

    private GHPRResolveConflictsLocallyViewModelImpl() {
    }

    public static ResolveConflictsLocallyViewModel<GHPRResolveConflictsLocallyError> create(Disposable parentCs,
                                                                                          Project project,
                                                                                          GitRemoteUrlCoordinates remoteUrlCoordinates,
                                                                                          GHPRDetailsDataProvider detailsData) {
        ComputationState<GHPRMergeabilityState> mergeability = detailsData.mergeabilityStateComputationFlow(parentCs);
        MutableObservableValue<@Nullable Boolean> hasConflicts = MutableObservableValue.of(null);
        Runnable updateConflicts = () -> {
            ComputedResult<GHPRMergeabilityState> result = mergeability.get();
            if (result == null || result.isInProgress()) {
                return;
            }
            GHPRMergeabilityState state = result.getOrNull();
            hasConflicts.set(state == null ? null : state.getHasConflicts());
        };
        Disposer.register(parentCs, mergeability.addListener(it -> updateConflicts.run())::run);
        updateConflicts.run();

        ComputationState<GHPullRequest> detailsComputation = detailsData.detailsComputationFlow(parentCs);
        MutableObservableValue<String> baseRef = MutableObservableValue.of("");
        ObservableValue<@Nullable Boolean> isBaseInHistory =
            GitAsyncExtensions.isInCurrentHistory(remoteUrlCoordinates.repository(), baseRef, parentCs);
        ObservableValue<GitRepoInfo> repoInfo = GitAsyncExtensions.infoStateIn(remoteUrlCoordinates.repository(), parentCs);

        MutableObservableValue<Either<GHPRResolveConflictsLocallyError, ResolveConflictsLocallyCoordinates>> requestOrError =
            MutableObservableValue.of(Either.left(GHPRResolveConflictsLocallyError.DetailsNotLoaded.INSTANCE));
        Runnable updateRequest = () -> {
            ComputedResult<GHPullRequest> detailsResult = detailsComputation.get();
            GHPullRequest loaded = detailsResult == null ? null : detailsResult.getOrNull();
            if (loaded != null && !Objects.equals(baseRef.get(), loaded.getBaseRefOid())) {
                baseRef.set(loaded.getBaseRefOid());
            }
            requestOrError.set(calcRequestOrError(remoteUrlCoordinates, Boolean.TRUE.equals(isBaseInHistory.get()), detailsResult, repoInfo.get()));
        };
        Disposer.register(parentCs, detailsComputation.addListener(it -> updateRequest.run())::run);
        Disposer.register(parentCs, isBaseInHistory.addListener(it -> updateRequest.run())::run);
        Disposer.register(parentCs, repoInfo.addListener(it -> updateRequest.run())::run);
        updateRequest.run();

        return ResolveConflictsLocallyViewModel.createIn(parentCs, project, remoteUrlCoordinates.repository(), hasConflicts, requestOrError);
    }

    private static Either<GHPRResolveConflictsLocallyError, ResolveConflictsLocallyCoordinates> calcRequestOrError(
        GitRemoteUrlCoordinates remoteUrlCoordinates,
        boolean isBaseInHistory,
        @Nullable ComputedResult<GHPullRequest> detailsResult,
        GitRepoInfo repoState) {
        if (REPO_MERGING_STATES.contains(repoState.getState())) {
            return Either.left(GHPRResolveConflictsLocallyError.MergeInProgress.INSTANCE);
        }

        GHPullRequest details = detailsResult == null ? null : detailsResult.getOrNull();
        if (details == null) {
            return Either.left(GHPRResolveConflictsLocallyError.DetailsNotLoaded.INSTANCE);
        }

        HostedGitRepositoryRemote headRemoteDescriptor = GHPRBranchesViewModel.getHeadRemoteDescriptor(details, remoteUrlCoordinates);
        if (headRemoteDescriptor == null) {
            return Either.left(new GHPRResolveConflictsLocallyError.RepositoryNotFound(BaseOrHead.Head));
        }
        HostedGitRepositoryRemote baseRemoteDescriptor = GHPRBranchesViewModel.getBaseRemoteDescriptor(details, remoteUrlCoordinates);
        if (baseRemoteDescriptor == null) {
            return Either.left(new GHPRResolveConflictsLocallyError.RepositoryNotFound(BaseOrHead.Base));
        }

        GitRemoteBranch currentRemoteBranch = GitAsyncExtensions.findFirstRemoteBranchTrackedByCurrent(repoState);
        if (currentRemoteBranch != null && isBaseInHistory &&
            currentRemoteBranch.getNameForRemoteOperations().equals(details.getHeadRefName()) &&
            Objects.equals(currentRemoteBranch.getRemote(), GitRemoteBranchesUtil.findRemote(remoteUrlCoordinates.repository(), headRemoteDescriptor))) {
            return Either.left(GHPRResolveConflictsLocallyError.AlreadyResolvedLocally.INSTANCE);
        }

        return Either.right(new ResolveConflictsLocallyCoordinates(headRemoteDescriptor, details.getHeadRefName(), baseRemoteDescriptor,
            details.getBaseRefName()));
    }
}
