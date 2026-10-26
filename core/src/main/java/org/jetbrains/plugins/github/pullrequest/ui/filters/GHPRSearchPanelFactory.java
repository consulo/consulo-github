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
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.review.icon.IconsProvider;
import consulo.review.list.search.ChooserPopup;
import consulo.review.list.search.ChooserPopupUtil;
import consulo.review.list.search.DropDownComponentFactory;
import consulo.review.list.search.PopupConfig;
import consulo.review.list.search.ReviewListSearchPanelFactory;
import consulo.review.util.popup.PopupItemPresentation;
import consulo.ui.ex.action.AnAction;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHUser;

import java.util.List;

public class GHPRSearchPanelFactory extends ReviewListSearchPanelFactory<GHPRListSearchValue, GHPRListQuickFilter, GHPRSearchPanelViewModel> {
    private static final int AVATAR_SIZE = 20;

    private final IconsProvider<String> myAvatarIconsProvider;

    public GHPRSearchPanelFactory(GHPRSearchPanelViewModel vm, IconsProvider<String> avatarIconsProvider) {
        super(vm);
        myAvatarIconsProvider = avatarIconsProvider;
    }

    @Override
    protected LocalizeValue getShortText(GHPRListSearchValue searchValue) {
        StringBuilder builder = new StringBuilder();
        if (searchValue.searchQuery() != null) {
            builder.append('"').append(searchValue.searchQuery()).append('"').append(" ");
        }
        if (searchValue.state() != null) {
            builder.append("state:\"").append(getShortText(searchValue.state()).get()).append('"').append(" ");
        }
        if (searchValue.label() != null) {
            builder.append("label:").append(searchValue.label()).append(" ");
        }
        if (searchValue.assignee() != null) {
            builder.append("assignee:").append(searchValue.assignee()).append(" ");
        }
        if (searchValue.reviewState() != null) {
            builder.append("reviewState:\"").append(getShortText(searchValue.reviewState()).get()).append('"').append(" ");
        }
        if (searchValue.author() != null) {
            builder.append("author:").append(searchValue.author()).append(" ");
        }
        if (searchValue.sort() != null) {
            builder.append("sort:\"").append(getShortText(searchValue.sort()).get()).append('"').append(" ");
        }
        return LocalizeValue.of(builder.toString());
    }

    @Override
    protected List<AnAction> createFilters(Disposable viewDisposable) {
        return List.of(
            new DropDownComponentFactory<>(myVm.getStateFilterState())
                .create(GithubLocalize.pullRequestListFilterState(),
                    List.of(GHPRListSearchValue.State.values()),
                    () -> {
                    },
                    it -> getShortText(it).get()),
            new DropDownComponentFactory<>(myVm.getAuthorFilterState())
                .create(GithubLocalize.pullRequestListFilterAuthor(), () -> mapUser(
                    ChooserPopupUtil.createChooserPopupWithIncrementalLoading(myVm.getAuthors(), this::presentUser, PopupConfig.DEFAULT))),
            new DropDownComponentFactory<>(myVm.getLabelFilterState())
                .create(GithubLocalize.pullRequestListFilterLabel(), () -> {
                    ChooserPopup<GHLabel> chooser =
                        ChooserPopupUtil.createChooserPopupWithIncrementalLoading(myVm.getLabels(), GHPRSearchPanelFactory::presentLabel, PopupConfig.DEFAULT);
                    return new ChooserPopup<>(chooser.popup(), chooser.result().thenApply(it -> it == null ? null : it.getName()));
                }),
            new DropDownComponentFactory<>(myVm.getAssigneeFilterState())
                .create(GithubLocalize.pullRequestListFilterAssignee(), () -> mapUser(
                    ChooserPopupUtil.createChooserPopupWithIncrementalLoading(myVm.getAssignees(), this::presentUser, PopupConfig.DEFAULT))),
            new DropDownComponentFactory<>(myVm.getReviewFilterState())
                .create(GithubLocalize.pullRequestListFilterReview(),
                    List.of(GHPRListSearchValue.ReviewState.values()),
                    () -> {
                    },
                    it -> getShortText(it).get(),
                    it -> new PopupItemPresentation.Simple(getFullText(it).get())),
            new DropDownComponentFactory<>(myVm.getSortFilterState())
                .create(GithubLocalize.pullRequestListFilterSort(),
                    List.of(GHPRListSearchValue.Sort.values()),
                    () -> {
                    },
                    it -> getShortText(it).get(),
                    it -> new PopupItemPresentation.Simple(getShortText(it).get()))
        );
    }

