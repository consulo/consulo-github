// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.pullrequest.data.service;

import consulo.disposer.Disposable;
import git4idea.GitRemoteBranch;
import git4idea.remote.GitRemoteUrlCoordinates;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.GithubUserWithPermissions;
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface GHPRRepositoryDataService {
    GitRemoteUrlCoordinates getRemoteCoordinates();

    GHRepositoryCoordinates getRepositoryCoordinates();

    default GHGitRepositoryMapping getRepositoryMapping() {
        return new GHGitRepositoryMapping(getRepositoryCoordinates(), getRemoteCoordinates());
    }

    String getRepositoryId();

    @Nullable
    String getDefaultBranchName();

    boolean isFork();

    void addDataReloadListener(Disposable parentDisposable, Runnable listener);

    CompletableFuture<?> loadBatchedCollaborators(Consumer<List<GithubUserWithPermissions>> consumer);

    CompletableFuture<?> loadBatchedContributors(Consumer<List<GHUser>> consumer);

    CompletableFuture<?> loadBatchedPotentialIssuesAssignees(Consumer<List<GHUser>> consumer);

    CompletableFuture<?> loadBatchedLabels(Consumer<List<GHLabel>> consumer);

    CompletableFuture<?> loadBatchedTeams(Consumer<List<GHTeam>> consumer);

    /**
     * Find a pull request description template
     */
    CompletableFuture<@Nullable String> loadTemplate();

    void resetData();

    @Nullable
    GitRemoteBranch getDefaultRemoteBranch();

    CompletableFuture<?> mentionableUsersBatchesFlow(Consumer<List<GHUser>> consumer);
}
