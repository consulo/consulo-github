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

import consulo.localize.LocalizeValue;
import consulo.review.icon.IconsProvider;
import consulo.review.list.UserPresentation;
import consulo.review.timeline.CodeReviewCommentViewModel;
import consulo.review.timeline.CodeReviewTimelineItem;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.review.issue.IssueIdsProcessor;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHCommitShort;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHGitRefName;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRAssignedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRBaseRefChangedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRBaseRefForcePushedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRConnectedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRConvertToDraftEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRCrossReferencedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRDisconnectedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRHeadRefDeletedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRHeadRefForcePushedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRHeadRefRestoredEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRLabeledEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRMergedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRReadyForReviewEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRReferencedSubject;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRRenamedTitleEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRReviewDismissedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRReviewRequestedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRReviewUnrequestedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRUnassignedEvent;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRUnlabeledEvent;
import consulo.github.localize.GithubLocalize;
import org.jetbrains.plugins.github.pullrequest.GHPRTimelineUIUtil;
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class GHPRTimelineEventComponentFactoryImpl implements GHPRTimelineEventComponentFactory<GHPRTimelineEvent> {
    private final IconsProvider<String> myAvatarIconsProvider;
    private final GHUser myGhostUser;

    private final SimpleEventComponentFactory mySimpleEventDelegate = new SimpleEventComponentFactory();
    private final StateEventComponentFactory myStateEventDelegate = new StateEventComponentFactory();
    private final BranchEventComponentFactory myBranchEventDelegate = new BranchEventComponentFactory();
    private final ComplexEventComponentFactory myComplexEventDelegate = new ComplexEventComponentFactory();

    public GHPRTimelineEventComponentFactoryImpl(GHPRTimelineViewModel timelineVm) {
        myAvatarIconsProvider = timelineVm.getAvatarIconsProvider();
        myGhostUser = timelineVm.getGhostUser();
    }

    @Override
    public CodeReviewTimelineItem createComponent(GHPRTimelineEvent event) {
        if (event instanceof GHPRTimelineEvent.Simple simple) {
            return mySimpleEventDelegate.createComponent(simple);
        }
        else if (event instanceof GHPRTimelineEvent.State state) {
            return myStateEventDelegate.createComponent(state);
        }
        else if (event instanceof GHPRTimelineEvent.Branch branch) {
            return myBranchEventDelegate.createComponent(branch);
        }
        else if (event instanceof GHPRTimelineEvent.Complex complex) {
            return myComplexEventDelegate.createComponent(complex);
        }
        throw throwUnknownType(event);
    }

    private static IllegalStateException throwUnknownType(GHPRTimelineEvent item) {
        return new IllegalStateException("Unknown event type \"" + item.getClass().getCanonicalName() + "\"");
    }

    CodeReviewCommentViewModel createTimelineItem(GHActor actor, @Nullable Date date, String text) {
        return new EventItem(GHPRTimelineUIUtil.createUserPresentation(actor, myAvatarIconsProvider), date, text);
    }

    private abstract class EventComponentFactory<T extends GHPRTimelineEvent> implements GHPRTimelineEventComponentFactory<T> {
        protected CodeReviewTimelineItem eventItem(GHPRTimelineEvent event, LocalizeValue detailsText) {
            return eventItem(event, detailsText.get());
        }

        protected CodeReviewTimelineItem eventItem(GHPRTimelineEvent event, String detailsText) {
            GHActor actor = event.getActor();
            return createTimelineItem(actor != null ? actor : myGhostUser, event.getCreatedAt(), detailsText);
        }

        protected CodeReviewTimelineItem eventItem(GHPRTimelineEvent event, LocalizeValue titleText, @Nullable String detailsText) {
            String text = detailsText == null ? titleText.get() : titleText.get() + "\n\n" + detailsText;
            return eventItem(event, text);
        }
    }

    private class SimpleEventComponentFactory extends EventComponentFactory<GHPRTimelineEvent.Simple> {
        @Override
        public CodeReviewTimelineItem createComponent(GHPRTimelineEvent.Simple event) {
            if (event instanceof GHPRAssignedEvent assigned) {
                return eventItem(event, assigneesText(listOfNotNull(assigned.getUser()), List.of()));
            }
            else if (event instanceof GHPRUnassignedEvent unassigned) {
                return eventItem(event, assigneesText(List.of(), listOfNotNull(unassigned.getUser())));
            }
            else if (event instanceof GHPRReviewRequestedEvent requested) {
                return eventItem(event, reviewersText(listOfNullable(requested.getRequestedReviewer()), List.of()));
            }
            else if (event instanceof GHPRReviewUnrequestedEvent unrequested) {
                return eventItem(event, reviewersText(List.of(), listOfNullable(unrequested.getRequestedReviewer())));
            }
            else if (event instanceof GHPRLabeledEvent labeled) {
                return eventItem(event, labelsText(List.of(labeled.getLabel()), List.of()));
            }
            else if (event instanceof GHPRUnlabeledEvent unlabeled) {
                return eventItem(event, labelsText(List.of(), List.of(unlabeled.getLabel())));
            }
            else if (event instanceof GHPRRenamedTitleEvent renamed) {
                return eventItem(event, renameText(renamed.getPreviousTitle(), renamed.getCurrentTitle()));
            }
            else if (event instanceof GHPRTimelineMergedSimpleEvents merged) {
                List<String> paragraphs = new ArrayList<>();
                appendParagraph(paragraphs, labelsText(merged.getAddedLabels(), merged.getRemovedLabels()));
                appendParagraph(paragraphs, assigneesText(merged.getAssignedPeople(), merged.getUnassignedPeople()));
                appendParagraph(paragraphs, reviewersText(merged.getAddedReviewers(), merged.getRemovedReviewers()));
                Pair<String, String> rename = merged.getRename();
                appendParagraph(paragraphs, rename != null ? renameText(rename.getFirst(), rename.getSecond()) : "");

                return eventItem(event, String.join("\n\n", paragraphs));
            }
            throw throwUnknownType(event);
        }

        private String assigneesText(Collection<GHUser> assigned, Collection<GHUser> unassigned) {
            String assignedNames = assigned.isEmpty() ? null : assigned.stream().map(GHPRTimelineEventComponentFactoryImpl::actorHTML)
                .collect(Collectors.joining(", "));
            String unassignedNames = unassigned.isEmpty() ? null : unassigned.stream().map(GHPRTimelineEventComponentFactoryImpl::actorHTML)
                .collect(Collectors.joining(", "));

            if (assignedNames != null && unassignedNames != null) {
                return GithubLocalize.pullRequestTimelineEventAssignedAndUnassigned(assignedNames, unassignedNames).get();
            }
            else if (assignedNames != null) {
                return GithubLocalize.pullRequestTimelineEventAssigned(assignedNames).get();
            }
            else if (unassignedNames != null) {
                return GithubLocalize.pullRequestTimelineEventUnassigned(unassignedNames).get();
            }
            return "";
        }

        private String reviewersText(Collection<? extends @Nullable GHPullRequestRequestedReviewer> added,
                                     Collection<? extends @Nullable GHPullRequestRequestedReviewer> removed) {
            String addedLogins = added.isEmpty() ? null : added.stream()
                .map(it -> reviewerHTML(it != null ? it : myGhostUser))
                .collect(Collectors.joining(", "));
            String removedLogins = removed.isEmpty() ? null : removed.stream()
                .map(it -> reviewerHTML(it != null ? it : myGhostUser))
                .collect(Collectors.joining(", "));

            if (addedLogins != null && removedLogins != null) {
                return GithubLocalize.pullRequestTimelineEventRequestedReviewAndRemovedReviewRequest(addedLogins, removedLogins).get();
            }
            else if (addedLogins != null) {
                return GithubLocalize.pullRequestTimelineEventRequestedReview(addedLogins).get();
            }
            else if (removedLogins != null) {
                return GithubLocalize.pullRequestTimelineEventRemovedReviewRequest(removedLogins).get();
            }
            return "";
        }

        private String labelsText(Collection<GHLabel> added, Collection<GHLabel> removed) {
            String addedLabels = added.isEmpty() ? null : added.stream().map(GHPRTimelineEventComponentFactoryImpl::labelHTML)
                .collect(Collectors.joining(", "));
            String removedLabels = removed.isEmpty() ? null : removed.stream().map(GHPRTimelineEventComponentFactoryImpl::labelHTML)
                .collect(Collectors.joining(", "));

            if (addedLabels != null && removedLabels != null) {
                return GithubLocalize.pullRequestTimelineEventLabelsAddedAndRemoved(addedLabels, removedLabels).get();
            }
            else if (addedLabels != null) {
                return GithubLocalize.pullRequestTimelineEventLabelsAdded(addedLabels).get();
            }
            else if (removedLabels != null) {
                return GithubLocalize.pullRequestTimelineEventLabelsRemoved(removedLabels).get();
            }
            return "";
        }

        private String renameText(String oldName, String newName) {
            return GithubLocalize.pullRequestTimelineRenamed(oldName, newName).get();
        }
    }

    private class StateEventComponentFactory extends EventComponentFactory<GHPRTimelineEvent.State> {
        @Override
        public CodeReviewTimelineItem createComponent(GHPRTimelineEvent.State event) {
            return switch (event.getNewState()) {
                case CLOSED -> eventItem(event, GithubLocalize.pullRequestTimelineClosed());
                case MERGED -> {
                    GHPRTimelineEvent.State lastEvent =
                        event instanceof GHPRTimelineMergedStateEvents merged ? merged.getLastStateEvent() : event;
                    GHPRMergedEvent mergeEvent = (GHPRMergedEvent) lastEvent;
                    GHCommitShort commit = mergeEvent.getCommit();
                    LocalizeValue text;
                    if (commit != null) {
                        String commitText = IssueIdsProcessor.markdownLink(commit.getAbbreviatedOid(), commit.getUrl());
                        String ref = branchHTML(mergeEvent.getMergeRefName());
                        text = GithubLocalize.pullRequestTimelineMergedCommit(commitText, ref);
                    }
                    else {
                        text = GithubLocalize.pullRequestTimelineMerged();
                    }
                    yield eventItem(event, text);
                }
                case OPEN -> eventItem(event, GithubLocalize.pullRequestTimelineReopened());
            };
        }
    }

    private class BranchEventComponentFactory extends EventComponentFactory<GHPRTimelineEvent.Branch> {
        @Override
        public CodeReviewTimelineItem createComponent(GHPRTimelineEvent.Branch event) {
            if (event instanceof GHPRBaseRefChangedEvent) {
                return eventItem(event, GithubLocalize.pullRequestTimelineChangedBaseBranch());
            }
            else if (event instanceof GHPRBaseRefForcePushedEvent forcePushed) {
                String ref = branchHTML(forcePushed.getRef());
                return eventItem(event, GithubLocalize.pullRequestTimelineBranchForcePushed(ref != null ? ref : "base"));
            }
            else if (event instanceof GHPRHeadRefForcePushedEvent forcePushed) {
                String ref = branchHTML(forcePushed.getRef());
                return eventItem(event, GithubLocalize.pullRequestTimelineBranchForcePushed(ref != null ? ref : "head"));
            }
            else if (event instanceof GHPRHeadRefDeletedEvent deleted) {
                String branch = GHPRTimelineEventComponentFactoryImpl.branchHTML(deleted.getHeadRefName());
                return eventItem(event, GithubLocalize.pullRequestTimelineBranchDeleted(branch));
            }
            else if (event instanceof GHPRHeadRefRestoredEvent) {
                return eventItem(event, GithubLocalize.pullRequestTimelineBranchHeadRestored());
            }
            throw throwUnknownType(event);
        }

        private @Nullable String branchHTML(@Nullable GHGitRefName ref) {
            return ref == null ? null : GHPRTimelineEventComponentFactoryImpl.branchHTML(ref.getName());
        }
    }

    private class ComplexEventComponentFactory extends EventComponentFactory<GHPRTimelineEvent.Complex> {
        @Override
        public CodeReviewTimelineItem createComponent(GHPRTimelineEvent.Complex event) {
            if (event instanceof GHPRReviewDismissedEvent dismissed) {
                GHActor reviewAuthor = dismissed.getReviewAuthor();
                String author = (reviewAuthor != null ? reviewAuthor : myGhostUser).getLogin();
                StringBuilder text = new StringBuilder(GithubLocalize.pullRequestTimelineStaleReviewDismissed(author).get()
                    .replace("<b>", "**")
                    .replace("</b>", "**"));
                String msg = dismissed.getDismissalMessageHTML();
                if (msg != null) {
                    text.append("\n\n").append(msg);
                }
                return eventItem(event, text.toString());
            }
            else if (event instanceof GHPRReadyForReviewEvent) {
                return eventItem(event, GithubLocalize.pullRequestTimelineMarkedAsReady());
            }
            else if (event instanceof GHPRConvertToDraftEvent) {
                return eventItem(event, GithubLocalize.pullRequestTimelineMarkedAsDraft());
            }
            else if (event instanceof GHPRCrossReferencedEvent crossReferenced) {
                return eventItem(event, GithubLocalize.pullRequestTimelineMentioned(), asReferenceLink(crossReferenced.getSource()));
            }
            else if (event instanceof GHPRConnectedEvent connected) {
                return eventItem(event, GithubLocalize.pullRequestTimelineConnected(), asReferenceLink(connected.getSubject()));
            }
            else if (event instanceof GHPRDisconnectedEvent disconnected) {
                return eventItem(event, GithubLocalize.pullRequestTimelineDisconnected(), asReferenceLink(disconnected.getSubject()));
            }
            throw throwUnknownType(event);
        }
    }

    static String branchHTML(String name) {
        return "`" + name + "`";
    }

    private static String labelHTML(GHLabel label) {
        return "`" + label.getName() + "`";
    }

    private static String actorHTML(GHActor actor) {
        return IssueIdsProcessor.markdownLink(actor.getPresentableName(), actor.getUrl());
    }

    private static String reviewerHTML(GHPullRequestRequestedReviewer reviewer) {
        return IssueIdsProcessor.markdownLink(reviewer.getPresentableName(), reviewer.getUrl());
    }

    private static void appendParagraph(List<String> paragraphs, String text) {
        if (!text.isEmpty()) {
            paragraphs.add(text);
        }
    }

    private static String asReferenceLink(GHPRReferencedSubject subject) {
        String linkTarget = subject instanceof GHPRReferencedSubject.PullRequest
            ? GHMarkdownToHtmlConverter.OPEN_PR_LINK_PREFIX + subject.getNumber()
            : subject.getUrl();
        return subject.getTitle() + " " + IssueIdsProcessor.markdownLink("#" + subject.getNumber(), linkTarget);
    }

    private static <T> List<T> listOfNotNull(@Nullable T value) {
        return value == null ? List.of() : List.of(value);
    }

    private static <T> List<@Nullable T> listOfNullable(@Nullable T value) {
        List<@Nullable T> result = new ArrayList<>(1);
        result.add(value);
        return result;
    }

    static final class EventItem implements CodeReviewCommentViewModel {
        private final UserPresentation myAuthor;
        private final @Nullable Date myCreatedAt;
        private final ObservableValue<String> myBody;

        EventItem(UserPresentation author, @Nullable Date createdAt, String body) {
            myAuthor = author;
            myCreatedAt = createdAt;
            myBody = ObservableValue.of(body);
        }

        @Override
        public String getId() {
            long time = myCreatedAt == null ? 0 : myCreatedAt.getTime();
            return "event:" + myAuthor.getUsername() + ":" + time + ":" + myBody.get().hashCode();
        }

        @Override
        public UserPresentation getAuthor() {
            return myAuthor;
        }

        @Override
        public Date getCreatedAt() {
            return myCreatedAt != null ? myCreatedAt : new Date(0);
        }

        @Override
        public ObservableValue<String> getBody() {
            return myBody;
        }
    }
}
