// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class GHPRRepositoryDataServiceExtensions {
    private GHPRRepositoryDataServiceExtensions() {
    }

    public static CompletableFuture<?> getBatchedPotentialReviewers(GHPRRepositoryDataService service,
                                                                    GHPRSecurityService securityService,
                                                                    Consumer<List<GHPullRequestRequestedReviewer>> consumer) {
        Consumer<List<? extends GHPullRequestRequestedReviewer>> send = batch -> {
            synchronized (consumer) {
                consumer.accept(new ArrayList<>(batch));
            }
        };
        CompletableFuture<?> teams = service.loadBatchedTeams(send::accept);
        CompletableFuture<?> users;
        if (securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)) {
            users = service.loadBatchedCollaborators(collaboratorsBatch -> send.accept(
                collaboratorsBatch.stream()
                    .filter(it -> it.getPermissions().isPush())
                    .map(user -> new GHUser(user.getNodeId(), user.getLogin(), user.getHtmlUrl(),
                        Objects.requireNonNullElse(user.getAvatarUrl(), ""), null))
                    .toList()
            ));
        }
        else {
            users = service.loadBatchedContributors(send::accept);
        }
        CompletableFuture<?> result = CompletableFuture.allOf(teams, users);
        result.whenComplete((r, e) -> {
            if (result.isCancelled()) {
                teams.cancel(false);
                users.cancel(false);
            }
        });
        return result;
    }
}
