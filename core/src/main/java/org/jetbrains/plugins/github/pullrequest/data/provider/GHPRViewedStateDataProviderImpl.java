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
package org.jetbrains.plugins.github.pullrequest.data.provider;

import consulo.disposer.Disposable;
import consulo.review.async.LoaderWithMutableCache;
import consulo.review.util.FutureUtil;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestChangedFile;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestFileViewedState;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRFilesService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GHPRViewedStateDataProviderImpl implements GHPRViewedStateDataProvider {
    private final GHPRFilesService myFilesService;
    private final GHPRIdentifier myPullRequestId;

    private final LoaderWithMutableCache<Map<String, GHPullRequestFileViewedState>> myLoader;

    public GHPRViewedStateDataProviderImpl(Disposable parentDisposable, GHPRFilesService filesService, GHPRIdentifier pullRequestId) {
        myFilesService = filesService;
        myPullRequestId = pullRequestId;
        myLoader = new LoaderWithMutableCache<>(parentDisposable, () -> myFilesService.loadFiles(myPullRequestId).thenApply(files -> {
            Map<String, GHPullRequestFileViewedState> result = new LinkedHashMap<>();
            for (GHPullRequestChangedFile file : files) {
                result.put(file.getPath(), file.getViewerViewedState());
            }
            return Map.copyOf(result);
        }));
    }

    @Override
    public void addViewedStateNeedsReloadListener(Disposable parentDisposable, Runnable listener) {
        myLoader.addUpdatedListener(parentDisposable, listener);
    }

    @Override
    public CompletableFuture<Map<String, GHPullRequestFileViewedState>> loadViewedState() {
        return myLoader.load();
    }

    @Override
    public CompletableFuture<?> updateViewedState(Iterable<String> paths, boolean isViewed) {
        myLoader.updateLoaded(it -> {
            Map<String, GHPullRequestFileViewedState> updated = new LinkedHashMap<>(it);
            GHPullRequestFileViewedState newState = isViewed ? GHPullRequestFileViewedState.VIEWED : GHPullRequestFileViewedState.UNVIEWED;
            paths.forEach(path -> updated.put(path, newState));
            return Map.copyOf(updated);
        });

        List<CompletableFuture<?>> requests = new ArrayList<>();
        for (String path : paths) {
            requests.add(myFilesService.updateViewedState(myPullRequestId, path, isViewed));
        }
        CompletableFuture<?> result = CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new));
        return result.handle((value, error) -> {
            if (error != null && !FutureUtil.isCancellation(error)) {
                signalViewedStateNeedsReload();
            }
            return null;
        });
    }

    @Override
    public void signalViewedStateNeedsReload() {
        myLoader.clearCache();
    }
}
