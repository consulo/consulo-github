// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline;

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

public class GHPRDisconnectedEvent implements GHPRTimelineEvent.Complex {
    private final @Nullable GHActor actor;
    private final Date createdAt;
    private final GHPRReferencedSubject subject;

    protected GHPRDisconnectedEvent() {
        this.actor = null;
        this.createdAt = null;
        this.subject = null;
    }

    public GHPRDisconnectedEvent(@Nullable GHActor actor, Date createdAt, GHPRReferencedSubject subject) {
        this.actor = actor;
        this.createdAt = createdAt;
        this.subject = subject;
    }

    @Override
    public @Nullable GHActor getActor() {
        return actor;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    public GHPRReferencedSubject getSubject() {
        return subject;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRDisconnectedEvent that)) {
            return false;
        }
        return Objects.equals(actor, that.actor)
            && Objects.equals(createdAt, that.createdAt)
            && Objects.equals(subject, that.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, createdAt, subject);
    }
}
