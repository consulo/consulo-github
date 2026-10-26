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

import consulo.review.details.data.ReviewState;

/**
 * Detailed state of a single reviewer of a pull request, mirroring how GitHub itself presents reviewers.
 * <p>
 * Unlike the platform-wide {@link ReviewState} (which only distinguishes accepted / waiting-for-updates / need-review),
 * this captures every state a reviewer can be shown in on the GitHub PR page, so the plugin stays consistent with it:
 * - {@link #APPROVED}, {@link #CHANGES_REQUESTED} — the reviewer submitted an opinionated review;
 * - {@link #COMMENTED} — the reviewer submitted a review with comments only (no approval/rejection), or their opinionated
 * review was later dismissed (GitHub then shows it as a plain comment — you can't dismiss a comment);
 * - {@link #NEEDS_REVIEW} — a review was requested and the reviewer has not reviewed yet;
 * - {@link #RE_REQUESTED} — the reviewer already reviewed, but a fresh review was requested again from them.
 * <p>
 * The distinction between {@link #NEEDS_REVIEW}/{@link #RE_REQUESTED} (the PR is waiting on this reviewer) and
 * {@link #COMMENTED} (no action is pending from this reviewer) is surfaced in the UI.
 */
public enum GHPRReviewerState {
    APPROVED,
    CHANGES_REQUESTED,
    COMMENTED,
    NEEDS_REVIEW,
    RE_REQUESTED;

    /**
     * {@code true} when the pull request is still awaiting a review from this reviewer.
     */
    public boolean isAwaitingReview() {
        return this == NEEDS_REVIEW || this == RE_REQUESTED;
    }

    /**
     * Collapses the detailed reviewer state into the platform-wide {@link ReviewState} used by cross-provider components.
     * <p>
     * States that are neither an approval nor an explicit request for updates ({@link #COMMENTED} and the
     * awaiting-review states) map to {@link ReviewState#NEED_REVIEW}, since the PR does not (yet) have this reviewer's approval.
     */
    public ReviewState toReviewState() {
        return switch (this) {
            case APPROVED -> ReviewState.ACCEPTED;
            case CHANGES_REQUESTED -> ReviewState.WAIT_FOR_UPDATES;
            case COMMENTED, NEEDS_REVIEW, RE_REQUESTED -> ReviewState.NEED_REVIEW;
        };
    }
}
