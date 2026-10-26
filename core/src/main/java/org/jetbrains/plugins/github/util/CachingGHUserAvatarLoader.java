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
package org.jetbrains.plugins.github.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.util.LowMemoryWatcher;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.GithubApiContentHelper;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class CachingGHUserAvatarLoader implements Disposable {
    private static final Logger LOG = Logger.getInstance(CachingGHUserAvatarLoader.class);

    private static final int MAXIMUM_ICON_SIZE = 40;

    // store images at maximum used size with maximum reasonable scale to avoid upscaling (3 for system scale, 2 for user scale)
    private static final int STORED_IMAGE_SIZE = MAXIMUM_ICON_SIZE * 6;

    private static final int MAX_ATTEMPTS = 5;

    private final Executor myLimitedIOExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("GitHub Avatars Loader", 2);

    private final Cache<String, CompletableFuture<@Nullable Image>> myAvatarCache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(5))
        .build();

    public CachingGHUserAvatarLoader() {
        LowMemoryWatcher.register(myAvatarCache::invalidateAll, this);
    }

    public static CachingGHUserAvatarLoader getInstance() {
        return Application.get().getInstance(CachingGHUserAvatarLoader.class);
    }

    public CompletableFuture<@Nullable Image> loadAvatar(GithubApiRequestExecutor requestExecutor, String url) {
        try {
            return myAvatarCache.get(url, () -> loadAndDownscale(requestExecutor, url, STORED_IMAGE_SIZE));
        }
        catch (ExecutionException e) {
            return CompletableFuture.failedFuture(e.getCause());
        }
    }

    private CompletableFuture<@Nullable Image> loadAndDownscale(GithubApiRequestExecutor requestExecutor, String url, int maximumSize) {
        // loading all avatars in parallel may cause issues with server DDOS protection (it will drop connections)
        // so we load only 2 in parallel and do this with retry
        return doWithBackoffRetry(() -> CompletableFuture.supplyAsync(() -> {
                try {
                    return requestExecutor.execute(new EmptyProgressIndicator(), GithubApiRequests.CurrentUser.getAvatar(url));
                }
                catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, myLimitedIOExecutor), 0)
            .thenApply(bytes -> {
                try {
                    Image loadedImage = GithubApiContentHelper.loadImage(bytes);
                    return loadedImage.getWidth() <= maximumSize && loadedImage.getHeight() <= maximumSize
                        ? loadedImage
                        : ImageEffects.resize(loadedImage, maximumSize, maximumSize);
                }
                catch (Exception e) {
                    throw new CompletionException(e);
                }
            })
            .<@Nullable Image>thenApply(it -> it)
            .exceptionally(e -> {
                if (LOG.isDebugEnabled()) {
                    LOG.warn("Error loading image from " + url, e);
                }
                else {
                    LOG.warn("Error loading image from " + url + " - " + e.getMessage());
                }
                return null;
            });
    }

    private static <T> CompletableFuture<T> doWithBackoffRetry(Supplier<CompletableFuture<T>> loader, int attempt) {
        return loader.get().handle((value, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(value);
            }
            if (attempt + 1 >= MAX_ATTEMPTS) {
                return CompletableFuture.<T>failedFuture(error);
            }
            Executor delayed = CompletableFuture.delayedExecutor(attempt * 50L, TimeUnit.MILLISECONDS);
            return CompletableFuture.supplyAsync(() -> null, delayed).thenCompose(ignored -> doWithBackoffRetry(loader, attempt + 1));
        }).thenCompose(it -> it);
    }

    @Override
    public void dispose() {
        myAvatarCache.invalidateAll();
    }
}
