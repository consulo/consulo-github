// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.list.search.ReviewListSearchPanelViewModelBase;
import consulo.review.util.IncrementallyComputedValue;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.lazy.LazyValue;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public class GHPRSearchPanelViewModel extends ReviewListSearchPanelViewModelBase<GHPRListSearchValue, GHPRListQuickFilter> {
    private final Disposable myScope;
    private final GHPRRepositoryDataService myRepositoryDataService;
    private final GHPRSecurityService mySecurityService;
    private final List<GHPRListQuickFilter> myQuickFilters;

    private final MutableObservableValue<GHPRListSearchValue.@Nullable State> myStateFilterState;
    private final MutableObservableValue<@Nullable String> myAuthorFilterState;
    private final MutableObservableValue<@Nullable String> myLabelFilterState;
    private final MutableObservableValue<@Nullable String> myAssigneeFilterState;
    private final MutableObservableValue<GHPRListSearchValue.@Nullable ReviewState> myReviewFilterState;
    private final MutableObservableValue<GHPRListSearchValue.@Nullable Sort> mySortFilterState;

    private final LazyValue<ObservableValue<IncrementallyComputedValue<List<GHUser>>>> myAuthors;
    private final LazyValue<ObservableValue<IncrementallyComputedValue<List<GHUser>>>> myAssignees;
    private final LazyValue<ObservableValue<IncrementallyComputedValue<List<GHLabel>>>> myLabels;

    public GHPRSearchPanelViewModel(Disposable scope,
                                    Project project,
                                    GHPRRepositoryDataService repositoryDataService,
                                    GHPRSecurityService securityService,
                                    GHPRSearchHistoryModel historyViewModel) {
        super(scope, historyViewModel, GHPRListSearchValue.EMPTY, new GHPRListQuickFilter.AssignedToYou(securityService.getCurrentUser()).getFilter());
        myScope = scope;
        myRepositoryDataService = repositoryDataService;
        mySecurityService = securityService;

        GHUser currentUser = securityService.getCurrentUser();
        myQuickFilters = List.of(
            new GHPRListQuickFilter.Open(currentUser),
            new GHPRListQuickFilter.YourPullRequests(currentUser),
            new GHPRListQuickFilter.AssignedToYou(currentUser),
            new GHPRListQuickFilter.ReviewRequests(currentUser)
        );

        myStateFilterState = partialState(getSearchState(), GHPRListSearchValue::state, GHPRListSearchValue::withState);
        myAuthorFilterState = partialState(getSearchState(), GHPRListSearchValue::author, GHPRListSearchValue::withAuthor);
        myLabelFilterState = partialState(getSearchState(), GHPRListSearchValue::label, GHPRListSearchValue::withLabel);
        myAssigneeFilterState = partialState(getSearchState(), GHPRListSearchValue::assignee, GHPRListSearchValue::withAssignee);
        myReviewFilterState = partialState(getSearchState(), GHPRListSearchValue::reviewState, GHPRListSearchValue::withReviewState);
        mySortFilterState = partialState(getSearchState(), GHPRListSearchValue::sort, GHPRListSearchValue::withSort);

        myAuthors = LazyValue.atomicNotNull(() -> createIncrementalDataState(consumer -> {
            if (mySecurityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)) {
                return myRepositoryDataService.loadBatchedCollaborators(batch -> consumer.accept(batch.stream()
                    .map(user -> new GHUser(user.getNodeId(), user.getLogin(), user.getHtmlUrl(),
                        Objects.requireNonNullElse(user.getAvatarUrl(), ""), null))
                    .toList()));
            }
            else { // users without push access cannot query the collaborators API, so we fall back to fetching contributors
                return myRepositoryDataService.loadBatchedContributors(consumer);
            }
        }));
        myAssignees = LazyValue.atomicNotNull(() -> createIncrementalDataState(myRepositoryDataService::loadBatchedPotentialIssuesAssignees));
        myLabels = LazyValue.atomicNotNull(() -> createIncrementalDataState(myRepositoryDataService::loadBatchedLabels));
    }

    @Override
    protected GHPRListSearchValue withQuery(GHPRListSearchValue search, @Nullable String query) {
        return search.withSearchQuery(query);
    }

    @Override
    public List<GHPRListQuickFilter> getQuickFilters() {
        return myQuickFilters;
    }

    public MutableObservableValue<GHPRListSearchValue.@Nullable State> getStateFilterState() {
        return myStateFilterState;
    }

    public MutableObservableValue<@Nullable String> getAuthorFilterState() {
        return myAuthorFilterState;
    }

    public MutableObservableValue<@Nullable String> getLabelFilterState() {
        return myLabelFilterState;
    }

    public MutableObservableValue<@Nullable String> getAssigneeFilterState() {
        return myAssigneeFilterState;
    }

    public MutableObservableValue<GHPRListSearchValue.@Nullable ReviewState> getReviewFilterState() {
        return myReviewFilterState;
    }

    public MutableObservableValue<GHPRListSearchValue.@Nullable Sort> getSortFilterState() {
        return mySortFilterState;
    }

    public ObservableValue<IncrementallyComputedValue<List<GHUser>>> getAuthors() {
        return myAuthors.get();
    }

    public ObservableValue<IncrementallyComputedValue<List<GHUser>>> getAssignees() {
        return myAssignees.get();
    }

    public ObservableValue<IncrementallyComputedValue<List<GHLabel>>> getLabels() {
        return myLabels.get();
    }

    private <T> ObservableValue<IncrementallyComputedValue<List<T>>> createIncrementalDataState(Function<Consumer<List<T>>, CompletableFuture<?>> loader) {
        MutableObservableValue<IncrementallyComputedValue<List<T>>> state = MutableObservableValue.of(IncrementallyComputedValue.loading());
        CompletableFuture<?>[] current = new CompletableFuture<?>[1];
        Runnable restart = () -> {
            synchronized (current) {
                if (current[0] != null) {
                    current[0].cancel(false);
                }
                current[0] = IncrementallyComputedValue.collectIncrementallyTo(loader, state::set);
            }
        };
        myRepositoryDataService.addDataReloadListener(myScope, restart);
        Disposer.register(myScope, () -> {
            synchronized (current) {
                if (current[0] != null) {
                    current[0].cancel(false);
                }
            }
        });
        restart.run();
        return state;
    }
}
