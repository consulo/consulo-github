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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Aleksey Pivovarov
 */
class GithubCommitRaw implements DataConstructor {
    @Nullable
    public String url;
    @Nullable
    public String sha;

    @Nullable
    public GithubUserRaw author;
    @Nullable
    public GithubUserRaw committer;

    @Nullable
    public GitCommitRaw commit;

    @Nullable
    public CommitStatsRaw stats;
    @Nullable
    public List<GithubFileRaw> files;

    @Nullable
    public List<GithubCommitRaw> parents;

    public static class GitCommitRaw {
        @Nullable
        public String url;
        @Nullable
        public String message;

        @Nullable
        public GitUserRaw author;
        @Nullable
        public GitUserRaw committer;

        @SuppressWarnings("ConstantConditions")
        @Nonnull
        public GithubCommit.GitCommit create() {
            return new GithubCommit.GitCommit(message, author.create(), committer.create());
        }
    }

    public static class GitUserRaw {
        @Nullable
        public String name;
        @Nullable
        public String email;
        @Nullable
        public Date date;

        @SuppressWarnings("ConstantConditions")
        @Nonnull
        public GithubCommit.GitUser create() {
            return new GithubCommit.GitUser(name, email, date);
        }
    }

    public static class CommitStatsRaw {
        @Nullable
        public Integer additions;
        @Nullable
        public Integer deletions;
        @Nullable
        public Integer total;

        @SuppressWarnings("ConstantConditions")
        @Nonnull
        public GithubCommitDetailed.CommitStats create() {
            return new GithubCommitDetailed.CommitStats(additions, deletions, total);
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull
    public GithubCommitSha createCommitSha() {
        return new GithubCommitSha(url, sha);
    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull
    public GithubCommit createCommit() {
        GithubUser author = this.author == null ? null : this.author.createUser();
        GithubUser committer = this.committer == null ? null : this.committer.createUser();

        List<GithubCommitSha> parents = new ArrayList<>();
        for (GithubCommitRaw raw : this.parents) {
            parents.add(raw.createCommitSha());
        }
        return new GithubCommit(url, sha, author, committer, parents, commit.create());
    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull
    public GithubCommitDetailed createCommitDetailed() {
        GithubCommit commit = createCommit();
        List<GithubFile> files = new ArrayList<>();
        for (GithubFileRaw raw : this.files) {
            files.add(raw.createFile());
        }

        return new GithubCommitDetailed(
            commit.getUrl(),
            commit.getSha(),
            commit.getAuthor(),
            commit.getCommitter(),
            commit.getParents(),
            commit.getCommit(),
            stats.create(),
            files
        );
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> T create(@Nonnull Class<T> resultClass) {
        if (resultClass.isAssignableFrom(GithubCommitSha.class)) {
            return (T)createCommitSha();
        }
        if (resultClass.isAssignableFrom(GithubCommit.class)) {
            return (T)createCommit();
        }
        if (resultClass.isAssignableFrom(GithubCommitDetailed.class)) {
            return (T)createCommitDetailed();
        }

        throw new ClassCastException(this.getClass().getName() + ": bad class type: " + resultClass.getName());
    }
}
