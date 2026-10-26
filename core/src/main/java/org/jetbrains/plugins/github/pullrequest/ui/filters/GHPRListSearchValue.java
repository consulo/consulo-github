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

import consulo.review.list.search.ReviewListSearchValue;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery;
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.QualifierName;
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.Term;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record GHPRListSearchValue(@Nullable String searchQuery,
                                  @Nullable State state,
                                  @Nullable String assignee,
                                  @Nullable ReviewState reviewState,
                                  @Nullable String author,
                                  @Nullable String label,
                                  @Nullable Sort sort) implements ReviewListSearchValue {
    public static final GHPRListSearchValue DEFAULT = new GHPRListSearchValue(null, State.OPEN, null, null, null, null, null);
    public static final GHPRListSearchValue EMPTY = new GHPRListSearchValue(null, null, null, null, null, null, null);

    @Override
    public @Nullable String getSearchQuery() {
        return searchQuery;
    }

    @Override
    public int getFilterCount() {
        int count = 0;
        if (searchQuery != null) {
            count++;
        }
        if (state != null) {
            count++;
        }
        if (assignee != null) {
            count++;
        }
        if (reviewState != null) {
            count++;
        }
        if (author != null) {
            count++;
        }
        if (label != null) {
            count++;
        }
        if (sort != null) {
            count++;
        }
        return count;
    }

    public GHPRListSearchValue withSearchQuery(@Nullable String value) {
        return new GHPRListSearchValue(value, state, assignee, reviewState, author, label, sort);
    }

    public GHPRListSearchValue withState(@Nullable State value) {
        return new GHPRListSearchValue(searchQuery, value, assignee, reviewState, author, label, sort);
    }

    public GHPRListSearchValue withAssignee(@Nullable String value) {
        return new GHPRListSearchValue(searchQuery, state, value, reviewState, author, label, sort);
    }

    public GHPRListSearchValue withReviewState(@Nullable ReviewState value) {
        return new GHPRListSearchValue(searchQuery, state, assignee, value, author, label, sort);
    }

    public GHPRListSearchValue withAuthor(@Nullable String value) {
        return new GHPRListSearchValue(searchQuery, state, assignee, reviewState, value, label, sort);
    }

    public GHPRListSearchValue withLabel(@Nullable String value) {
        return new GHPRListSearchValue(searchQuery, state, assignee, reviewState, author, value, sort);
    }

    public GHPRListSearchValue withSort(@Nullable Sort value) {
        return new GHPRListSearchValue(searchQuery, state, assignee, reviewState, author, label, value);
    }

    public @Nullable GHPRSearchQuery toQuery() {
        List<Term<?>> terms = new ArrayList<>();

        if (searchQuery != null) {
            terms.add(new Term.QueryPart(searchQuery));
        }

        if (state != null) {
            terms.add(switch (state) {
                case OPEN -> QualifierName.is.createTerm("open");
                case CLOSED -> QualifierName.is.createTerm("closed");
                case MERGED -> QualifierName.is.createTerm("merged");
            });
        }

        if (assignee != null) {
            terms.add(QualifierName.assignee.createTerm(assignee));
        }

        if (reviewState != null) {
            terms.add(switch (reviewState) {
                case NO_REVIEW -> QualifierName.review.createTerm("none");
                case REQUIRED -> QualifierName.review.createTerm("required");
                case APPROVED -> QualifierName.review.createTerm("approved");
                case CHANGES_REQUESTED -> QualifierName.review.createTerm("changes-requested");
                case REVIEWED_BY_ME -> QualifierName.reviewedBy.createTerm("@me");
                case NOT_REVIEWED_BY_ME -> ((Term.Qualifier.Simple) QualifierName.reviewedBy.createTerm("@me")).not();
                case AWAITING_REVIEW -> QualifierName.reviewRequested.createTerm("@me");
            });
        }

        if (author != null) {
            terms.add(QualifierName.author.createTerm(author));
        }

        if (label != null) {
            terms.add(QualifierName.label.createTerm(StringUtil.wrapWithDoubleQuote(label)));
        }

        if (sort != null) {
            terms.add(switch (sort) {
                case NEWEST -> QualifierName.sortBy.createTerm("created-desc");
                case OLDEST -> QualifierName.sortBy.createTerm("created-asc");
                case MOST_COMMENTED -> QualifierName.sortBy.createTerm("comments-desc");
                case LEAST_COMMENTED -> QualifierName.sortBy.createTerm("comments-asc");
                case RECENTLY_UPDATED -> QualifierName.sortBy.createTerm("updated-desc");
                case LEAST_RECENTLY_UPDATED -> QualifierName.sortBy.createTerm("updated-asc");
            });
        }

        if (terms.isEmpty()) {
            return null;
        }
        return new GHPRSearchQuery(terms);
    }

    public enum State {
        OPEN,
        CLOSED,
        MERGED
    }

    public enum ReviewState {
        NO_REVIEW,
        REQUIRED,
        APPROVED,
        CHANGES_REQUESTED,
        REVIEWED_BY_ME,
        NOT_REVIEWED_BY_ME,
        AWAITING_REVIEW
    }

    public enum Sort {
        NEWEST,
        OLDEST,
        MOST_COMMENTED,
        LEAST_COMMENTED,
        RECENTLY_UPDATED,
        LEAST_RECENTLY_UPDATED,
    }
}
