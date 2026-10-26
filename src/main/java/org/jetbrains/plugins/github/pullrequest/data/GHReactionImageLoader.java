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
package org.jetbrains.plugins.github.pullrequest.data;

import consulo.review.icon.AsyncImageLoader;
import consulo.ui.image.Image;
import consulo.util.lang.lazy.LazyValue;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GHReactionContent;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class GHReactionImageLoader implements AsyncImageLoader<GHReactionContent> {
    private final GithubApiRequestExecutor myRequestExecutor;

    private final LazyValue<CompletableFuture<Map<String, String>>> myEmojisNameToUrl;

    public GHReactionImageLoader(GithubServerPath serverPath, GithubApiRequestExecutor requestExecutor) {
        myRequestExecutor = requestExecutor;
        myEmojisNameToUrl = LazyValue.atomicNotNull(() -> myRequestExecutor.execute(GithubApiRequests.Emojis.loadNameToUrlMap(serverPath)));
    }

    @Override
    public CompletableFuture<@Nullable Image> load(GHReactionContent key) {
        return myEmojisNameToUrl.get().thenCompose(map -> {
            String url = map.get(getEmojiName(key));
            if (url == null) {
                return CompletableFuture.<@Nullable Image>completedFuture(null);
            }
            // seems to be a bug in github asset manager - loading emoji images with auth sometimes leads to 404
            GithubApiRequestExecutor exec = url.contains("githubassets") ? GithubApiRequestExecutorFactory.getInstance().create() : myRequestExecutor;
            return exec.execute(GithubApiRequests.Emojis.loadImage(url)).thenApply(bytes -> {
                try {
                    return Image.fromBytes(Image.ImageType.PNG, bytes);
                }
                catch (Exception e) {
                    throw new CompletionException(e);
                }
            });
        });
    }

    private static String getEmojiName(GHReactionContent content) {
        return switch (content) {
            case THUMBS_UP -> "+1";
            case THUMBS_DOWN -> "-1";
            case LAUGH -> "smile";
            case HOORAY -> "tada";
            case CONFUSED -> "confused";
            case HEART -> "heart";
            case ROCKET -> "rocket";
            case EYES -> "eyes";
        };
    }
}
