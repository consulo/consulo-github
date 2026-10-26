// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import consulo.http.graphql.dto.GraphQLConnectionDTO;
import consulo.http.graphql.dto.GraphQLCursorPageInfoDTO;
import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.data.GHRefUpdateRule;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

@GraphQLFragment("/graphql/fragment/pullRequestMergeability.graphql")
public class GHPullRequestMergeabilityData {
    private final GHPullRequestMergeableState mergeable;
    private final boolean canBeRebased;
    private final GHPullRequestMergeStateStatus mergeStateStatus;
    private final @Nullable BaseRef baseRef;
    private final GHPullRequestCommitWithCheckStatusesConnection commits;

    protected GHPullRequestMergeabilityData() {
        this.mergeable = null;
        this.canBeRebased = false;
        this.mergeStateStatus = null;
        this.baseRef = null;
        this.commits = null;
    }

    public GHPullRequestMergeabilityData(GHPullRequestMergeableState mergeable,
                                         boolean canBeRebased,
                                         GHPullRequestMergeStateStatus mergeStateStatus,
                                         @Nullable BaseRef baseRef,
                                         GHPullRequestCommitWithCheckStatusesConnection commits) {
        this.mergeable = mergeable;
        this.canBeRebased = canBeRebased;
        this.mergeStateStatus = mergeStateStatus;
        this.baseRef = baseRef;
        this.commits = commits;
    }

    public GHPullRequestMergeableState getMergeable() {
        return mergeable;
    }

    public boolean getCanBeRebased() {
        return canBeRebased;
    }

    public GHPullRequestMergeStateStatus getMergeStateStatus() {
        return mergeStateStatus;
    }

    public GHPullRequestCommitWithCheckStatusesConnection getCommits() {
        return commits;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPullRequestMergeabilityData that)) {
            return false;
        }
        return Objects.equals(mergeable, that.mergeable)
            && canBeRebased == that.canBeRebased
            && Objects.equals(mergeStateStatus, that.mergeStateStatus)
            && Objects.equals(baseRef, that.baseRef)
            && Objects.equals(commits, that.commits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mergeable, canBeRebased, mergeStateStatus, baseRef, commits);
    }

    public @Nullable GHRefUpdateRule getBaseRefUpdateRule() {
        return baseRef == null ? null : baseRef.getRefUpdateRule();
    }

    public static class GHPullRequestCommitWithCheckStatusesConnection extends GraphQLConnectionDTO<GHPullRequestCommitWithCheckStatuses> {
        public GHPullRequestCommitWithCheckStatusesConnection(GraphQLCursorPageInfoDTO pageInfo) {
            this(pageInfo, List.of());
        }

        @JsonCreator
        public GHPullRequestCommitWithCheckStatusesConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo,
                                                              @JsonProperty("nodes") List<GHPullRequestCommitWithCheckStatuses> nodes) {
            super(pageInfo, nodes);
        }
    }

    public static class BaseRef {
        private final @Nullable GHRefUpdateRule refUpdateRule;

        protected BaseRef() {
            this(null);
        }

        public BaseRef(@Nullable GHRefUpdateRule refUpdateRule) {
            this.refUpdateRule = refUpdateRule;
        }

        public @Nullable GHRefUpdateRule getRefUpdateRule() {
            return refUpdateRule;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BaseRef that)) {
                return false;
            }
            return Objects.equals(refUpdateRule, that.refUpdateRule);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(refUpdateRule);
        }
    }
}
