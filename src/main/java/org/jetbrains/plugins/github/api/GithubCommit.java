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

import java.util.Date;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubCommit extends GithubCommitSha {
    @Nullable
    private final GithubUser myAuthor;
    @Nullable
    private final GithubUser myCommitter;

    @Nonnull
    private final List<GithubCommitSha> myParents;

    @Nonnull
    private final GitCommit myCommit;

    public static class GitCommit {
        @Nonnull
        private final String myMessage;

        @Nonnull
        private final GitUser myAuthor;
        @Nonnull
        private final GitUser myCommitter;

        public GitCommit(@Nonnull String message, @Nonnull GitUser author, @Nonnull GitUser committer) {
            myMessage = message;
            myAuthor = author;
            myCommitter = committer;
        }

        @Nonnull
        public String getMessage() {
            return myMessage;
        }

        @Nonnull
        public GitUser getAuthor() {
            return myAuthor;
        }

        @Nonnull
        public GitUser getCommitter() {
            return myCommitter;
        }
    }

    public static class GitUser {
        @Nonnull
        private final String myName;
        @Nonnull
        private final String myEmail;
        @Nonnull
        private final Date myDate;

        public GitUser(@Nonnull String name, @Nonnull String email, @Nonnull Date date) {
            myName = name;
            myEmail = email;
            myDate = date;
        }

        @Nonnull
        public String getName() {
            return myName;
        }

        @Nonnull
        public String getEmail() {
            return myEmail;
        }

        @Nonnull
        public Date getDate() {
            return myDate;
        }
    }

    public GithubCommit(
        @Nonnull String url,
        @Nonnull String sha,
        @Nullable GithubUser author,
        @Nullable GithubUser committer,
        @Nonnull List<GithubCommitSha> parents,
        @Nonnull GitCommit commit
    ) {
        super(url, sha);
        myAuthor = author;
        myCommitter = committer;
        myParents = parents;
        myCommit = commit;
    }

    @Nullable
    public GithubUser getAuthor() {
        return myAuthor;
    }

    @Nullable
    public GithubUser getCommitter() {
        return myCommitter;
    }

    @Nonnull
    public List<GithubCommitSha> getParents() {
        return myParents;
    }

    @Nonnull
    public GitCommit getCommit() {
        return myCommit;
    }
}
