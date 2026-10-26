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

import consulo.util.lang.Pair;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRAssignedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRLabeledEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRRenamedTitleEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRReviewRequestedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRReviewUnrequestedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRUnassignedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRUnlabeledEvent;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class GHPRTimelineMergedSimpleEvents extends GHPRTimelineMergedEvents<GHPRTimelineEvent.Simple> implements GHPRTimelineEvent.Simple {
    private final Set<GHLabel> myAddedLabels = new LinkedHashSet<>();
    private final Set<GHLabel> myRemovedLabels = new LinkedHashSet<>();

    private final Set<GHUser> myAssignedPeople = new LinkedHashSet<>();
    private final Set<GHUser> myUnassignedPeople = new LinkedHashSet<>();

    private final Set<GHPullRequestRequestedReviewer> myAddedReviewers = new LinkedHashSet<>();
    private final Set<GHPullRequestRequestedReviewer> myRemovedReviewers = new LinkedHashSet<>();

    private @Nullable Pair<String, String> myRename;

    public Set<GHLabel> getAddedLabels() {
        return Collections.unmodifiableSet(myAddedLabels);
    }

    public Set<GHLabel> getRemovedLabels() {
        return Collections.unmodifiableSet(myRemovedLabels);
    }

    public Set<GHUser> getAssignedPeople() {
        return Collections.unmodifiableSet(myAssignedPeople);
    }

    public Set<GHUser> getUnassignedPeople() {
        return Collections.unmodifiableSet(myUnassignedPeople);
    }

    public Set<GHPullRequestRequestedReviewer> getAddedReviewers() {
        return Collections.unmodifiableSet(myAddedReviewers);
    }

    public Set<GHPullRequestRequestedReviewer> getRemovedReviewers() {
        return Collections.unmodifiableSet(myRemovedReviewers);
    }

    public @Nullable Pair<String, String> getRename() {
        Pair<String, String> rename = myRename;
        return rename != null && !rename.getFirst().equals(rename.getSecond()) ? rename : null;
    }

    @Override
    protected void addNonMergedEvent(GHPRTimelineEvent.Simple event) {
        if (event instanceof GHPRLabeledEvent labeled) {
            if (!myRemovedLabels.remove(labeled.getLabel())) {
                myAddedLabels.add(labeled.getLabel());
            }
        }
        else if (event instanceof GHPRUnlabeledEvent unlabeled) {
            if (!myAddedLabels.remove(unlabeled.getLabel())) {
                myRemovedLabels.add(unlabeled.getLabel());
            }
        }
        else if (event instanceof GHPRAssignedEvent assigned) {
            GHUser user = assigned.getUser();
            if (user != null && !myUnassignedPeople.remove(user)) {
                myAssignedPeople.add(user);
            }
        }
        else if (event instanceof GHPRUnassignedEvent unassigned) {
            GHUser user = unassigned.getUser();
            if (user != null && !myAssignedPeople.remove(user)) {
                myUnassignedPeople.add(user);
            }
        }
        else if (event instanceof GHPRReviewRequestedEvent requested) {
            GHPullRequestRequestedReviewer reviewer = requested.getRequestedReviewer();
            if (reviewer != null && !myRemovedReviewers.remove(reviewer)) {
                myAddedReviewers.add(reviewer);
            }
        }
        else if (event instanceof GHPRReviewUnrequestedEvent unrequested) {
            GHPullRequestRequestedReviewer reviewer = unrequested.getRequestedReviewer();
            if (reviewer != null && !myAddedReviewers.remove(reviewer)) {
                myRemovedReviewers.add(reviewer);
            }
        }
        else if (event instanceof GHPRRenamedTitleEvent renamed) {
            Pair<String, String> rename = myRename;
            myRename = Pair.create(rename != null ? rename.getFirst() : renamed.getPreviousTitle(), renamed.getCurrentTitle());
        }
    }

    @Override
    public boolean hasAnyChanges() {
        return !getAssignedPeople().isEmpty() || !getUnassignedPeople().isEmpty()
            || !getAddedLabels().isEmpty() || !getRemovedLabels().isEmpty()
            || !getAddedReviewers().isEmpty() || !getRemovedReviewers().isEmpty()
            || getRename() != null;
    }
}
