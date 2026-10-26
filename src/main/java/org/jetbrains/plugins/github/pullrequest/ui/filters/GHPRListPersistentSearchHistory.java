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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.project.Project;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Singleton
@State(name = "GitHubPullRequestSearchHistory", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GHPRListPersistentSearchHistory implements PersistentStateComponent<GHPRListPersistentSearchHistory.HistoryState> {
    public static class SearchValueState {
        public @Nullable String searchQuery;
        public GHPRListSearchValue.@Nullable State state;
        public @Nullable String assignee;
        public GHPRListSearchValue.@Nullable ReviewState reviewState;
        public @Nullable String author;
        public @Nullable String label;
        public GHPRListSearchValue.@Nullable Sort sort;

        public SearchValueState() {
        }

        SearchValueState(GHPRListSearchValue value) {
            searchQuery = value.searchQuery();
            state = value.state();
            assignee = value.assignee();
            reviewState = value.reviewState();
            author = value.author();
            label = value.label();
            sort = value.sort();
        }

        GHPRListSearchValue toValue() {
            return new GHPRListSearchValue(searchQuery, state, assignee, reviewState, author, label, sort);
        }
    }

    public static class HistoryState {
        public List<SearchValueState> history = new ArrayList<>();
        public @Nullable SearchValueState lastFilter;
    }

    private HistoryState myState = new HistoryState();

    public static GHPRListPersistentSearchHistory getInstance(Project project) {
        return project.getInstance(GHPRListPersistentSearchHistory.class);
    }

    public synchronized @Nullable GHPRListSearchValue getLastFilter() {
        return myState.lastFilter == null ? null : myState.lastFilter.toValue();
    }

    public synchronized void setLastFilter(@Nullable GHPRListSearchValue value) {
        myState.lastFilter = value == null ? null : new SearchValueState(value);
    }

    public synchronized List<GHPRListSearchValue> getHistory() {
        return myState.history.stream().map(SearchValueState::toValue).toList();
    }

    public synchronized void setHistory(List<GHPRListSearchValue> value) {
        myState.history = new ArrayList<>(value.stream().map(SearchValueState::new).toList());
    }

    @Override
    public synchronized HistoryState getState() {
        return myState;
    }

    @Override
    public synchronized void loadState(HistoryState state) {
        myState = state;
    }
}
