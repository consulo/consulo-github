// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort;
import org.jspecify.annotations.Nullable;

import java.util.Date;

public class GHPRTimelineGroupedCommits extends GHPRTimelineGroupedItems<GHPullRequestCommitShort> {
    @Override
    public @Nullable Date getCreatedAt() {
        Date result = null;
        for (GHPullRequestCommitShort item : getItems()) {
            Date createdAt = item.getCreatedAt();
            if (createdAt != null && (result == null || createdAt.after(result))) {
                result = createdAt;
            }
        }
        return result;
    }
}
