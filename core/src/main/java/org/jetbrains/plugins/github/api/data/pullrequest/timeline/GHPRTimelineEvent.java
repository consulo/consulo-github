// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline;

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState;
import org.jspecify.annotations.Nullable;

import java.util.Date;

public interface GHPRTimelineEvent extends GHPRTimelineItem {
    @Nullable
    GHActor getActor();

    @Override
    Date getCreatedAt();

    /**
     * Simple events which can be merged together
     */
    interface Simple extends GHPRTimelineEvent {
    }

    /**
     * Events about pull request state
     */
    interface State extends GHPRTimelineEvent {
        GHPullRequestState getNewState();
    }

    /**
     * More complex events which can NOT be merged together
     */
    interface Complex extends GHPRTimelineEvent {
    }

    /**
     * Pull request head/base branch changes events
     */
    interface Branch extends Complex {
    }
}
