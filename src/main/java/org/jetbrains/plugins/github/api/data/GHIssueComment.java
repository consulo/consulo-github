// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.Objects;

public class GHIssueComment extends GHComment implements GHPRTimelineItem {
    private final boolean viewerCanDelete;
    private final boolean viewerCanUpdate;
    private final boolean viewerCanReact;

    protected GHIssueComment() {
        super("", null, "", null, null);
        this.viewerCanDelete = false;
        this.viewerCanUpdate = false;
        this.viewerCanReact = false;
    }

    public GHIssueComment(String id,
                          @Nullable GHActor author,
                          String body,
                          Date createdAt,
                          GHReactable.ReactionConnection reactions,
                          boolean viewerCanDelete,
                          boolean viewerCanUpdate,
                          boolean viewerCanReact) {
        super(id, author, body, createdAt, reactions);
        this.viewerCanDelete = viewerCanDelete;
        this.viewerCanUpdate = viewerCanUpdate;
        this.viewerCanReact = viewerCanReact;
    }

    public boolean getViewerCanDelete() {
        return viewerCanDelete;
    }

    public boolean getViewerCanUpdate() {
        return viewerCanUpdate;
    }

    public boolean getViewerCanReact() {
        return viewerCanReact;
    }

    public GHIssueComment withBody(String body) {
        return new GHIssueComment(getId(), getAuthor(), body, getCreatedAt(), getReactions(), viewerCanDelete, viewerCanUpdate, viewerCanReact);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHIssueComment that)) {
            return false;
        }
        return viewerCanDelete == that.viewerCanDelete
            && viewerCanUpdate == that.viewerCanUpdate
            && viewerCanReact == that.viewerCanReact;
    }

    @Override
    public int hashCode() {
        return Objects.hash(viewerCanDelete, viewerCanUpdate, viewerCanReact);
    }
}
