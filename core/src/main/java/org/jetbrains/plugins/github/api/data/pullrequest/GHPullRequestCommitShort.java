// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import org.jspecify.annotations.Nullable;
import org.jetbrains.plugins.github.api.data.GHCommitShort;
import org.jetbrains.plugins.github.api.data.GHNode;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem;

import java.util.Date;
import java.util.Objects;

public class GHPullRequestCommitShort extends GHNode implements GHPRTimelineItem {
    private final GHCommitShort commit;
    private final String url;

    protected GHPullRequestCommitShort() {
        super("");
        this.commit = null;
        this.url = "";
    }

    public GHPullRequestCommitShort(String id, GHCommitShort commit, String url) {
        super(id);
        this.commit = commit;
        this.url = url;
    }

    public GHCommitShort getCommit() {
        return commit;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPullRequestCommitShort that)) {
            return false;
        }
        return Objects.equals(getId(), that.getId())
            && Objects.equals(commit, that.commit)
            && Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), commit, url);
    }

    @Override
    public @Nullable Date getCreatedAt() {
        return null;
    }
}
