// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline;

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

public class GHPRHeadRefDeletedEvent implements GHPRTimelineEvent.Branch {
    private final @Nullable GHActor actor;
    private final Date createdAt;
    private final String headRefName;

    protected GHPRHeadRefDeletedEvent() {
        this.actor = null;
        this.createdAt = null;
        this.headRefName = "";
    }

    public GHPRHeadRefDeletedEvent(@Nullable GHActor actor, Date createdAt, String headRefName) {
        this.actor = actor;
        this.createdAt = createdAt;
        this.headRefName = headRefName;
    }

    @Override
    public @Nullable GHActor getActor() {
        return actor;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    public String getHeadRefName() {
        return headRefName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRHeadRefDeletedEvent that)) {
            return false;
        }
        return Objects.equals(actor, that.actor)
            && Objects.equals(createdAt, that.createdAt)
            && Objects.equals(headRefName, that.headRefName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, createdAt, headRefName);
    }
}
