/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Aleksey Pivovarov
 */
public class GithubUserDetailed extends GithubUser {
    @Nullable
    private final String myName;
    @Nullable
    private final String myEmail;

    private final int myOwnedPrivateRepos;

    @Nonnull
    private final String myType;
    @Nonnull
    private final UserPlan myPlan;

    public static class UserPlan {
        @Nonnull
        private final String myName;
        private final long myPrivateRepos;

        public UserPlan(@Nonnull String name, long privateRepos) {
            myName = name;
            myPrivateRepos = privateRepos;
        }

        @Nonnull
        public String getName() {
            return myName;
        }

        public long getPrivateRepos() {
            return myPrivateRepos;
        }
    }

    public boolean canCreatePrivateRepo() {
        return getPlan().getPrivateRepos() > getOwnedPrivateRepos();
    }

    public GithubUserDetailed(
        @Nonnull String login,
        @Nonnull String htmlUrl,
        @Nullable String gravatarId,
        @Nullable String name,
        @Nullable String email,
        int ownedPrivateRepos,
        @Nonnull String type,
        @Nonnull UserPlan plan
    ) {
        super(login, htmlUrl, gravatarId);
        myName = name;
        myEmail = email;
        myOwnedPrivateRepos = ownedPrivateRepos;
        myType = type;
        myPlan = plan;
    }

    @Nullable
    public String getName() {
        return myName;
    }

    @Nullable
    public String getEmail() {
        return myEmail;
    }

    @Nonnull
    public String getType() {
        return myType;
    }

    public int getOwnedPrivateRepos() {
        return myOwnedPrivateRepos;
    }

    @Nonnull
    public UserPlan getPlan() {
        return myPlan;
    }
}
