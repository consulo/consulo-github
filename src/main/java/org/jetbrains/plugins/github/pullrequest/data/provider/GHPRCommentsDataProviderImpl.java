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

import consulo.proxy.EventDispatcher;
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentService;

import java.util.concurrent.CompletableFuture;

public class GHPRCommentsDataProviderImpl implements GHPRCommentsDataProvider {
    private final GHPRCommentService myCommentService;
    private final GHPRIdentifier myPullRequestId;
    private final EventDispatcher<GHPRDataOperationsListener> myMessageBus;

    public GHPRCommentsDataProviderImpl(GHPRCommentService commentService,
                                        GHPRIdentifier pullRequestId,
                                        EventDispatcher<GHPRDataOperationsListener> messageBus) {
        myCommentService = commentService;
        myPullRequestId = pullRequestId;
        myMessageBus = messageBus;
    }

    //TODO: switch to GQL
    @Override
    public CompletableFuture<GithubIssueCommentWithHtml> addComment(String body) {
        return myCommentService.addComment(myPullRequestId, body).whenComplete((comment, error) -> {
            if (error == null) {
                myMessageBus.getMulticaster().onCommentAdded();
            }
        });
    }

    @Override
    public CompletableFuture<String> updateComment(String commentId, String text) {
        return myCommentService.updateComment(commentId, text).thenApply(comment -> {
            myMessageBus.getMulticaster().onCommentUpdated(commentId, comment.getBody());
            return comment.getBody();
        });
    }

    @Override
    public CompletableFuture<?> deleteComment(String commentId) {
        return myCommentService.deleteComment(commentId).whenComplete((result, error) -> {
            if (error == null) {
                myMessageBus.getMulticaster().onCommentDeleted(commentId);
            }
        });
    }
}
