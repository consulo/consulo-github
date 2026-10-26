// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.pullrequest.data.provider;

import consulo.disposer.Disposable;
import consulo.review.async.ComputationState;
import consulo.versionControlSystem.change.patch.FilePatch;
import git4idea.changes.GitBranchComparisonResult;
import org.jetbrains.plugins.github.api.data.GHCommit;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GHPRChangesDataProvider {
    void addChangesNeedReloadListener(Disposable parentDisposable, Runnable listener);

    CompletableFuture<List<GHCommit>> loadCommits();

    CompletableFuture<GitBranchComparisonResult> loadChanges();

    CompletableFuture<?> ensureAllRevisionsFetched();

    CompletableFuture<@Nullable FilePatch> loadPatchFromMergeBase(String commitSha, String filePath);

    void signalChangesNeedReload();

    default ComputationState<GitBranchComparisonResult> changesComputationState(Disposable parentDisposable) {
        return ComputationState.create(parentDisposable, this::addChangesNeedReloadListener, this::loadChanges);
    }
}
