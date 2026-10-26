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

import consulo.application.Application;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class GHPRTimelineMergingModel {
    private static final long MERGE_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(2);

    private final List<GHPRTimelineItem> myUnmergedList = new ArrayList<>();
    private final List<GHPRTimelineItem> myList = new ArrayList<>();

    public GHPRTimelineItem getElementAt(int index) {
        return myList.get(index);
    }

    public int getSize() {
        return myList.size();
    }

    public void add(List<? extends GHPRTimelineItem> items) {
        GHPRTimelineItem lastItem = myList.isEmpty() ? null : myList.get(myList.size() - 1);
        if (lastItem != null) {
            myList.remove(myList.size() - 1);
        }

        boolean hideUnknown = !Application.get().isInternal();
        for (GHPRTimelineItem item : items) {
            myUnmergedList.add(item);
            if (item instanceof GHPRTimelineItem.Unknown unknown
                && (hideUnknown || GHPRTimelineItem.IGNORED_TYPES.contains(unknown.get__typename()))) {
                continue;
            }
            GHPRTimelineItem merged = mergeIfPossible(lastItem, item);
            if (merged != null) {
                lastItem = merged;
            }
            else {
                if (lastItem != null && !isCollapsedMerge(lastItem)) {
                    myList.add(lastItem);
                }
                lastItem = item;
            }
        }
        if (lastItem != null && !isCollapsedMerge(lastItem)) {
            myList.add(lastItem);
        }
    }

    public void update(int idx, GHPRTimelineItem newItem) {
        if (idx < 0 || idx >= myUnmergedList.size()) {
            return;
        }
        GHPRTimelineItem originalItem = myUnmergedList.get(idx);
        int mergedIdx = myList.indexOf(originalItem);
        if (mergedIdx >= 0) {
            myUnmergedList.set(idx, newItem);
            myList.set(mergedIdx, newItem);
        }
    }

    public void remove(int idx) {
        if (idx < 0 || idx >= myUnmergedList.size()) {
            return;
        }
        GHPRTimelineItem originalItem = myUnmergedList.get(idx);
        int mergedIdx = myList.indexOf(originalItem);
        if (mergedIdx >= 0) {
            myUnmergedList.remove(idx);
            myList.remove(mergedIdx);
        }
    }

    public void removeAll() {
        myList.clear();
    }

    private static @Nullable GHPRTimelineItem mergeIfPossible(@Nullable GHPRTimelineItem existing, @Nullable GHPRTimelineItem newItem) {
        GHPRTimelineItem groupedCommits = tryGroupCommits(existing, newItem);
        if (groupedCommits != null) {
            return groupedCommits;
        }

        if (!(existing instanceof GHPRTimelineEvent existingEvent) || !(newItem instanceof GHPRTimelineEvent newEvent)) {
            return null;
        }
        if (!Objects.equals(existingEvent.getActor(), newEvent.getActor())) {
            return null;
        }
        if (newEvent.getCreatedAt().getTime() - existingEvent.getCreatedAt().getTime() > MERGE_THRESHOLD_MS) {
            return null;
        }

        if (existingEvent instanceof GHPRTimelineEvent.Simple existingSimple && newEvent instanceof GHPRTimelineEvent.Simple newSimple) {
            if (existingSimple instanceof GHPRTimelineMergedSimpleEvents merged) {
                merged.add(newSimple);
                return merged;
            }
            else {
                GHPRTimelineMergedSimpleEvents merged = new GHPRTimelineMergedSimpleEvents();
                merged.add(existingSimple);
                merged.add(newSimple);
                return merged;
            }
        }
        else if (existingEvent instanceof GHPRTimelineEvent.State existingState && newEvent instanceof GHPRTimelineEvent.State newState) {
            if (existingState instanceof GHPRTimelineMergedStateEvents merged) {
                merged.add(newState);
                return merged;
            }
            else {
                GHPRTimelineMergedStateEvents merged = new GHPRTimelineMergedStateEvents(existingState);
                merged.add(newState);
                return merged;
            }
        }
        return null;
    }

    private static @Nullable GHPRTimelineItem tryGroupCommits(@Nullable GHPRTimelineItem existing, @Nullable GHPRTimelineItem newItem) {
        if (existing instanceof GHPullRequestCommitShort existingCommit && newItem instanceof GHPullRequestCommitShort newCommit) {
            GHPRTimelineGroupedCommits grouped = new GHPRTimelineGroupedCommits();
            grouped.add(existingCommit);
            grouped.add(newCommit);
            return grouped;
        }

        if (existing instanceof GHPRTimelineGroupedCommits grouped && newItem instanceof GHPullRequestCommitShort newCommit) {
            grouped.add(newCommit);
            return grouped;
        }

        if (existing instanceof GHPullRequestCommitShort existingCommit && newItem instanceof GHPRTimelineGroupedCommits newGrouped) {
            GHPRTimelineGroupedCommits grouped = new GHPRTimelineGroupedCommits();
            grouped.add(existingCommit);
            for (GHPullRequestCommitShort item : newGrouped.getItems()) {
                grouped.add(item);
            }
            return grouped;
        }

        if (existing instanceof GHPRTimelineGroupedCommits grouped && newItem instanceof GHPRTimelineGroupedCommits newGrouped) {
            for (GHPullRequestCommitShort item : newGrouped.getItems()) {
                grouped.add(item);
            }
            return grouped;
        }
        return null;
    }

    private static boolean isCollapsedMerge(GHPRTimelineItem event) {
        return event instanceof GHPRTimelineMergedEvents<?> merged && !merged.hasAnyChanges();
    }
}
