// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import java.util.Objects;

public class GHCommitsCompareResult {
    private final CommitSha mergeBaseCommit;

    protected GHCommitsCompareResult() {
        this.mergeBaseCommit = null;
    }

    public GHCommitsCompareResult(CommitSha mergeBaseCommit) {
        this.mergeBaseCommit = mergeBaseCommit;
    }

    public CommitSha getMergeBaseCommit() {
        return mergeBaseCommit;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHCommitsCompareResult that)) {
            return false;
        }
        return Objects.equals(mergeBaseCommit, that.mergeBaseCommit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mergeBaseCommit);
    }

    public static class CommitSha {
        private final String sha;

        protected CommitSha() {
            this("");
        }

        public CommitSha(String sha) {
            this.sha = sha;
        }

        public String getSha() {
            return sha;
        }
    }
}
