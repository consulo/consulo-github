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

import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.data.GHReaction;
import org.jetbrains.plugins.github.api.data.GHReactionContent;

import java.util.concurrent.CompletableFuture;

public class GHReactionsServiceImpl implements GHReactionsService {
    private final GithubApiRequestExecutor myRequestExecutor;
    private final GHRepositoryCoordinates myRepository;

    public GHReactionsServiceImpl(GithubApiRequestExecutor requestExecutor, GHRepositoryCoordinates repository) {
        myRequestExecutor = requestExecutor;
        myRepository = repository;
    }

    @Override
    public CompletableFuture<GHReaction> addReaction(String subjectId, GHReactionContent reaction) {
        GithubApiRequest<GHReaction> request = GHGQLRequests.Comment.addReaction(myRepository.getServerPath(), subjectId, reaction);
        return myRequestExecutor.execute(request);
    }

    @Override
    public CompletableFuture<GHReaction> removeReaction(String subjectId, GHReactionContent reaction) {
        GithubApiRequest<GHReaction> request = GHGQLRequests.Comment.removeReaction(myRepository.getServerPath(), subjectId, reaction);
        return myRequestExecutor.execute(request);
    }
}
