// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.filters;

import consulo.review.list.search.PersistingReviewListSearchHistoryModel;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class GHPRSearchHistoryModel extends PersistingReviewListSearchHistoryModel<GHPRListSearchValue> {
    private final GHPRListPersistentSearchHistory myPersistentHistoryComponent;

    public GHPRSearchHistoryModel(GHPRListPersistentSearchHistory persistentHistoryComponent) {
        myPersistentHistoryComponent = persistentHistoryComponent;
    }

    @Override
    public @Nullable GHPRListSearchValue getLastFilter() {
        return myPersistentHistoryComponent.getLastFilter();
    }

    @Override
    public void setLastFilter(@Nullable GHPRListSearchValue lastFilter) {
        myPersistentHistoryComponent.setLastFilter(lastFilter);
    }

    @Override
    protected List<GHPRListSearchValue> getPersistentHistory() {
        return myPersistentHistoryComponent.getHistory();
    }

    @Override
    protected void setPersistentHistory(List<GHPRListSearchValue> history) {
        myPersistentHistoryComponent.setHistory(history);
    }
}
