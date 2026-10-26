// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

public class GHPRReviewDismissedEvent implements GHPRTimelineEvent.Complex {
    private final @Nullable GHActor actor;
    private final Date createdAt;
    private final @Nullable String dismissalMessageHTML;
    @JsonProperty("review")
    private final @Nullable ReviewAuthor review;

    protected GHPRReviewDismissedEvent() {
        this.actor = null;
        this.createdAt = null;
        this.dismissalMessageHTML = null;
        this.review = null;
    }

    public GHPRReviewDismissedEvent(@Nullable GHActor actor,
                                    Date createdAt,
                                    @Nullable String dismissalMessageHTML,
                                    @Nullable ReviewAuthor review) {
        this.actor = actor;
        this.createdAt = createdAt;
        this.dismissalMessageHTML = dismissalMessageHTML;
        this.review = review;
    }

    @Override
    public @Nullable GHActor getActor() {
        return actor;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    public @Nullable String getDismissalMessageHTML() {
        return dismissalMessageHTML;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRReviewDismissedEvent that)) {
            return false;
        }
        return Objects.equals(actor, that.actor)
            && Objects.equals(createdAt, that.createdAt)
            && Objects.equals(dismissalMessageHTML, that.dismissalMessageHTML)
            && Objects.equals(review, that.review);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, createdAt, dismissalMessageHTML, review);
    }

    public @Nullable GHActor getReviewAuthor() {
        return review == null ? null : review.getAuthor();
    }

    public static class ReviewAuthor {
        private final @Nullable GHActor author;

        protected ReviewAuthor() {
            this(null);
        }

        public ReviewAuthor(@Nullable GHActor author) {
            this.author = author;
        }

        public @Nullable GHActor getAuthor() {
            return author;
        }
    }
}
