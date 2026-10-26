// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline;

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

public class GHPRLabeledEvent implements GHPRTimelineEvent.Simple {
    private final @Nullable GHActor actor;
    private final Date createdAt;
    private final GHLabel label;

    protected GHPRLabeledEvent() {
        this.actor = null;
        this.createdAt = null;
        this.label = null;
    }

    public GHPRLabeledEvent(@Nullable GHActor actor, Date createdAt, GHLabel label) {
        this.actor = actor;
        this.createdAt = createdAt;
        this.label = label;
    }

    @Override
    public @Nullable GHActor getActor() {
        return actor;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    public GHLabel getLabel() {
        return label;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRLabeledEvent that)) {
            return false;
        }
        return Objects.equals(actor, that.actor)
            && Objects.equals(createdAt, that.createdAt)
            && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, createdAt, label);
    }
}
