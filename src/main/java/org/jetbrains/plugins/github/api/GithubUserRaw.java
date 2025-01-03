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

import java.util.Date;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Information about a user on GitHub.
 *
 * @author Kirill Likhodedov
 */
@SuppressWarnings("UnusedDeclaration")
class GithubUserRaw implements DataConstructor {
    @Nullable
    public String login;
    @Nullable
    public Long id;

    @Nullable
    public String url;
    @Nullable
    public String htmlUrl;

    @Nullable
    public String name;
    @Nullable
    public String email;
    @Nullable
    public String company;
    @Nullable
    public String location;
    @Nullable
    public String type;

    @Nullable
    public Integer publicRepos;
    @Nullable
    public Integer publicGists;
    @Nullable
    public Integer totalPrivateRepos;
    @Nullable
    public Integer ownedPrivateRepos;
    @Nullable
    public Integer privateGists;
    @Nullable
    public Long diskUsage;

    @Nullable
    public Integer followers;
    @Nullable
    public Integer following;
    @Nullable
    public String avatarUrl;
    @Nullable
    public String gravatarId;
    @Nullable
    public Integer collaborators;
    @Nullable
    public String blog;

    @Nullable
    public UserPlanRaw plan;

    @Nullable
    public Date createdAt;

    public static class UserPlanRaw {
        @Nullable
        public String name;
        @Nullable
        public Long space;
        @Nullable
        public Long collaborators;
        @Nullable
        public Long privateRepos;

        @SuppressWarnings("ConstantConditions")
        @Nonnull
        public GithubUserDetailed.UserPlan create() {
            return new GithubUserDetailed.UserPlan(name, privateRepos);
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull
    public GithubUser createUser() {
        return new GithubUser(login, htmlUrl, gravatarId);
    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull
    public GithubUserDetailed createUserDetailed() {
        return new GithubUserDetailed(login, htmlUrl, gravatarId, name, email, ownedPrivateRepos, type, plan.create());
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> T create(@Nonnull Class<T> resultClass) {
        if (resultClass.isAssignableFrom(GithubUser.class)) {
            return (T)createUser();
        }
        if (resultClass.isAssignableFrom(GithubUserDetailed.class)) {
            return (T)createUserDetailed();
        }

        throw new ClassCastException(this.getClass().getName() + ": bad class type: " + resultClass.getName());
    }
}
