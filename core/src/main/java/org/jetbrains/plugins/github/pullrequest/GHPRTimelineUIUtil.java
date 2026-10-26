// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest;

import consulo.project.Project;
import consulo.review.icon.IconsProvider;
import consulo.review.list.UserPresentation;
import consulo.review.timeline.thread.DefaultRepliesStateData;
import consulo.review.timeline.thread.RepliesStateData;
import consulo.review.user.CodeReviewUser;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GHPRTimelineUIUtil {
    public static final int AVATAR_SIZE = 32;

    private GHPRTimelineUIUtil() {
    }

    public static String getName(GHPRIdentifier pullRequest) {
        return "#" + pullRequest.number();
    }

    public static String getPresentableName(Project project, GHRepositoryCoordinates repository, GHPRIdentifier pullRequest) {
        GHPullRequestShort details = findDetails(project, repository, pullRequest);
        return details != null ? details.getTitle() + " " + getName(pullRequest) : getName(pullRequest);
    }

    public static String getPresentablePath(Project project, GHRepositoryCoordinates repository, GHPRIdentifier pullRequest) {
        GHPullRequestShort details = findDetails(project, repository, pullRequest);
        return details != null ? details.getUrl() : repository.toUrl() + "/pulls/" + pullRequest.number();
    }

    public static GHActorPresentation createUserPresentation(GHActor actor, IconsProvider<String> avatarIconsProvider) {
        return new GHActorPresentation(actor, avatarIconsProvider);
    }

    public static RepliesStateData createRepliesState(List<GHPullRequestReviewComment> comments,
                                                      IconsProvider<String> avatarIconsProvider) {
        if (comments.size() < 2) {
            return RepliesStateData.EMPTY;
        }
        List<GHPullRequestReviewComment> replies = comments.subList(1, comments.size());
        Set<CodeReviewUser> authors = new LinkedHashSet<>();
        for (GHPullRequestReviewComment reply : replies) {
            GHActor author = reply.getAuthor();
            if (author != null) {
                authors.add(createUserPresentation(author, avatarIconsProvider));
            }
        }
        return new DefaultRepliesStateData(authors, replies.size(), replies.get(replies.size() - 1).getCreatedAt());
    }

    private static @Nullable GHPRConnectedProjectViewModel findProjectVm(Project project, GHRepositoryCoordinates repository) {
        GHPRConnectedProjectViewModel vm = GHPRProjectViewModel.getInstance(project).getConnectedProjectVm().get();
        return vm != null && vm.getRepository().equals(repository) ? vm : null;
    }

    private static @Nullable GHPullRequestShort findDetails(Project project,
                                                            GHRepositoryCoordinates repository,
                                                            GHPRIdentifier pullRequest) {
        GHPRConnectedProjectViewModel vm = findProjectVm(project, repository);
        return vm != null ? vm.findDetails(pullRequest) : null;
    }

    public static final class GHActorPresentation implements UserPresentation, CodeReviewUser {
        private final GHActor myActor;
        private final IconsProvider<String> myAvatarIconsProvider;

        private GHActorPresentation(GHActor actor, IconsProvider<String> avatarIconsProvider) {
            myActor = actor;
            myAvatarIconsProvider = avatarIconsProvider;
        }

        public GHActor getActor() {
            return myActor;
        }

        @Override
        public String getUsername() {
            return myActor.getLogin();
        }

        @Override
        public @Nullable String getFullName() {
            String presentableName = myActor.getPresentableName();
            return presentableName.equals(myActor.getLogin()) ? null : presentableName;
        }

        @Override
        public String getPresentableName() {
            return myActor.getPresentableName();
        }

        @RequiredUIAccess
        @Override
        public Image getAvatarIcon() {
            return myAvatarIconsProvider.getIcon(myActor.getAvatarUrl(), AVATAR_SIZE);
        }

        @Override
        public @Nullable String getAvatarUrl() {
            return myActor.getAvatarUrl();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof GHActorPresentation that && myActor.equals(that.myActor);
        }

        @Override
        public int hashCode() {
            return myActor.hashCode();
        }
    }
}
