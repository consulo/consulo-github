// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline;

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHCommitHash;
import org.jetbrains.plugins.github.api.data.pullrequest.GHGitRefName;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

public class GHPRBaseRefForcePushedEvent implements GHPRTimelineEvent.Branch {
    private final @Nullable GHActor actor;
    private final Date createdAt;
    private final @Nullable GHGitRefName ref;
    private final @Nullable GHCommitHash beforeCommit;
    private final @Nullable GHCommitHash afterCommit;

    protected GHPRBaseRefForcePushedEvent() {
        this.actor = null;
        this.createdAt = null;
        this.ref = null;
        this.beforeCommit = null;
        this.afterCommit = null;
    }

    public GHPRBaseRefForcePushedEvent(@Nullable GHActor actor,
                                       Date createdAt,
                                       @Nullable GHGitRefName ref,
                                       @Nullable GHCommitHash beforeCommit,
                                       @Nullable GHCommitHash afterCommit) {
        this.actor = actor;
        this.createdAt = createdAt;
        this.ref = ref;
        this.beforeCommit = beforeCommit;
        this.afterCommit = afterCommit;
    }

    @Override
    public @Nullable GHActor getActor() {
        return actor;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    public @Nullable GHGitRefName getRef() {
        return ref;
    }

    public @Nullable GHCommitHash getBeforeCommit() {
        return beforeCommit;
    }

    public @Nullable GHCommitHash getAfterCommit() {
        return afterCommit;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRBaseRefForcePushedEvent that)) {
            return false;
        }
        return Objects.equals(actor, that.actor)
            && Objects.equals(createdAt, that.createdAt)
            && Objects.equals(ref, that.ref)
            && Objects.equals(beforeCommit, that.beforeCommit)
            && Objects.equals(afterCommit, that.afterCommit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, createdAt, ref, beforeCommit, afterCommit);
    }
}
