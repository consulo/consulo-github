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

import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.log.VcsCommitMetadata;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestBranchMatch;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRestIdOnly;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GHPRCreationService {
    CompletableFuture<GHPullRequestShort> createPullRequest(GitRemoteBranch baseBranch,
                                                            GHGitRepositoryMapping headRepo,
                                                            GitRemoteBranch headBranch,
                                                            String title,
                                                            String description,
                                                            boolean draft);

    CompletableFuture<@Nullable GHPRIdentifier> findOpenPullRequest(@Nullable GitRemoteBranch baseBranch,
                                                                    GHRepositoryPath headRepo,
                                                                    GitRemoteBranch headBranch);

    CompletableFuture<@Nullable GHPullRequestRestIdOnly> findOpenPullRequestDetails(@Nullable GitRemoteBranch baseBranch,
                                                                                    GHRepositoryPath headRepo,
                                                                                    GitRemoteBranch headBranch);

    /**
     * Finds an open pull request for each of {@code headBranches} that has one, in a single request.
     */
    CompletableFuture<List<GHPullRequestBranchMatch>> findOpenPullRequestsByHeadBranches(Collection<GitRemoteBranch> headBranches);

    CompletableFuture<Collection<RefComparisonChange>> getDiff(VcsCommitMetadata commit);

    CompletableFuture<Collection<RefComparisonChange>> getDiff(GitRemoteBranch baseBranch, GitBranch headBranch);
}
