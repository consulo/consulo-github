// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.http.graphql.dto.GraphQLFragment;
import consulo.http.graphql.dto.GraphQLNodesDTO;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHReactable;
import org.jetbrains.plugins.github.api.data.GHRepository;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

@GraphQLFragment("/graphql/fragment/pullRequestInfo.graphql")
public class GHPullRequest extends GHPullRequestShort {
    private final @Nullable GHPullRequestReviewDecision reviewDecision;
    private final boolean viewerCanDeleteHeadRef;
    private final String body;
    private final String baseRefName;
    private final String baseRefOid;
    private final @Nullable GHRepository baseRepository;
    private final @Nullable HeadRef headRef;
    private final String headRefName;
    private final String headRefOid;
    private final @Nullable GHRepository headRepository;

    protected GHPullRequest() {
        super("", "", 0, "", null, false, null, null, null, null, null, null, null, null, null, false, false, false, null);
        this.reviewDecision = null;
        this.viewerCanDeleteHeadRef = false;
        this.body = "";
        this.baseRefName = "";
        this.baseRefOid = "";
        this.baseRepository = null;
        this.headRef = null;
        this.headRefName = "";
        this.headRefOid = "";
        this.headRepository = null;
    }

    public GHPullRequest(String id,
                         String url,
                         long number,
                         String title,
                         GHPullRequestState state,
                         boolean isDraft,
                         @Nullable GHActor author,
                         Date createdAt,
                         Date updatedAt,
                         GraphQLNodesDTO<GHUser> assignees,
                         @Nullable GraphQLNodesDTO<GHLabel> labels,
                         @Nullable GraphQLNodesDTO<GHPullRequestReviewRequest> reviewRequests,
                         GraphQLNodesDTO<ReviewThreadDetails> reviewThreads,
                         @Nullable GraphQLNodesDTO<GHPullRequestReview> reviews,
                         @Nullable GHPullRequestReviewDecision reviewDecision,
                         GHPullRequestMergeableState mergeable,
                         boolean viewerCanUpdate,
                         boolean viewerCanReact,
                         boolean viewerDidAuthor,
                         boolean viewerCanDeleteHeadRef,
                         String body,
                         String baseRefName,
                         String baseRefOid,
                         @Nullable GHRepository baseRepository,
                         @Nullable HeadRef headRef,
                         String headRefName,
                         String headRefOid,
                         @Nullable GHRepository headRepository,
                         GHReactable.ReactionConnection reactions) {
        super(id,
              url,
              number,
              title,
              state,
              isDraft,
              author,
              createdAt,
              updatedAt,
              assignees,
              labels,
              reviewRequests,
              reviewThreads,
              reviews,
              mergeable,
              viewerCanUpdate,
              viewerCanReact,
              viewerDidAuthor,
              reactions);
        this.reviewDecision = reviewDecision;
        this.viewerCanDeleteHeadRef = viewerCanDeleteHeadRef;
        this.body = body;
        this.baseRefName = baseRefName;
        this.baseRefOid = baseRefOid;
        this.baseRepository = baseRepository;
        this.headRef = headRef;
        this.headRefName = headRefName;
        this.headRefOid = headRefOid;
        this.headRepository = headRepository;
    }

    public @Nullable GHPullRequestReviewDecision getReviewDecision() {
        return reviewDecision;
    }

    public boolean getViewerCanDeleteHeadRef() {
        return viewerCanDeleteHeadRef;
    }

    public String getBody() {
        return body;
    }

    public String getBaseRefName() {
        return baseRefName;
    }

    public String getBaseRefOid() {
        return baseRefOid;
    }

    public @Nullable GHRepository getBaseRepository() {
        return baseRepository;
    }

    public String getHeadRefName() {
        return headRefName;
    }

    public String getHeadRefOid() {
        return headRefOid;
    }

    public @Nullable GHRepository getHeadRepository() {
        return headRepository;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPullRequest that)) {
            return false;
        }
        if (!super.equals(other)) {
            return false;
        }
        return Objects.equals(reviewDecision, that.reviewDecision)
            && viewerCanDeleteHeadRef == that.viewerCanDeleteHeadRef
            && Objects.equals(body, that.body)
            && Objects.equals(baseRefName, that.baseRefName)
            && Objects.equals(baseRefOid, that.baseRefOid)
            && Objects.equals(baseRepository, that.baseRepository)
            && Objects.equals(headRef, that.headRef)
            && Objects.equals(headRefName, that.headRefName)
            && Objects.equals(headRefOid, that.headRefOid)
            && Objects.equals(headRepository, that.headRepository);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), reviewDecision, viewerCanDeleteHeadRef, body, baseRefName, baseRefOid, baseRepository, headRef, headRefName, headRefOid, headRepository);
    }

    public @Nullable String getHeadRefId() {
        return headRef == null ? null : headRef.getId();
    }

    public static class HeadRef {
        private final @Nullable String id;

        protected HeadRef() {
            this(null);
        }

        public HeadRef(@Nullable String id) {
            this.id = id;
        }

        public @Nullable String getId() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof HeadRef that)) {
                return false;
            }
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }
}
