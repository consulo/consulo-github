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
package org.jetbrains.plugins.github.pullrequest.data.service;

import consulo.http.page.ApiPageUtil;
import consulo.logging.Logger;
import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestChangedFile;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static consulo.review.util.ResultUtil.processErrorAndGet;
import static consulo.review.util.ResultUtil.runCatchingUser;

public class GHPRFilesServiceImpl implements GHPRFilesService {
    private static final Logger LOG = Logger.getInstance(GHPRFilesServiceImpl.class);

    private final GithubApiRequestExecutor myRequestExecutor;
    private final GHRepositoryCoordinates myRepository;

    public GHPRFilesServiceImpl(GithubApiRequestExecutor requestExecutor, GHRepositoryCoordinates repository) {
        myRequestExecutor = requestExecutor;
        myRepository = repository;
    }

    @Override
    public CompletableFuture<List<GHPullRequestChangedFile>> loadFiles(GHPRIdentifier pullRequestId) {
        return processErrorAndGet(runCatchingUser(() -> ApiPageUtil.foldGQLPagesToList(
                pagination -> myRequestExecutor.execute(GHGQLRequests.PullRequest.files(myRepository, pullRequestId.number(), pagination)))),
            e -> LOG.info("Error occurred while loading pull request files", e));
    }

    @Override
    public CompletableFuture<?> updateViewedState(GHPRIdentifier pullRequestId, String path, boolean isViewed) {
        return processErrorAndGet(runCatchingUser(() -> {
            GithubApiRequest<Void> request = isViewed
                ? GHGQLRequests.PullRequest.markFileAsViewed(myRepository.getServerPath(), pullRequestId.id(), path)
                : GHGQLRequests.PullRequest.unmarkFileAsViewed(myRepository.getServerPath(), pullRequestId.id(), path);
            return myRequestExecutor.execute(request);
        }), e -> LOG.info("Error occurred while updating file viewed state", e));
    }
}
