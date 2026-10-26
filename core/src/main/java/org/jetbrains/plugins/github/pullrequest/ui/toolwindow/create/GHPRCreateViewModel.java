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

import consulo.project.Project;
import consulo.review.create.CodeReviewTitleDescriptionViewModel;
import consulo.review.icon.IconsProvider;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.ObservableValue;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.ui.component.LabeledListPanelViewModel;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public interface GHPRCreateViewModel extends CodeReviewTitleDescriptionViewModel {
    Project getProject();

    IconsProvider<String> getAvatarIconsProvider();

    String getRepositoryName();

    Collection<GHGitRepositoryMapping> getRepositories();

    ObservableValue<BranchesState> getBranches();

    ObservableValue<@Nullable ComputedResult<@Nullable GHPRCreateChangesViewModel>> getChangesVm();

    GHPRCreateDiffViewModel getDiffVm();

    ObservableValue<ComputedResult<@Nullable String>> getTemplateLoadingState();

    LabeledListPanelViewModel<GHUser> getAssigneesVm();

    LabeledListPanelViewModel<GHPullRequestRequestedReviewer> getReviewersVm();

    LabeledListPanelViewModel<GHLabel> getLabelsVm();

    ObservableValue<@Nullable ComputedResult<BranchesCheckResult>> getBranchesCheckState();

    ObservableValue<@Nullable CreationState> getCreationProgress();

    @Nullable
    Function<String, CompletableFuture<String>> getRemoteBranchNameCallback();

    void setRemoteBranchNameCallback(@Nullable Function<String, CompletableFuture<String>> callback);

    void setHead(@Nullable GHGitRepositoryMapping repo, @Nullable GitBranch branch);

    void setBaseBranch(@Nullable GitRemoteBranch branch);

    void create(boolean isDraft);

    void refreshBranchesState();

    record BranchesState(
        GHGitRepositoryMapping baseRepo,
        @Nullable GitRemoteBranch baseBranch,
        @Nullable GHGitRepositoryMapping headRepo,
        @Nullable GitBranch headBranch
    ) {
        public BranchesState withHead(@Nullable GHGitRepositoryMapping headRepo, @Nullable GitBranch headBranch) {
            return new BranchesState(baseRepo, baseBranch, headRepo, headBranch);
        }

        public BranchesState withBaseBranch(@Nullable GitRemoteBranch baseBranch) {
            return new BranchesState(baseRepo, baseBranch, headRepo, headBranch);
        }
    }

    sealed interface BranchesCheckResult {
        record NoChanges(GitRemoteBranch baseBranch, GitBranch headBranch) implements BranchesCheckResult {
        }

        record AlreadyExists(Runnable open) implements BranchesCheckResult {
        }

        record OK() implements BranchesCheckResult {
            public static final OK INSTANCE = new OK();
        }
    }

    sealed interface CreationState {
        record CollectingData() implements CreationState {
            public static final CollectingData INSTANCE = new CollectingData();
        }

        record Pushing() implements CreationState {
            public static final Pushing INSTANCE = new Pushing();
        }

        record CallingAPI() implements CreationState {
            public static final CallingAPI INSTANCE = new CallingAPI();
        }

        record SettingMetadata() implements CreationState {
            public static final SettingMetadata INSTANCE = new SettingMetadata();
        }

        record Created() implements CreationState {
            public static final Created INSTANCE = new Created();
        }

        record Error(Throwable error) implements CreationState {
        }
    }
}
