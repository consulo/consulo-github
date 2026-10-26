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
import consulo.review.util.SequenceComputer;
import consulo.review.util.SequenceItem;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem;

import java.util.List;

public interface GHPRTimelineDataProvider {
    /**
     * Signals that the whole {@link #getTimelineItems} sequence might have changed
     */
    void addTimelineChangeListener(Disposable parentDisposable, Runnable listener);

    /**
     * Signals that the new items might have appeared in the {@link #getTimelineItems} sequence,
     * and a client might want to call {@link SequenceComputer#computeNext} again
     */
    void addTimelineUpdateListener(Disposable parentDisposable, Runnable listener);

    /**
     * Returns an "infinite" {@link SequenceComputer} which can be polled for new items
     */
    SequenceComputer<SequenceItem<List<GHPRTimelineItem>>> getTimelineItems();

    /**
     * Clear the caches and signal that the timeline needs to be fully reloaded
     */
    void signalTimelineNeedsReload();
}
