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

import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.review.details.ReviewDetailsUIUtil;
import consulo.review.details.data.ReviewState;
import consulo.review.icon.ReviewIconGroup;
import consulo.review.localize.ReviewLocalize;
import consulo.ui.AvatarStyle;
import consulo.ui.image.Image;

/**
 * Maps a {@link GHPRReviewerState} to the icon, avatar-border style and label used to present a reviewer,
 * so the plugin matches how GitHub shows each reviewer state.
 * <p>
 * The status icon carries the "is the PR waiting on this reviewer?" signal:
 * awaiting-review states use the warning icon, while {@link GHPRReviewerState#COMMENTED}
 * uses a neutral icon so the two situations are visually distinct.
 */
public final class GHPRReviewerStateUIUtil {
    private GHPRReviewerStateUIUtil() {
    }

    public static Image getStatusIcon(GHPRReviewerState state) {
        return switch (state) {
            case APPROVED -> ReviewDetailsUIUtil.getReviewStateIcon(ReviewState.ACCEPTED);
            case CHANGES_REQUESTED -> ReviewDetailsUIUtil.getReviewStateIcon(ReviewState.WAIT_FOR_UPDATES);
            case NEEDS_REVIEW, RE_REQUESTED -> ReviewDetailsUIUtil.getReviewStateIcon(ReviewState.NEED_REVIEW);
            case COMMENTED -> ReviewIconGroup.comment();
        };
    }

    public static AvatarStyle getBorderStyle(GHPRReviewerState state) {
        return switch (state) {
            case APPROVED -> ReviewDetailsUIUtil.getReviewStateAvatarStyle(ReviewState.ACCEPTED);
            case CHANGES_REQUESTED -> ReviewDetailsUIUtil.getReviewStateAvatarStyle(ReviewState.WAIT_FOR_UPDATES);
            case COMMENTED, NEEDS_REVIEW, RE_REQUESTED -> ReviewDetailsUIUtil.getReviewStateAvatarStyle(ReviewState.NEED_REVIEW);
        };
    }

    public static LocalizeValue getText(GHPRReviewerState state, String reviewer) {
        return switch (state) {
            case APPROVED -> ReviewLocalize.reviewDetailsStatusReviewerApproved(reviewer);
            case CHANGES_REQUESTED -> ReviewLocalize.reviewDetailsStatusReviewerWaitForUpdates(reviewer);
            case NEEDS_REVIEW -> ReviewLocalize.reviewDetailsStatusReviewerNeedReview(reviewer);
            case RE_REQUESTED -> GithubLocalize.pullRequestReviewerStateReRequested(reviewer);
            case COMMENTED -> GithubLocalize.pullRequestReviewerStateCommented(reviewer);
        };
    }
}
