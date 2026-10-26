// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHComment;
import org.jetbrains.plugins.github.api.data.GHCommitHash;
import org.jetbrains.plugins.github.api.data.GHNode;
import org.jetbrains.plugins.github.api.data.GHReactable;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

@GraphQLFragment("/graphql/fragment/pullRequestReviewComment.graphql")
public class GHPullRequestReviewComment extends GHComment {
    private final String url;
    private final GHPullRequestReviewCommentState state;
    private final @Nullable GHCommitHash commit;
    private final @Nullable GHCommitHash originalCommit;
    private final String diffHunk;
    @JsonProperty("pullRequestReview")
    private final @Nullable GHNode pullRequestReview;
    private final boolean viewerCanDelete;
    private final boolean viewerCanUpdate;
    private final boolean viewerCanReact;

    protected GHPullRequestReviewComment() {
        super("", null, "", null, null);
        this.url = "";
        this.state = null;
        this.commit = null;
        this.originalCommit = null;
        this.diffHunk = "";
        this.pullRequestReview = null;
        this.viewerCanDelete = false;
        this.viewerCanUpdate = false;
        this.viewerCanReact = false;
    }

    public GHPullRequestReviewComment(String id,
                                      String url,
                                      @Nullable GHActor author,
                                      String body,
                                      Date createdAt,
                                      GHReactable.ReactionConnection reactions,
                                      GHPullRequestReviewCommentState state,
                                      @Nullable GHCommitHash commit,
                                      @Nullable GHCommitHash originalCommit,
                                      String diffHunk,
                                      @Nullable GHNode pullRequestReview,
                                      boolean viewerCanDelete,
                                      boolean viewerCanUpdate,
                                      boolean viewerCanReact) {
        super(id, author, body, createdAt, reactions);
        this.url = url;
        this.state = state;
        this.commit = commit;
        this.originalCommit = originalCommit;
        this.diffHunk = diffHunk;
        this.pullRequestReview = pullRequestReview;
        this.viewerCanDelete = viewerCanDelete;
        this.viewerCanUpdate = viewerCanUpdate;
        this.viewerCanReact = viewerCanReact;
    }

    public GHPullRequestReviewComment withBody(String body) {
        return new GHPullRequestReviewComment(getId(), url, getAuthor(), body, getCreatedAt(), getReactions(), state, commit, originalCommit,
            diffHunk, pullRequestReview, viewerCanDelete, viewerCanUpdate, viewerCanReact);
    }

    public String getUrl() {
        return url;
    }

    public GHPullRequestReviewCommentState getState() {
        return state;
    }

    public @Nullable GHCommitHash getCommit() {
        return commit;
    }

    public @Nullable GHCommitHash getOriginalCommit() {
        return originalCommit;
    }

    public String getDiffHunk() {
        return diffHunk;
    }

    public boolean getViewerCanDelete() {
        return viewerCanDelete;
    }

    public boolean getViewerCanUpdate() {
        return viewerCanUpdate;
    }

    public boolean getViewerCanReact() {
        return viewerCanReact;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPullRequestReviewComment that)) {
            return false;
        }
        return Objects.equals(getId(), that.getId())
            && Objects.equals(url, that.url)
            && Objects.equals(getAuthor(), that.getAuthor())
            && Objects.equals(getBody(), that.getBody())
            && Objects.equals(getCreatedAt(), that.getCreatedAt())
            && Objects.equals(getReactions(), that.getReactions())
            && Objects.equals(state, that.state)
            && Objects.equals(commit, that.commit)
            && Objects.equals(originalCommit, that.originalCommit)
            && Objects.equals(diffHunk, that.diffHunk)
            && Objects.equals(pullRequestReview, that.pullRequestReview)
            && viewerCanDelete == that.viewerCanDelete
            && viewerCanUpdate == that.viewerCanUpdate
            && viewerCanReact == that.viewerCanReact;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), url, getAuthor(), getBody(), getCreatedAt(), getReactions(), state, commit, originalCommit, diffHunk, pullRequestReview, viewerCanDelete, viewerCanUpdate, viewerCanReact);
    }

    public @Nullable String getReviewId() {
        return pullRequestReview == null ? null : pullRequestReview.getId();
    }
}
