// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHCommitShort;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

public class GHPRMergedEvent implements GHPRTimelineEvent.State {
    private final @Nullable GHActor actor;
    private final Date createdAt;
    @JsonProperty("mergedEventCommit")
    private final @Nullable GHCommitShort commit;
    private final String mergeRefName;

    protected GHPRMergedEvent() {
        this.actor = null;
        this.createdAt = null;
        this.commit = null;
        this.mergeRefName = "";
    }

    public GHPRMergedEvent(@Nullable GHActor actor, Date createdAt, @Nullable GHCommitShort commit, String mergeRefName) {
        this.actor = actor;
        this.createdAt = createdAt;
        this.commit = commit;
        this.mergeRefName = mergeRefName;
    }

    @Override
    public @Nullable GHActor getActor() {
        return actor;
    }

    @Override
    public Date getCreatedAt() {
        return createdAt;
    }

    public @Nullable GHCommitShort getCommit() {
        return commit;
    }

    public String getMergeRefName() {
        return mergeRefName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRMergedEvent that)) {
            return false;
        }
        return Objects.equals(actor, that.actor)
            && Objects.equals(createdAt, that.createdAt)
            && Objects.equals(commit, that.commit)
            && Objects.equals(mergeRefName, that.mergeRefName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, createdAt, commit, mergeRefName);
    }

    @Override
    public GHPullRequestState getNewState() {
        return GHPullRequestState.MERGED;
    }
}
