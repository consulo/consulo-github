// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api;

public final class GHGQLQueries {
    public static final String findOrganizationTeams = "graphql/query/findOrganizationTeams.graphql";
    public static final String getMentionableUsers = "graphql/query/findMentionableUsers.graphql";
    public static final String getPullRequestParticipants = "graphql/query/pullRequestParticipants.graphql";
    public static final String findUser = "graphql/query/findUser.graphql";
    public static final String findRepository = "graphql/query/findRepository.graphql";
    public static final String getPullRequestTemplates = "graphql/query/getPullRequestTemplates.graphql";
    public static final String getProtectionRules = "graphql/query/getProtectionRules.graphql";
    public static final String updateIssueComment = "graphql/query/updateIssueComment.graphql";
    public static final String deleteIssueComment = "graphql/query/deleteIssueComment.graphql";
    public static final String issueSearch = "graphql/query/issueSearch.graphql";
    public static final String findPullRequestsByHeadBranch = "graphql/query/findPullRequestsByHeadBranch.graphql";
    public static final String metrics = "graphql/query/metrics.graphql";
    public static final String createPullRequest = "graphql/query/createPullRequest.graphql";
    public static final String findPullRequest = "graphql/query/findPullRequest.graphql";
    public static final String findPullRequestId = "graphql/query/findPullRequestId.graphql";
    public static final String updatePullRequest = "graphql/query/updatePullRequest.graphql";
    public static final String markPullRequestReadyForReview = "graphql/query/markPullRequestReadyForReview.graphql";
    public static final String pullRequestTimeline = "graphql/query/pullRequestTimeline.graphql";
    public static final String pullRequestReviewThreads = "graphql/query/pullRequestReviewThreads.graphql";
    public static final String pullRequestCommits = "graphql/query/pullRequestCommits.graphql";
    public static final String pullRequestMergeabilityData = "graphql/query/findPullRequestMergeability.graphql";
    public static final String pullRequestFiles = "graphql/query/pullRequestFiles.graphql";
    public static final String deleteRef = "graphql/query/deleteRef.graphql";
    public static final String markFileAsViewed = "graphql/query/markFileAsViewed.graphql";
    public static final String unmarkFileAsViewed = "graphql/query/unmarkFileAsViewed.graphql";
    public static final String createReview = "graphql/query/createReview.graphql";
    public static final String submitReview = "graphql/query/submitReview.graphql";
    public static final String updateReview = "graphql/query/updateReview.graphql";
    public static final String deleteReview = "graphql/query/deleteReview.graphql";
    public static final String pendingReview = "graphql/query/findPendingReview.graphql";
    public static final String addReviewComment = "graphql/query/addReviewComment.graphql";
    public static final String deleteReviewComment = "graphql/query/deleteReviewComment.graphql";
    public static final String updateReviewComment = "graphql/query/updateReviewComment.graphql";
    public static final String addPullRequestReviewThread = "graphql/query/addPullRequestReviewThread.graphql";
    public static final String resolveReviewThread = "graphql/query/resolveReviewThread.graphql";
    public static final String unresolveReviewThread = "graphql/query/unresolveReviewThread.graphql";
    public static final String addReaction = "graphql/query/addReaction.graphql";
    public static final String removeReaction = "graphql/query/removeReaction.graphql";
    public static final String getRepositoryCommitStatus = "graphql/query/getRepositoryCommitStatusShort.graphql";
    public static final String getRepositoryCommitStatusContexts = "graphql/query/getRepositoryCommitStatusContexts.graphql";

    private GHGQLQueries() {
    }
}
