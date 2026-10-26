// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.data.GHNode;

@GraphQLFragment("/graphql/fragment/pullRequestPendingReview.graphql")
public class GHPullRequestPendingReviewDTO extends GHNode {
    private final GHPullRequestReviewState state;
    private final CommentCount comments;

    protected GHPullRequestPendingReviewDTO() {
        super("");
        this.state = null;
        this.comments = null;
    }

    public GHPullRequestPendingReviewDTO(String id, GHPullRequestReviewState state, CommentCount comments) {
        super(id);
        this.state = state;
        this.comments = comments;
    }

    public GHPullRequestReviewState getState() {
        return state;
    }

    public CommentCount getComments() {
        return comments;
    }

    public static class CommentCount {
        private final int totalCount;

        protected CommentCount() {
            this(0);
        }

        public CommentCount(int totalCount) {
            this.totalCount = totalCount;
        }

        public int getTotalCount() {
            return totalCount;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CommentCount that)) {
                return false;
            }
            return totalCount == that.totalCount;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(totalCount);
        }
    }
}
