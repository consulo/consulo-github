// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline;

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

public class GHPRAssignedEvent implements GHPRTimelineEvent.Simple {
    private final @Nullable GHActor actor;
    private final Date createdAt;
    private final @Nullable GHUser user;

    protected GHPRAssignedEvent() {
        this.actor = null;
        this.createdAt = null;
        this.user = null;
    }

    public GHPRAssignedEvent(@Nullable GHActor actor, Date createdAt, @Nullable GHUser user) {
        this.actor = actor;
        this.createdAt = createdAt;
        this.user = user;
    }

    @Override
    public @Nullable GHActor getActor() {
        return actor;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    public @Nullable GHUser getUser() {
        return user;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRAssignedEvent that)) {
            return false;
        }
        return Objects.equals(actor, that.actor)
            && Objects.equals(createdAt, that.createdAt)
            && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, createdAt, user);
    }
}
