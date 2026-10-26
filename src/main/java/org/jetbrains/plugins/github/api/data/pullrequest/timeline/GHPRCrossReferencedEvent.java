// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline;

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

public class GHPRCrossReferencedEvent implements GHPRTimelineEvent.Complex {
    private final @Nullable GHActor actor;
    private final Date createdAt;
    private final GHPRReferencedSubject source;

    protected GHPRCrossReferencedEvent() {
        this.actor = null;
        this.createdAt = null;
        this.source = null;
    }

    public GHPRCrossReferencedEvent(@Nullable GHActor actor, Date createdAt, GHPRReferencedSubject source) {
        this.actor = actor;
        this.createdAt = createdAt;
        this.source = source;
    }

    @Override
    public @Nullable GHActor getActor() {
        return actor;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    public GHPRReferencedSubject getSource() {
        return source;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRCrossReferencedEvent that)) {
            return false;
        }
        return Objects.equals(actor, that.actor)
            && Objects.equals(createdAt, that.createdAt)
            && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, createdAt, source);
    }
}