    private static ChooserPopup<String> mapUser(ChooserPopup<GHUser> chooser) {
        return new ChooserPopup<>(chooser.popup(), chooser.result().thenApply(it -> it == null ? null : it.getLogin()));
    }

    private PopupItemPresentation presentUser(GHUser user) {
        return new PopupItemPresentation.Simple(user.getShortName(), myAvatarIconsProvider.getIcon(user.getAvatarUrl(), AVATAR_SIZE), user.getName());
    }

    private static PopupItemPresentation presentLabel(GHLabel label) {
        return new PopupItemPresentation.Simple(label.getName());
    }

    @Override
    protected LocalizeValue getQuickFilterTitle(GHPRListQuickFilter quickFilter) {
        return switch (quickFilter) {
            case GHPRListQuickFilter.Open open -> GithubLocalize.pullRequestListFilterQuickOpen();
            case GHPRListQuickFilter.YourPullRequests yours -> GithubLocalize.pullRequestListFilterQuickYours();
            case GHPRListQuickFilter.AssignedToYou assigned -> GithubLocalize.pullRequestListFilterQuickAssigned();
            case GHPRListQuickFilter.ReviewRequests requests -> GithubLocalize.pullRequestListFilterQuickReviewRequests();
        };
    }

    private static LocalizeValue getShortText(GHPRListSearchValue.State stateFilterValue) {
        return switch (stateFilterValue) {
            case OPEN -> GithubLocalize.pullRequestListFilterStateOpen();
            case CLOSED -> GithubLocalize.pullRequestListFilterStateClosed();
            case MERGED -> GithubLocalize.pullRequestListFilterStateMerged();
        };
    }

    private static LocalizeValue getShortText(GHPRListSearchValue.ReviewState reviewStateFilterValue) {
        return switch (reviewStateFilterValue) {
            case NO_REVIEW -> GithubLocalize.pullRequestListFilterReviewNoShort();
            case REQUIRED -> GithubLocalize.pullRequestListFilterReviewRequiredShort();
            case APPROVED -> GithubLocalize.pullRequestListFilterReviewApprovedShort();
            case CHANGES_REQUESTED -> GithubLocalize.pullRequestListFilterReviewChangeRequestedShort();
            case REVIEWED_BY_ME -> GithubLocalize.pullRequestListFilterReviewReviewedShort();
            case NOT_REVIEWED_BY_ME -> GithubLocalize.pullRequestListFilterReviewNotShort();
            case AWAITING_REVIEW -> GithubLocalize.pullRequestListFilterReviewAwaitingShort();
        };
    }

    private static LocalizeValue getShortText(GHPRListSearchValue.Sort sortFilterValue) {
        return switch (sortFilterValue) {
            case NEWEST -> GithubLocalize.pullRequestListFilterSortNewest();
            case OLDEST -> GithubLocalize.pullRequestListFilterSortOldest();
            case MOST_COMMENTED -> GithubLocalize.pullRequestListFilterSortMostCommented();
            case LEAST_COMMENTED -> GithubLocalize.pullRequestListFilterSortLeastCommented();
            case RECENTLY_UPDATED -> GithubLocalize.pullRequestListFilterSortRecentlyUpdated();
            case LEAST_RECENTLY_UPDATED -> GithubLocalize.pullRequestListFilterSortLeastRecentlyUpdated();
        };
    }

    private static LocalizeValue getFullText(GHPRListSearchValue.ReviewState reviewStateFilterValue) {
        return switch (reviewStateFilterValue) {
            case NO_REVIEW -> GithubLocalize.pullRequestListFilterReviewNoFull();
            case REQUIRED -> GithubLocalize.pullRequestListFilterReviewRequiredFull();
            case APPROVED -> GithubLocalize.pullRequestListFilterReviewApprovedFull();
            case CHANGES_REQUESTED -> GithubLocalize.pullRequestListFilterReviewChangeRequestedFull();
            case REVIEWED_BY_ME -> GithubLocalize.pullRequestListFilterReviewReviewedFull();
            case NOT_REVIEWED_BY_ME -> GithubLocalize.pullRequestListFilterReviewNotFull();
            case AWAITING_REVIEW -> GithubLocalize.pullRequestListFilterReviewAwaitingFull();
        };
    }
}
