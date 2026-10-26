// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.data.provider;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.proxy.EventDispatcher;
import consulo.review.async.ListPartSequenceWithMutableCache;
import consulo.review.util.SequenceComputer;
import consulo.review.util.SequenceItem;
import org.jetbrains.plugins.github.api.data.GHIssueComment;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRTimelineService;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GHPRTimelineDataProviderImpl implements GHPRTimelineDataProvider {
    private final List<Runnable> myChangeListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> myUpdateListeners = new CopyOnWriteArrayList<>();

    private final ListPartSequenceWithMutableCache<GHPRTimelineItem> myLoader;

    public GHPRTimelineDataProviderImpl(Disposable parentDisposable,
                                        GHPRTimelineService timelineService,
                                        EventDispatcher<GHPRDataOperationsListener> messageBus,
                                        GHPRIdentifier id) {
        myLoader = new ListPartSequenceWithMutableCache<>(parentDisposable, timelineService.getTimelineItems(id));

        // The granular in-place item updates the old loader performed are preserved on the cache below, but instead of
        // firing item-level notifications we just tell consumers the whole sequence might have changed.
        messageBus.addListener(new GHPRDataOperationsListener() {
            @Override
            public void onMetadataChanged() {
                signalUpdate();
            }

            @Override
            public void onCommentAdded() {
                signalUpdate();
            }

            @Override
            public void onCommentUpdated(String commentId, String newBody) {
                myLoader.updateItem(it -> it instanceof GHIssueComment c && c.getId().equals(commentId) ? c.withBody(newBody) : null);
                myChangeListeners.forEach(Runnable::run);
            }

            @Override
            public void onCommentDeleted(String commentId) {
                // need to reload the whole thing, bc the cursors might have changed
                signalTimelineNeedsReload();
            }

            @Override
            public void onReviewsChanged() {
                signalUpdate();
            }

            @Override
            public void onReviewUpdated(String reviewId, String newBody) {
                myLoader.updateItem(it -> it instanceof GHPullRequestReview r && r.getId().equals(reviewId) ? r.withBody(newBody) : null);
                myChangeListeners.forEach(Runnable::run);
            }
        }, parentDisposable);
    }

    private void signalUpdate() {
        myUpdateListeners.forEach(Runnable::run);
    }

    @Override
    public void addTimelineChangeListener(Disposable parentDisposable, Runnable listener) {
        myChangeListeners.add(listener);
        Disposer.register(parentDisposable, () -> myChangeListeners.remove(listener));
    }

    @Override
    public void addTimelineUpdateListener(Disposable parentDisposable, Runnable listener) {
        myUpdateListeners.add(listener);
        Disposer.register(parentDisposable, () -> myUpdateListeners.remove(listener));
    }

    @Override
    public SequenceComputer<SequenceItem<List<GHPRTimelineItem>>> getTimelineItems() {
        return myLoader.getComputer();
    }

    @Override
    public void signalTimelineNeedsReload() {
        myLoader.reset();
        myChangeListeners.forEach(Runnable::run);
    }
}
