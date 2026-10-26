// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.List;

public abstract class GHPRTimelineMergedEvents<T extends GHPRTimelineEvent> extends GHPRTimelineGroupedItems<T>
    implements GHPRTimelineEvent {
    @Override
    public @Nullable GHActor getActor() {
        List<T> items = getItems();
        return items.get(items.size() - 1).getActor();
    }

    @Override
    public Date getCreatedAt() {
        Date result = null;
        for (T item : getItems()) {
            Date createdAt = item.getCreatedAt();
            if (result == null || createdAt.after(result)) {
                result = createdAt;
            }
        }
        if (result == null) {
            throw new IllegalStateException("No merged events");
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void add(T item) {
        super.add(item);
        if (item instanceof GHPRTimelineMergedEvents<?> merged) {
            for (Object evt : merged.getItems()) {
                add((T) evt);
            }
        }
        else {
            addNonMergedEvent(item);
        }
    }

    protected abstract void addNonMergedEvent(T event);

    public abstract boolean hasAnyChanges();
}
