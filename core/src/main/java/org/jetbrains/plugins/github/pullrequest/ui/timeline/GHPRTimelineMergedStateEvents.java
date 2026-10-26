// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github.pullrequest.ui.timeline;

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent;

public class GHPRTimelineMergedStateEvents extends GHPRTimelineMergedEvents<GHPRTimelineEvent.State> implements GHPRTimelineEvent.State {
    private final GHPullRequestState myInferredOriginalState;

    private GHPullRequestState myNewState;

    private GHPRTimelineEvent.State myLastStateEvent;

    public GHPRTimelineMergedStateEvents(GHPRTimelineEvent.State initialState) {
        myInferredOriginalState = switch (initialState.getNewState()) {
            case CLOSED, MERGED -> GHPullRequestState.OPEN;
            case OPEN -> GHPullRequestState.CLOSED;
        };
        myNewState = initialState.getNewState();
        myLastStateEvent = initialState;
        add(initialState);
    }

    @Override
    public GHPullRequestState getNewState() {
        return myNewState;
    }

    public GHPRTimelineEvent.State getLastStateEvent() {
        return myLastStateEvent;
    }

    @Override
    protected void addNonMergedEvent(GHPRTimelineEvent.State event) {
        if (myNewState != GHPullRequestState.MERGED) {
            myNewState = event.getNewState();
            myLastStateEvent = event;
        }
    }

    @Override
    public boolean hasAnyChanges() {
        return myNewState != myInferredOriginalState;
    }
}
