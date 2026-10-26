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
package org.jetbrains.plugins.github.pullrequest.ui;

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GHReviewersUtils {
    private GHReviewersUtils() {
    }

    /**
     * Computes the state of every reviewer of a pull request, matching how GitHub presents them.
     * <p>
     * The result merges two sources the way GitHub does:
     * - each reviewer's effective review state ({@code reviews}), resolved by {@link #calcReviewerState} — surfaced as approved /
     * changes-requested / commented;
     * - the pending review requests ({@code reviewers}) — a request always supersedes a prior review, so a reviewer who
     * already reviewed and is requested again becomes {@link GHPRReviewerState#RE_REQUESTED}, while a reviewer who never
     * reviewed becomes {@link GHPRReviewerState#NEEDS_REVIEW}.
     * <p>
     * The PR {@code author}'s own reviews are excluded.
     */
    public static Map<GHPullRequestRequestedReviewer, GHPRReviewerState> getReviewsByReviewers(@Nullable GHActor author,
                                                                                              List<GHPullRequestReview> reviews,
                                                                                              List<GHPullRequestRequestedReviewer> reviewers,
                                                                                              GHUser ghostUser) {
        Map<GHPullRequestRequestedReviewer, GHPRReviewerState> result = new LinkedHashMap<>();

        Map<GHPullRequestRequestedReviewer, List<GHPullRequestReview>> byReviewer = new LinkedHashMap<>();
        for (GHPullRequestReview review : reviews) {
            GHPullRequestRequestedReviewer reviewer = review.getAuthor() instanceof GHPullRequestRequestedReviewer r ? r : ghostUser;
            byReviewer.computeIfAbsent(reviewer, it -> new ArrayList<>()).add(review);
        }
        byReviewer.forEach((reviewer, reviewerReviews) -> {
            if (Objects.equals(reviewer, author)) {
                return;
            }
            GHPRReviewerState state = calcReviewerState(reviewerReviews);
            if (state == null) {
                return;
            }
            result.put(reviewer, state);
        });

        for (GHPullRequestRequestedReviewer requestedReviewer : reviewers) {
            result.put(requestedReviewer, result.containsKey(requestedReviewer) ? GHPRReviewerState.RE_REQUESTED : GHPRReviewerState.NEEDS_REVIEW);
        }

        return result;
    }

    /**
     * Resolves a reviewer's effective state from all of their reviews, the way GitHub web does.
     * <p>
     * GitHub only ever surfaces four reviewer states — review requested / approved / changes-requested / commented —
     * so the most recent <i>opinionated</i> review (approved or changes-requested) wins, and any plain comments submitted
     * afterwards do not override it. A DISMISSED review is terminal, though: once the
     * <i>latest</i> review is dismissed, it is surfaced as a plain comment and does not let an older opinionated review
     * resurface (you can't dismiss a comment, so a plain COMMENTED review never hides one).
     * <p>
     * Returns {@link GHPRReviewerState#COMMENTED} when the reviewer only ever left (or had dismissed) comments, and {@code null}
     * when they have no submitted review at all (e.g. only an in-progress PENDING draft),
     * so such a reviewer is not surfaced.
     */
    private static @Nullable GHPRReviewerState calcReviewerState(List<GHPullRequestReview> reviews) {
        boolean commented = false;
        List<GHPullRequestReview> sorted = new ArrayList<>(reviews);
        sorted.sort(Comparator.comparing(GHPullRequestReview::getCreatedAt).reversed());
        for (GHPullRequestReview review : sorted) {
            switch (review.getState()) {
                case APPROVED:
                    return GHPRReviewerState.APPROVED;
                case CHANGES_REQUESTED:
                    return GHPRReviewerState.CHANGES_REQUESTED;
                case DISMISSED:
                    return GHPRReviewerState.COMMENTED;
                case COMMENTED:
                    commented = true;
                    break;
                case PENDING:
                    // review not submitted yet, keep looking
                    break;
            }
        }
        return commented ? GHPRReviewerState.COMMENTED : null;
    }
}
