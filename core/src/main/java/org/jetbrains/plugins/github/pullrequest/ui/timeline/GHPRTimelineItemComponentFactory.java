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
package org.jetbrains.plugins.github.pullrequest.ui.timeline;

import consulo.project.Project;
import consulo.review.details.CommitPresentation;
import consulo.review.icon.IconsProvider;
import consulo.review.list.UserPresentation;
import consulo.review.timeline.CodeReviewCommitsTimelineItem;
import consulo.review.timeline.CodeReviewTimelineCommit;
import consulo.review.timeline.CodeReviewTimelineItem;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHCommitShort;
import org.jetbrains.plugins.github.api.data.GHGitActor;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort;
import consulo.github.localize.GithubLocalize;
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineUIUtil;
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsFull;
import org.jetbrains.plugins.github.pullrequest.ui.timeline.item.GHPRTimelineItem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class GHPRTimelineItemComponentFactory {
    private final Project myProject;
    private final GHPRTimelineViewModel myTimelineVm;

    private final IconsProvider<String> myAvatarIconsProvider;
    private final GHUser myGhostUser;

    private final GHPRTimelineEventComponentFactoryImpl myEventComponentFactory;

    public GHPRTimelineItemComponentFactory(Project project, GHPRTimelineViewModel timelineVm) {
        myProject = project;
        myTimelineVm = timelineVm;
        myAvatarIconsProvider = timelineVm.getAvatarIconsProvider();
        myGhostUser = timelineVm.getGhostUser();
        myEventComponentFactory = new GHPRTimelineEventComponentFactoryImpl(timelineVm);
    }

    private @Nullable GHActor getPrAuthor() {
        GHPRDetailsFull details = myTimelineVm.getDetailsVm().getDetails().get().getOrNull();
        return details == null ? null : details.author();
    }

    public CodeReviewTimelineItem invoke(GHPRTimelineItem item) {
        if (item instanceof GHPRTimelineItem.Review review) {
            return (CodeReviewTimelineItem) review;
        }
        else if (item instanceof GHPRTimelineItem.Comment comment) {
            return comment;
        }
        else if (item instanceof GHPRTimelineItem.Commits commits) {
            return createComponent(commits.commits());
        }
        else if (item instanceof GHPRTimelineItem.Event event) {
            try {
                return myEventComponentFactory.createComponent(event.event());
            }
            catch (Exception e) {
                String body = GithubLocalize.cannotDisplayItem(String.valueOf(e.getMessage())).get();
                return myEventComponentFactory.createTimelineItem(prAuthorOrGhost(), null, body);
            }
        }
        else if (item instanceof GHPRTimelineItem.Unknown unknown) {
            String body = GithubLocalize.cannotDisplayItem(unknown.typename()).get();
            return myEventComponentFactory.createTimelineItem(prAuthorOrGhost(), null, body);
        }
        throw new IllegalStateException("Unknown item " + item);
    }

    private GHActor prAuthorOrGhost() {
        GHActor prAuthor = getPrAuthor();
        return prAuthor != null ? prAuthor : myGhostUser;
    }

    private CodeReviewTimelineItem createComponent(List<GHPullRequestCommitShort> commits) {
        GHGitActor singleAuthor = commits.size() == 1 ? commits.get(0).getCommit().getAuthor() : null;
        GHUser singleUser = singleAuthor != null ? singleAuthor.getUser() : null;
        GHActor actor = singleUser != null ? singleUser : prAuthorOrGhost();
        Date date = singleAuthor != null ? singleAuthor.getDate() : null;
        return new CommitsItem(GHPRTimelineUIUtil.createUserPresentation(actor, myAvatarIconsProvider), date, commits);
    }

    private CommitPresentation createCommitPresentation(GHPullRequestCommitShort pullRequestCommit) {
        GHCommitShort commit = pullRequestCommit.getCommit();
        String htmlMessage = GHMarkdownToHtmlConverter.convertToHtml(commit.getMessageHeadline(), myProject);
        GHGitActor author = commit.getAuthor();
        GHUser user = author != null ? author.getUser() : null;
        GHActor actor = user != null ? user : myGhostUser;
        Date date = author != null ? author.getDate() : null;
        if (date == null) {
            date = pullRequestCommit.getCreatedAt();
        }
        return new CommitPresentation(htmlMessage, "", actor.getPresentableName(), date);
    }

    private final class CommitsItem implements CodeReviewCommitsTimelineItem {
        private final UserPresentation myAuthor;
        private final @Nullable Date myCreatedAt;
        private final List<GHPullRequestCommitShort> myCommits;

        private CommitsItem(UserPresentation author, @Nullable Date createdAt, List<GHPullRequestCommitShort> commits) {
            myAuthor = author;
            myCreatedAt = createdAt;
            myCommits = commits;
        }

        @Override
        public UserPresentation getAuthor() {
            return myAuthor;
        }

        @Override
        public @Nullable Date getCreatedAt() {
            return myCreatedAt;
        }

        @Override
        public List<CodeReviewTimelineCommit> getCommits() {
            List<CodeReviewTimelineCommit> result = new ArrayList<>(myCommits.size());
            for (GHPullRequestCommitShort commit : myCommits) {
                result.add(new CodeReviewTimelineCommit(commit.getCommit().getOid(),
                    commit.getCommit().getAbbreviatedOid(),
                    createCommitPresentation(commit)));
            }
            return result;
        }

        @RequiredUIAccess
        @Override
        public void showCommit(String hash) {
            myTimelineVm.showCommit(hash);
        }
    }
}
