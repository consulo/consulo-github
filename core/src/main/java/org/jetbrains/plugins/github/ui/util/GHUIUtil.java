// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.ui.util;

import consulo.platform.base.icon.PlatformIconGroup;
import consulo.localize.LocalizeValue;
import consulo.review.icon.ReviewIconGroup;
import consulo.review.icon.IconsProvider;
import consulo.review.util.popup.PopupItemPresentation;
import consulo.ui.color.RGBColor;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import consulo.review.localize.ReviewLocalize;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState;

import java.util.Collection;
import java.util.function.Function;

public final class GHUIUtil {
    private GHUIUtil() {
    }

    public static final class SelectionPresenters {
        private static final int AVATAR_SIZE = 20;

        private SelectionPresenters() {
        }

        public static Function<GHPullRequestRequestedReviewer, PopupItemPresentation> PRReviewers(IconsProvider<String> avatarIconsProvider) {
            return it -> new PopupItemPresentation.Simple(it.getShortName(), avatarIconsProvider.getIcon(it.getAvatarUrl(), AVATAR_SIZE), it.getName());
        }

        public static Function<GHUser, PopupItemPresentation> Users(IconsProvider<String> avatarIconsProvider) {
            return it -> new PopupItemPresentation.Simple(it.getLogin(), avatarIconsProvider.getIcon(it.getAvatarUrl(), AVATAR_SIZE), it.getName());
        }

        public static Function<GHLabel, PopupItemPresentation> Labels() {
            return it -> new PopupItemPresentation.Simple(it.getName(), ImageEffects.colorFilled(16, 16, RGBColor.decode("#" + it.getColor())), null);
        }
    }

    public static Image getPullRequestStateIcon(GHPullRequestState state, boolean isDraft) {
        if (isDraft) {
            return ImageEffects.grayed(ReviewIconGroup.pullrequestopen());
        }
        return switch (state) {
            case CLOSED -> ReviewIconGroup.pullrequestclosed();
            case MERGED -> PlatformIconGroup.vcsMerge();
            case OPEN -> ReviewIconGroup.pullrequestopen();
        };
    }

    public static LocalizeValue getPullRequestStateText(GHPullRequestState state, boolean isDraft) {
        if (isDraft) {
            return ReviewLocalize.reviewDetailsReviewStateDraft();
        }
        return switch (state) {
            case CLOSED -> ReviewLocalize.reviewDetailsReviewStateClosed();
            case MERGED -> ReviewLocalize.reviewDetailsReviewStateMerged();
            case OPEN -> ReviewLocalize.reviewDetailsReviewStateOpen();
        };
    }

    public static String getRepositoryDisplayName(Collection<GHRepositoryCoordinates> allRepositories, GHRepositoryCoordinates repository) {
        return getRepositoryDisplayName(allRepositories, repository, false);
    }

    public static String getRepositoryDisplayName(Collection<GHRepositoryCoordinates> allRepositories,
                                                  GHRepositoryCoordinates repository,
                                                  boolean alwaysShowOwner) {
        boolean showServer = needToShowRepositoryServer(allRepositories);
        boolean showOwner = showServer || alwaysShowOwner || needToShowRepositoryOwner(allRepositories);

        StringBuilder builder = new StringBuilder();
        if (showServer) {
            builder.append(repository.getServerPath().toUrl(false)).append("/");
        }
        if (showOwner) {
            builder.append(repository.getRepositoryPath().getOwner()).append("/");
        }
        builder.append(repository.getRepositoryPath().getRepository());
        return builder.toString();
    }

    /**
     * Assuming all servers are the same
     */
    private static boolean needToShowRepositoryOwner(Collection<GHRepositoryCoordinates> repos) {
        if (repos.size() <= 1) {
            return false;
        }
        String firstOwner = repos.iterator().next().getRepositoryPath().getOwner();
        return repos.stream().anyMatch(it -> !it.getRepositoryPath().getOwner().equals(firstOwner));
    }

    private static boolean needToShowRepositoryServer(Collection<GHRepositoryCoordinates> repos) {
        if (repos.size() <= 1) {
            return false;
        }
        GithubServerPath firstServer = repos.iterator().next().getServerPath();
        return repos.stream().anyMatch(it -> !it.getServerPath().equals(firstServer));
    }
}
