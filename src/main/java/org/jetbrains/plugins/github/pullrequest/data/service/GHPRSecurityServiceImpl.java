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

import consulo.accountManagement.Account;
import org.jetbrains.plugins.github.api.data.GHRepository;
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.GithubUser;
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings;

public class GHPRSecurityServiceImpl implements GHPRSecurityService {
    private final GithubSharedProjectSettings mySharedProjectSettings;
    private final GHUser myGhostUser;
    private final Account myAccount;
    private final GHUser myCurrentUser;
    private final GHRepository myRepo;

    public GHPRSecurityServiceImpl(GithubSharedProjectSettings sharedProjectSettings,
                                   GHUser ghostUser,
                                   Account account,
                                   GHUser currentUser,
                                   GHRepository repo) {
        mySharedProjectSettings = sharedProjectSettings;
        myGhostUser = ghostUser;
        myAccount = account;
        myCurrentUser = currentUser;
        myRepo = repo;
    }

    @Override
    public GHUser getGhostUser() {
        return myGhostUser;
    }

    @Override
    public Account getAccount() {
        return myAccount;
    }

    @Override
    public GHUser getCurrentUser() {
        return myCurrentUser;
    }

    @Override
    public boolean isCurrentUser(GithubUser user) {
        return user.getNodeId().equals(myCurrentUser.getId());
    }

    @Override
    public boolean currentUserHasPermissionLevel(GHRepositoryPermissionLevel level) {
        GHRepositoryPermissionLevel permission = myRepo.getViewerPermission();
        return (permission == null ? -1 : permission.ordinal()) >= level.ordinal();
    }

    @Override
    public boolean isMergeAllowed() {
        return myRepo.getMergeCommitAllowed();
    }

    @Override
    public boolean isRebaseMergeAllowed() {
        return myRepo.getRebaseMergeAllowed();
    }

    @Override
    public boolean isSquashMergeAllowed() {
        return myRepo.getSquashMergeAllowed();
    }

    @Override
    public boolean isMergeForbiddenForProject() {
        return mySharedProjectSettings.isPullRequestMergeForbidden();
    }
}
