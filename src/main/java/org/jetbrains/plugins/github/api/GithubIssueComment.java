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

import javax.annotation.Nonnull;

import java.util.Date;

/**
 * @author Aleksey Pivovarov
 */
public class GithubIssueComment {
    private final long myId;

    @Nonnull
    private final String myHtmlUrl;
    @Nonnull
    private final String myBodyHtml;

    @Nonnull
    private final Date myCreatedAt;
    @Nonnull
    private final Date myUpdatedAt;

    @Nonnull
    private final GithubUser myUser;

    public GithubIssueComment(
        long id,
        @Nonnull String htmlUrl,
        @Nonnull String bodyHtml,
        @Nonnull Date createdAt,
        @Nonnull Date updatedAt,
        @Nonnull GithubUser user
    ) {
        myId = id;
        myHtmlUrl = htmlUrl;
        myBodyHtml = bodyHtml;
        myCreatedAt = createdAt;
        myUpdatedAt = updatedAt;
        myUser = user;
    }

    public long getId() {
        return myId;
    }

    @Nonnull
    public String getHtmlUrl() {
        return myHtmlUrl;
    }

    @Nonnull
    public String getBodyHtml() {
        return myBodyHtml;
    }

    @Nonnull
    public Date getCreatedAt() {
        return myCreatedAt;
    }

    @Nonnull
    public Date getUpdatedAt() {
        return myUpdatedAt;
    }

    @Nonnull
    public GithubUser getUser() {
        return myUser;
    }
}
