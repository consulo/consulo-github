// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline;

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

public class GHPRReviewRequestedEvent implements GHPRTimelineEvent.Simple {
    private final @Nullable GHActor actor;
    private final Date createdAt;
    private final @Nullable GHPullRequestRequestedReviewer requestedReviewer;

    protected GHPRReviewRequestedEvent() {
        this.actor = null;
        this.createdAt = null;
        this.requestedReviewer = null;
    }

    public GHPRReviewRequestedEvent(@Nullable GHActor actor, Date createdAt, @Nullable GHPullRequestRequestedReviewer requestedReviewer) {
        this.actor = actor;
        this.createdAt = createdAt;
        this.requestedReviewer = requestedReviewer;
    }

    @Override
    public @Nullable GHActor getActor() {
        return actor;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    public @Nullable GHPullRequestRequestedReviewer getRequestedReviewer() {
        return requestedReviewer;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRReviewRequestedEvent that)) {
            return false;
        }
        return Objects.equals(actor, that.actor)
            && Objects.equals(createdAt, that.createdAt)
            && Objects.equals(requestedReviewer, that.requestedReviewer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, createdAt, requestedReviewer);
    }
}
