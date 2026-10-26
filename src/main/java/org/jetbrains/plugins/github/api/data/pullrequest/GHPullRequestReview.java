// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHNode;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

@GraphQLFragment("/graphql/fragment/pullRequestReview.graphql")
public class GHPullRequestReview extends GHNode implements GHPRTimelineItem {
    private final String url;
    private final @Nullable GHActor author;
    private final String body;
    private final GHPullRequestReviewState state;
    private final Date createdAt;
    private final boolean viewerCanUpdate;

    protected GHPullRequestReview() {
        super("");
        this.url = "";
        this.author = null;
        this.body = "";
        this.state = null;
        this.createdAt = null;
        this.viewerCanUpdate = false;
    }

    public GHPullRequestReview(String id,
                               String url,
                               @Nullable GHActor author,
                               String body,
                               GHPullRequestReviewState state,
                               Date createdAt,
                               boolean viewerCanUpdate) {
        super(id);
        this.url = url;
        this.author = author;
        this.body = body;
        this.state = state;
        this.createdAt = createdAt;
        this.viewerCanUpdate = viewerCanUpdate;
    }

    public String getUrl() {
        return url;
    }

    public @Nullable GHActor getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public GHPullRequestReviewState getState() {
        return state;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    public boolean getViewerCanUpdate() {
        return viewerCanUpdate;
    }

    public GHPullRequestReview withBody(String body) {
        return new GHPullRequestReview(getId(), url, author, body, state, createdAt, viewerCanUpdate);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPullRequestReview that)) {
            return false;
        }
        return Objects.equals(url, that.url)
            && Objects.equals(author, that.author)
            && Objects.equals(body, that.body)
            && Objects.equals(state, that.state)
            && Objects.equals(createdAt, that.createdAt)
            && viewerCanUpdate == that.viewerCanUpdate;
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, author, body, state, createdAt, viewerCanUpdate);
    }
}
