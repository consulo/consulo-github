// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.http.graphql.dto.GraphQLFragment;
import consulo.http.graphql.dto.GraphQLNodesDTO;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHNode;
import org.jetbrains.plugins.github.api.data.GHReactable;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@GraphQLFragment("/graphql/fragment/pullRequestInfoShort.graphql")
public class GHPullRequestShort extends GHNode implements GHReactable {
    private final String url;
    private final long number;
    private final String title;
    private final GHPullRequestState state;
    private final boolean isDraft;
    private final @Nullable GHActor author;
    private final Date createdAt;
    private final Date updatedAt;
    private final GHPullRequestMergeableState mergeable;
    private final boolean viewerCanUpdate;
    private final boolean viewerCanReact;
    private final boolean viewerDidAuthor;
    private final GHReactable.ReactionConnection reactions;
    @JsonProperty("assignees")
    private final GraphQLNodesDTO<GHUser> assigneesNodes;
    @JsonProperty("labels")
    private final @Nullable GraphQLNodesDTO<GHLabel> labelsNodes;
    @JsonProperty("reviewRequests")
    private final @Nullable GraphQLNodesDTO<GHPullRequestReviewRequest> reviewRequestsNodes;
    @JsonProperty("reviewThreads")
    private final GraphQLNodesDTO<ReviewThreadDetails> reviewThreadsNodes;
    @JsonProperty("reviews")
    private final @Nullable GraphQLNodesDTO<GHPullRequestReview> reviewsNodes;

    protected GHPullRequestShort() {
        super("");
        this.url = "";
        this.number = 0;
        this.title = "";
        this.state = null;
        this.isDraft = false;
        this.author = null;
        this.createdAt = null;
        this.updatedAt = null;
        this.mergeable = null;
        this.viewerCanUpdate = false;
        this.viewerCanReact = false;
        this.viewerDidAuthor = false;
        this.reactions = null;
        this.assigneesNodes = new GraphQLNodesDTO<>();
        this.labelsNodes = null;
        this.reviewRequestsNodes = null;
        this.reviewThreadsNodes = new GraphQLNodesDTO<>();
        this.reviewsNodes = null;
    }

    public GHPullRequestShort(String id,
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
                              GHPullRequestMergeableState mergeable,
                              boolean viewerCanUpdate,
                              boolean viewerCanReact,
                              boolean viewerDidAuthor,
                              GHReactable.ReactionConnection reactions) {
        super(id);
        this.url = url;
        this.number = number;
        this.title = title;
        this.state = state;
        this.isDraft = isDraft;
        this.author = author;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.mergeable = mergeable;
        this.viewerCanUpdate = viewerCanUpdate;
        this.viewerCanReact = viewerCanReact;
        this.viewerDidAuthor = viewerDidAuthor;
        this.reactions = reactions;
        this.assigneesNodes = assignees;
        this.labelsNodes = labels;
        this.reviewRequestsNodes = reviewRequests;
        this.reviewThreadsNodes = reviewThreads;
        this.reviewsNodes = reviews;
    }

    public String getUrl() {
        return url;
    }

    public long getNumber() {
        return number;
    }

    public String getTitle() {
        return title;
    }

    public GHPullRequestState getState() {
        return state;
    }

    public boolean isDraft() {
        return isDraft;
    }

    public @Nullable GHActor getAuthor() {
        return author;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public GHPullRequestMergeableState getMergeable() {
        return mergeable;
    }

    public boolean getViewerCanUpdate() {
        return viewerCanUpdate;
    }

    public boolean getViewerCanReact() {
        return viewerCanReact;
    }

    public boolean getViewerDidAuthor() {
        return viewerDidAuthor;
    }

    @Override
    public GHReactable.ReactionConnection getReactions() {
        return reactions;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        if (!super.equals(other)) {
            return false;
        }

        GHPullRequestShort that = (GHPullRequestShort) other;
        return Objects.equals(url, that.url)
            && number == that.number
            && Objects.equals(title, that.title)
            && state == that.state
            && isDraft == that.isDraft
            && Objects.equals(author, that.author)
            && Objects.equals(createdAt, that.createdAt)
            && Objects.equals(updatedAt, that.updatedAt)
            && mergeable == that.mergeable
            && viewerCanUpdate == that.viewerCanUpdate
            && viewerDidAuthor == that.viewerDidAuthor
            && Objects.equals(getPrId(), that.getPrId())
            && Objects.equals(getAssignees(), that.getAssignees())
            && Objects.equals(getLabels(), that.getLabels())
            && Objects.equals(getReviewRequests(), that.getReviewRequests())
            && getUnresolvedReviewThreadsCount() == that.getUnresolvedReviewThreadsCount()
            && Objects.equals(getReviews(), that.getReviews());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), url, number, title, state, isDraft, author, createdAt, updatedAt, mergeable, viewerCanUpdate,
            viewerDidAuthor, getPrId(), getAssignees(), getLabels(), getReviewRequests(), getUnresolvedReviewThreadsCount(), getReviews());
    }

    public GHPRIdentifier getPrId() {
        return new GHPRIdentifier(getId(), number);
    }

    public List<GHUser> getAssignees() {
        return assigneesNodes.getNodes();
    }

    public List<GHLabel> getLabels() {
        return labelsNodes == null ? List.of() : labelsNodes.getNodes();
    }

    public List<GHPullRequestReviewRequest> getReviewRequests() {
        return reviewRequestsNodes == null ? List.of() : reviewRequestsNodes.getNodes();
    }

    public int getUnresolvedReviewThreadsCount() {
        int count = 0;
        for (ReviewThreadDetails thread : reviewThreadsNodes.getNodes()) {
            if (!thread.isResolved() && !thread.isOutdated()) {
                count++;
            }
        }
        return count;
    }

    public List<GHPullRequestReview> getReviews() {
        return reviewsNodes == null ? List.of() : reviewsNodes.getNodes();
    }

    @Override
    public String toString() {
        return "#" + number + " " + title;
    }

    public static class ReviewThreadDetails {
        private final boolean isResolved;
        private final boolean isOutdated;

        protected ReviewThreadDetails() {
            this(false, false);
        }

        public ReviewThreadDetails(boolean isResolved, boolean isOutdated) {
            this.isResolved = isResolved;
            this.isOutdated = isOutdated;
        }

        public boolean isResolved() {
            return isResolved;
        }

        public boolean isOutdated() {
            return isOutdated;
        }
    }
}
