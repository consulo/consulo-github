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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubApiRequestOperation;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GHEnterpriseServerMeta;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class GHEnterpriseServerMetadataLoader {
    private final Map<GithubServerPath, CompletableFuture<GHEnterpriseServerMeta>> myCache = new ConcurrentHashMap<>();

    private volatile GithubApiRequestExecutor myApiRequestExecutor;

    public static GHEnterpriseServerMetadataLoader getInstance() {
        return Application.get().getInstance(GHEnterpriseServerMetadataLoader.class);
    }

    public CompletableFuture<GHEnterpriseServerMeta> loadMetadata(GithubServerPath server) {
        if (server.isGithubDotCom() || server.isGheDataResidency()) {
            throw new IllegalArgumentException("Cannot retrieve server metadata from github.com");
        }
        return myCache.computeIfAbsent(server, it -> {
            String metaUrl = it.toApiUrl() + "/meta";
            return getExecutor().execute(GithubApiRequest.Get.json(metaUrl, GHEnterpriseServerMeta.class)
                .withOperation(GithubApiRequestOperation.RestGetServerMetadata)
                .withOperationName("get server metadata"));
        });
    }

    private GithubApiRequestExecutor getExecutor() {
        GithubApiRequestExecutor executor = myApiRequestExecutor;
        if (executor == null) {
            executor = GithubApiRequestExecutorFactory.getInstance().create();
            myApiRequestExecutor = executor;
        }
        return executor;
    }
}
