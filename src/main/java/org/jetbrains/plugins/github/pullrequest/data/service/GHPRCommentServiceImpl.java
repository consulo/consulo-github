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
package org.jetbrains.plugins.github.pullrequest.data.service;

import consulo.logging.Logger;
import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.data.GHComment;
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;

import java.util.concurrent.CompletableFuture;

import static consulo.review.util.ResultUtil.processErrorAndGet;
import static consulo.review.util.ResultUtil.runCatchingUser;

public class GHPRCommentServiceImpl implements GHPRCommentService {
    private static final Logger LOG = Logger.getInstance(GHPRCommentService.class);

    private final GithubApiRequestExecutor myRequestExecutor;
    private final GHRepositoryCoordinates myRepository;

    public GHPRCommentServiceImpl(GithubApiRequestExecutor requestExecutor, GHRepositoryCoordinates repository) {
        myRequestExecutor = requestExecutor;
        myRepository = repository;
    }

    @Override
    public CompletableFuture<GithubIssueCommentWithHtml> addComment(GHPRIdentifier pullRequestId, String body) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(
                GithubApiRequests.Repos.Issues.Comments.create(myRepository, pullRequestId.number(), body))),
            e -> LOG.info("Error occurred while adding PR comment", e));
    }

    @Override
    public CompletableFuture<GHComment> updateComment(String commentId, String text) {
        return processErrorAndGet(runCatchingUser(() ->
                myRequestExecutor.execute(GHGQLRequests.Comment.updateComment(myRepository.getServerPath(), commentId, text))),
            e -> LOG.info("Error occurred while updating comment", e));
    }

    @Override
    public CompletableFuture<?> deleteComment(String commentId) {
        return processErrorAndGet(runCatchingUser(() ->
                myRequestExecutor.execute(GHGQLRequests.Comment.deleteComment(myRepository.getServerPath(), commentId))),
            e -> LOG.info("Error occurred while deleting comment", e));
    }
}
