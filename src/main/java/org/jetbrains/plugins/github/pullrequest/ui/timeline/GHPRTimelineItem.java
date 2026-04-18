// Copyright 2013-2025 consulo.io
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
package org.jetbrains.plugins.github.pullrequest.ui.timeline;

import org.jspecify.annotations.Nullable;

import java.util.Date;

/**
 * A single item in a PR timeline (comment, review, commit, event).
 */
public final class GHPRTimelineItem {
    private final GHPRTimelineItemType myType;
    private final String myId;
    private final @Nullable String myAuthorLogin;
    private final @Nullable String myAuthorAvatarUrl;
    private final @Nullable String myBody;
    private final @Nullable Date myCreatedAt;
    private final @Nullable String myEventType;

    public GHPRTimelineItem(GHPRTimelineItemType type, String id, @Nullable String authorLogin,
                            @Nullable String authorAvatarUrl, @Nullable String body,
                            @Nullable Date createdAt, @Nullable String eventType) {
        myType = type;
        myId = id;
        myAuthorLogin = authorLogin;
        myAuthorAvatarUrl = authorAvatarUrl;
        myBody = body;
        myCreatedAt = createdAt;
        myEventType = eventType;
    }

    public GHPRTimelineItemType getType() {
        return myType;
    }

    public String getId() {
        return myId;
    }

    public @Nullable String getAuthorLogin() {
        return myAuthorLogin;
    }

    public @Nullable String getAuthorAvatarUrl() {
        return myAuthorAvatarUrl;
    }

    public @Nullable String getBody() {
        return myBody;
    }

    public @Nullable Date getCreatedAt() {
        return myCreatedAt;
    }

    public @Nullable String getEventType() {
        return myEventType;
    }
}
