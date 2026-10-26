// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline;

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

public class GHPRRenamedTitleEvent implements GHPRTimelineEvent.Simple {
    private final @Nullable GHActor actor;
    private final Date createdAt;
    private final String previousTitle;
    private final String currentTitle;

    protected GHPRRenamedTitleEvent() {
        this.actor = null;
        this.createdAt = null;
        this.previousTitle = "";
        this.currentTitle = "";
    }

    public GHPRRenamedTitleEvent(@Nullable GHActor actor, Date createdAt, String previousTitle, String currentTitle) {
        this.actor = actor;
        this.createdAt = createdAt;
        this.previousTitle = previousTitle;
        this.currentTitle = currentTitle;
    }

    @Override
    public @Nullable GHActor getActor() {
        return actor;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    public String getPreviousTitle() {
        return previousTitle;
    }

    public String getCurrentTitle() {
        return currentTitle;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRRenamedTitleEvent that)) {
            return false;
        }
        return Objects.equals(actor, that.actor)
            && Objects.equals(createdAt, that.createdAt)
            && Objects.equals(previousTitle, that.previousTitle)
            && Objects.equals(currentTitle, that.currentTitle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, createdAt, previousTitle, currentTitle);
    }
}
