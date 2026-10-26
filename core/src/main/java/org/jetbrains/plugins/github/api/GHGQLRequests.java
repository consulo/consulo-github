// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.diff.util.Side;
import consulo.http.graphql.GraphQLRequestPagination;
import consulo.http.graphql.dto.GraphQLConnectionDTO;
import consulo.http.graphql.dto.GraphQLCursorPageInfoDTO;
import consulo.http.graphql.dto.GraphQLNodesDTO;
import consulo.http.graphql.dto.GraphQLPagedResponseDataDTO;
import org.jetbrains.plugins.github.api.GithubApiRequest.Post.GQLQuery;
import org.jetbrains.plugins.github.api.data.GHBranchProtectionRule;
import org.jetbrains.plugins.github.api.data.GHComment;
import org.jetbrains.plugins.github.api.data.GHPullRequestMetrics;
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent;
import org.jetbrains.plugins.github.api.data.GHReaction;
import org.jetbrains.plugins.github.api.data.GHReactionContent;
import org.jetbrains.plugins.github.api.data.GHRepository;
import org.jetbrains.plugins.github.api.data.GHRepositoryPullRequestTemplate;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.GithubIssueState;
import org.jetbrains.plugins.github.api.data.commit.GHCommitStatusRollupContextDTO;
import org.jetbrains.plugins.github.api.data.commit.GHCommitStatusRollupShortDTO;
import org.jetbrains.plugins.github.api.data.graphql.query.GHGQLSearchQueryResponse;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestBranchMatch;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestChangedFile;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommit;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeabilityData;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestPendingReviewDTO;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort;
import org.jetbrains.plugins.github.api.data.pullrequest.GHTeam;
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem;
import org.jetbrains.plugins.github.api.data.request.GHPullRequestDraftReviewThread;
import org.jetbrains.plugins.github.api.data.request.search.GithubIssueSearchType;
import org.jetbrains.plugins.github.api.util.GHSchemaPreview;
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery;
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.QualifierName;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GHGQLRequests {
    private GHGQLRequests() {
    }

    private static Map<String, @Nullable Object> vars(@Nullable Object... keysAndValues) {
        Map<String, @Nullable Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) Objects.requireNonNull(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return map;
    }

    private static <Q extends GQLQuery<?>> Q configure(Q query, GithubApiRequestOperation operation, String operationName) {
        query.withOperation(operation);
        query.withOperationName(operationName);
        return query;
    }

    private static <Q extends GQLQuery<?>> Q configure(Q query, String acceptMimeType, GithubApiRequestOperation operation, String operationName) {
        query.setAcceptMimeType(acceptMimeType);
        return configure(query, operation, operationName);
    }

    private static @Nullable Integer pageSize(@Nullable GraphQLRequestPagination pagination) {
        return pagination == null ? null : pagination.getPageSize();
    }

    private static @Nullable String cursor(@Nullable GraphQLRequestPagination pagination) {
        return pagination == null ? null : pagination.getAfterCursor();
    }

    public static final class User {
        private User() {
        }

        public static GQLQuery<@Nullable GHUser> find(GithubServerPath server, String login) {
            return configure(new GQLQuery.OptionalTraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.findUser,
                    vars("login", login), GHUser.class, "user"),
                GithubApiRequestOperation.GraphQLGetUser, "get user");
        }
    }

    public static final class Organization {
        private Organization() {
        }

        public static final class Team {
            private Team() {
            }

            public static GQLQuery<GraphQLPagedResponseDataDTO<GHTeam>> findAll(GithubServerPath server,
                                                                              String organization,
                                                                              @Nullable GraphQLRequestPagination pagination) {
                return configure(new GQLQuery.TraversedParsed<GraphQLPagedResponseDataDTO<GHTeam>>(server.toGraphQLUrl(),
                        GHGQLQueries.findOrganizationTeams,
                        vars("organization", organization, "pageSize", pageSize(pagination), "cursor", cursor(pagination)),
                        TeamsConnection.class,
                        "organization", "teams"),
                    GithubApiRequestOperation.GraphQLGetTeamsForOrganization, "get teams in organization");
            }

            private static class TeamsConnection extends GraphQLConnectionDTO<GHTeam> {
                @JsonCreator
                TeamsConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo, @JsonProperty("nodes") List<GHTeam> nodes) {
                    super(pageInfo, nodes);
                }
            }
        }
    }

    public static final class Repo {
        private Repo() {
        }

        public static GQLQuery<GraphQLPagedResponseDataDTO<GHUser>> findMentionableUsers(GHRepositoryCoordinates repository,
                                                                                        GithubServerPath server,
                                                                                        @Nullable GraphQLRequestPagination pagination) {
            return configure(new GQLQuery.TraversedParsed<GraphQLPagedResponseDataDTO<GHUser>>(server.toGraphQLUrl(),
                    GHGQLQueries.getMentionableUsers,
                    vars("repoOwner", repository.getRepositoryPath().getOwner(),
                        "repoName", repository.getRepositoryPath().getRepository(),
                        "pageSize", pageSize(pagination),
                        "cursor", cursor(pagination)),
                    UserConnection.class,
                    "repository", "mentionableUsers"),
                GithubApiRequestOperation.GraphQLGetMentionableUsers, "get mentionable users");
        }

        public static GQLQuery<@Nullable GHRepository> find(GHRepositoryCoordinates repository) {
            return configure(new GQLQuery.OptionalTraversedParsed<>(repository.getServerPath().toGraphQLUrl(), GHGQLQueries.findRepository,
                    vars("repoOwner", repository.getRepositoryPath().getOwner(),
                        "repoName", repository.getRepositoryPath().getRepository()),
                    GHRepository.class,
                    "repository"),
                GithubApiRequestOperation.GraphQLGetRepository, "get repository");
        }

        public static GQLQuery<@Nullable List<GHRepositoryPullRequestTemplate>> loadPullRequestTemplates(GHRepositoryCoordinates repository) {
            return configure(new GQLQuery.OptionalTraversedParsedList<>(repository.getServerPath().toGraphQLUrl(),
                    GHGQLQueries.getPullRequestTemplates,
                    vars("repoOwner", repository.getRepositoryPath().getOwner(),
                        "repoName", repository.getRepositoryPath().getRepository()),
                    GHRepositoryPullRequestTemplate.class,
                    "repository", "pullRequestTemplates"),
                GithubApiRequestOperation.GraphQLGetPullRequestTemplates, "get pull request templates");
        }

        public static GQLQuery<GraphQLPagedResponseDataDTO<GHBranchProtectionRule>> getProtectionRules(GHRepositoryCoordinates repository,
                                                                                                      @Nullable GraphQLRequestPagination pagination) {
            return configure(new GQLQuery.TraversedParsed<GraphQLPagedResponseDataDTO<GHBranchProtectionRule>>(
                    repository.getServerPath().toGraphQLUrl(),
                    GHGQLQueries.getProtectionRules,
                    vars("repoOwner", repository.getRepositoryPath().getOwner(),
                        "repoName", repository.getRepositoryPath().getRepository(),
                        "pageSize", pageSize(pagination),
                        "cursor", cursor(pagination)),
                    ProtectedRulesConnection.class,
                    "repository", "branchProtectionRules"),
                GithubApiRequestOperation.GraphQLGetBranchProtectionRules, "get branch protection rules");
        }

        private static class ProtectedRulesConnection extends GraphQLConnectionDTO<GHBranchProtectionRule> {
            @JsonCreator
            ProtectedRulesConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo,
                                     @JsonProperty("nodes") List<GHBranchProtectionRule> nodes) {
                super(pageInfo, nodes);
            }
        }

        public static GQLQuery<@Nullable GHCommitStatusRollupShortDTO> getCommitStatus(GHRepositoryCoordinates repo, String commitHash) {
            return configure(new GQLQuery.OptionalTraversedParsed<>(repo.getServerPath().toGraphQLUrl(),
                    GHGQLQueries.getRepositoryCommitStatus,
                    vars("repoOwner", repo.getRepositoryPath().getOwner(),
                        "repoName", repo.getRepositoryPath().getRepository(),
                        "oid", commitHash),
                    GHCommitStatusRollupShortDTO.class,
                    "repository", "object", "statusCheckRollup"),
                GithubApiRequestOperation.GraphQLGetCommitStatuses, "get commit statuses");
        }

        public static GQLQuery<@Nullable GraphQLConnectionDTO<GHCommitStatusRollupContextDTO>> getCommitStatusContext(GHRepositoryCoordinates repo,
                                                                                                                   String commitHash,
                                                                                                                   GraphQLRequestPagination pageData) {
            Map<String, @Nullable Object> variables = vars("repoOwner", repo.getRepositoryPath().getOwner(),
                "repoName", repo.getRepositoryPath().getRepository(),
                "oid", commitHash);
            variables.putAll(pageData.asParameters());
            return configure(new GQLQuery.OptionalTraversedParsed<GraphQLConnectionDTO<GHCommitStatusRollupContextDTO>>(
                    repo.getServerPath().toGraphQLUrl(),
                    GHGQLQueries.getRepositoryCommitStatusContexts,
                    variables,
                    ContextsConnection.class,
                    "repository", "object", "statusCheckRollup", "contexts"),
                GithubApiRequestOperation.GraphQLGetCommitStatusContexts, "get commit status contexts");
        }

        private static class ContextsConnection extends GraphQLConnectionDTO<GHCommitStatusRollupContextDTO> {
            @JsonCreator
            ContextsConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo,
                               @JsonProperty("nodes") List<GHCommitStatusRollupContextDTO> nodes) {
                super(pageInfo, nodes);
            }
        }
    }

    public static final class Comment {
        private Comment() {
        }

        public static GQLQuery<GHComment> updateComment(GithubServerPath server, String commentId, String newText) {
            return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.updateIssueComment,
                    vars("id", commentId, "body", newText),
                    GHComment.class,
                    "updateIssueComment", "issueComment"),
                GithubApiRequestOperation.GraphQLUpdateIssueComment, "update issue comment");
        }

        public static GQLQuery<@Nullable Object> deleteComment(GithubServerPath server, String commentId) {
            return configure(new GQLQuery.TraversedParsed<@Nullable Object>(server.toGraphQLUrl(), GHGQLQueries.deleteIssueComment,
                    vars("id", commentId),
                    Object.class),
                GithubApiRequestOperation.GraphQLDeleteIssueComment, "delete issue comment");
        }

        public static GQLQuery<GHReaction> addReaction(GithubServerPath server, String commentId, GHReactionContent reaction) {
            return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.addReaction,
                    vars("id", commentId, "reaction", reaction),
                    GHReaction.class,
                    "addReaction", "reaction"),
                GithubApiRequestOperation.GraphQLAddReactionToComment, "add reaction");
        }

        public static GQLQuery<GHReaction> removeReaction(GithubServerPath server, String commentId, GHReactionContent reaction) {
            return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.removeReaction,
                    vars("id", commentId, "reaction", reaction),
                    GHReaction.class,
                    "removeReaction", "reaction"),
                GithubApiRequestOperation.GraphQLRemoveReactionFromComment, "remove reaction");
        }
    }

    public static final class Ref {
        private Ref() {
        }

        public static GQLQuery<Void> delete(GithubServerPath server, String refId) {
            return new GQLQuery.Parsed<>(server.toGraphQLUrl(), GHGQLQueries.deleteRef, vars("refId", refId), Void.class);
        }
    }

    private static class UserConnection extends GraphQLConnectionDTO<GHUser> {
        @JsonCreator
        UserConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo, @JsonProperty("nodes") List<GHUser> nodes) {
            super(pageInfo, nodes);
        }
    }

    public static final class PullRequest {
        private PullRequest() {
        }

        public static GQLQuery<@Nullable GHPRIdentifier> findOneId(GHRepositoryCoordinates repository, long number) {
            return configure(new GQLQuery.OptionalTraversedParsed<>(repository.getServerPath().toGraphQLUrl(), GHGQLQueries.findPullRequestId,
                    vars("repoOwner", repository.getRepositoryPath().getOwner(),
                        "repoName", repository.getRepositoryPath().getRepository(),
                        "number", number),
                    GHPRIdentifier.class,
                    "repository", "pullRequest"),
                GHSchemaPreview.PR_DRAFT.getMimeType(), GithubApiRequestOperation.GraphQLGetPullRequestId, "get pull request id");
        }

        public static GQLQuery<GHPullRequestShort> create(GHRepositoryCoordinates repository,
                                                          String repositoryId,
                                                          String baseRefName,
                                                          String headRefName,
                                                          String title,
                                                          @Nullable String body,
                                                          @Nullable Boolean draft) {
            return configure(new GQLQuery.TraversedParsed<>(repository.getServerPath().toGraphQLUrl(), GHGQLQueries.createPullRequest,
                    vars("repositoryId", repositoryId,
                        "baseRefName", baseRefName,
                        "headRefName", headRefName,
                        "title", title,
                        "body", body,
                        "draft", draft),
                    GHPullRequestShort.class,
                    "createPullRequest", "pullRequest"),
                GHSchemaPreview.PR_DRAFT.getMimeType(), GithubApiRequestOperation.GraphQLCreatePullRequest, "create pull request");
        }

        public static GQLQuery<@Nullable GHPullRequest> findOne(GHRepositoryCoordinates repository, long number) {
            return configure(new GQLQuery.OptionalTraversedParsed<>(repository.getServerPath().toGraphQLUrl(), GHGQLQueries.findPullRequest,
                    vars("repoOwner", repository.getRepositoryPath().getOwner(),
                        "repoName", repository.getRepositoryPath().getRepository(),
                        "number", number),
                    GHPullRequest.class,
                    "repository", "pullRequest"),
                GHSchemaPreview.PR_DRAFT.getMimeType(), GithubApiRequestOperation.GraphQLGetPullRequest, "get pull request");
        }

        public static GQLQuery<GHPullRequest> update(GHRepositoryCoordinates repository,
                                                     String pullRequestId,
                                                     @Nullable String title,
                                                     @Nullable String description) {
            Map<String, @Nullable Object> parameters = vars("pullRequestId", pullRequestId);
            if (title != null) {
                parameters.put("title", title);
            }
            if (description != null) {
                parameters.put("body", description);
            }

            return configure(new GQLQuery.TraversedParsed<>(repository.getServerPath().toGraphQLUrl(), GHGQLQueries.updatePullRequest,
                    parameters,
                    GHPullRequest.class,
                    "updatePullRequest", "pullRequest"),
                GHSchemaPreview.PR_DRAFT.getMimeType(), GithubApiRequestOperation.GraphQLUpdatePullRequest, "update pull request");
        }

        public static GQLQuery<@Nullable Object> markReadyForReview(GHRepositoryCoordinates repository, String pullRequestId) {
            return configure(new GQLQuery.Parsed<@Nullable Object>(repository.getServerPath().toGraphQLUrl(),
                    GHGQLQueries.markPullRequestReadyForReview,
                    vars("pullRequestId", pullRequestId),
                    Object.class),
                GHSchemaPreview.PR_DRAFT.getMimeType(), GithubApiRequestOperation.GraphQLMarkPullRequestReadyForReview,
                "mark pull request ready for review");
        }

        public static GQLQuery<@Nullable GHPullRequestMergeabilityData> mergeabilityData(GHRepositoryCoordinates repository, long number) {
            return configure(new GQLQuery.OptionalTraversedParsed<>(repository.getServerPath().toGraphQLUrl(),
                    GHGQLQueries.pullRequestMergeabilityData,
                    vars("repoOwner", repository.getRepositoryPath().getOwner(),
                        "repoName", repository.getRepositoryPath().getRepository(),
                        "number", number),
                    GHPullRequestMergeabilityData.class,
                    "repository", "pullRequest"),
                GHSchemaPreview.CHECKS.getMimeType() + "," + GHSchemaPreview.PR_MERGE_INFO.getMimeType(),
                GithubApiRequestOperation.GraphQLGetMergeabilityData, "get mergeability data");
        }

        public static GQLQuery<GHGQLSearchQueryResponse<GHPullRequestShort>> search(GithubServerPath server,
                                                                                   String query,
                                                                                   @Nullable GraphQLRequestPagination pagination) {
            return configure(new GQLQuery.Parsed<GHGQLSearchQueryResponse<GHPullRequestShort>>(server.toGraphQLUrl(), GHGQLQueries.issueSearch,
                    vars("query", query, "pageSize", pageSize(pagination), "cursor", cursor(pagination)),
                    PRSearch.class),
                GHSchemaPreview.PR_DRAFT.getMimeType(), GithubApiRequestOperation.GraphQLSearchPullRequests, "search issues");
        }

        /**
         * Finds open pull requests whose head branch matches one of {@code headBranchRefs}, in a single request.
         * Each entry in {@code headBranchRefs} is a bare branch name, as accepted by the GitHub "head" search qualifier.
         * Do not prefix an entry with the owner. The "owner:branch-name" form fits the REST "head" parameter, not this qualifier.
         */
        public static GQLQuery<GHGQLSearchQueryResponse<GHPullRequestBranchMatch>> searchByHeadBranches(GHRepositoryCoordinates repository,
                                                                                                       Collection<String> headBranchRefs) {
            return configure(new GQLQuery.Parsed<GHGQLSearchQueryResponse<GHPullRequestBranchMatch>>(repository.getServerPath().toGraphQLUrl(),
                    GHGQLQueries.findPullRequestsByHeadBranch,
                    vars("query", buildHeadBranchSearchQuery(repository, headBranchRefs),
                        "pageSize", Math.max(1, Math.min(100, headBranchRefs.size()))),
                    BranchMatchSearch.class),
                GHSchemaPreview.PR_DRAFT.getMimeType(), GithubApiRequestOperation.GraphQLSearchPullRequestsByHeadBranch,
                "search pull requests by head branch");
        }

        private static String buildHeadBranchSearchQuery(GHRepositoryCoordinates repository, Collection<String> headBranchRefs) {
            List<GHPRSearchQuery.Term<?>> headTerms = new ArrayList<>();
            for (String ref : headBranchRefs) {
                headTerms.add(QualifierName.head.createTerm("\"" + ref + "\""));
            }
            return new GHPRSearchQuery(List.of(
                QualifierName.repo.createTerm(repository.getRepositoryPath().toString(true)),
                QualifierName.type.createTerm(GithubIssueSearchType.pr.name()),
                QualifierName.state.createTerm(GithubIssueState.open.name()),
                new GHPRSearchQuery.Term.Or(headTerms)
            )).toString();
        }

        private static class BranchMatchSearch extends GHGQLSearchQueryResponse<GHPullRequestBranchMatch> {
            @JsonCreator
            BranchMatchSearch(@JsonProperty("search") SearchConnection<GHPullRequestBranchMatch> search) {
                super(search);
            }
        }

        public static GQLQuery<GraphQLPagedResponseDataDTO<GHUser>> findParticipants(GHRepositoryCoordinates repository,
                                                                                    long number,
                                                                                    @Nullable GraphQLRequestPagination pagination) {
            return configure(new GQLQuery.TraversedParsed<GraphQLPagedResponseDataDTO<GHUser>>(repository.getServerPath().toGraphQLUrl(),
                    GHGQLQueries.getPullRequestParticipants,
                    vars("repoOwner", repository.getRepositoryPath().getOwner(),
                        "repoName", repository.getRepositoryPath().getRepository(),
                        "number", number,
                        "pageSize", pageSize(pagination),
                        "cursor", cursor(pagination)),
                    UserConnection.class,
                    "repository", "pullRequest", "participants"),
                GithubApiRequestOperation.GraphQLGetPullRequestParticipants, "get pull request participants");
        }

        static GQLQuery<GHPullRequestMetrics> metrics(GHRepositoryCoordinates repo) {
            return configure(new GQLQuery.Parsed<>(repo.getServerPath().toGraphQLUrl(), GHGQLQueries.metrics,
                    MetricCountType.params(repo),
                    GHPullRequestMetrics.class),
                GithubApiRequestOperation.GraphQLPullRequestsMetrics, "count pull requests");
        }

        private enum MetricCountType {
            ALL("allPRCountQuery"),
            OPEN("openPRCountQuery"),
            OPEN_AUTHORED("openAuthoredPRCountQuery"),
            OPEN_ASSIGNEE("openAssigneePRCountQuery"),
            OPEN_TO_BE_REVIEWED("openReviewAssignedPRCountQuery"),
            OPEN_REVIEWED("openReviewedPRCountQuery");

            private final String myParamName;

            MetricCountType(String paramName) {
                myParamName = paramName;
            }

            private String constructMetricQuery(GHRepositoryCoordinates repo) {
                return GithubApiSearchQueryBuilder.searchQuery(it -> {
                    if (this != ALL) {
                        it.term(QualifierName.state.createTerm(GithubIssueState.open.name()));
                    }

                    it.term(QualifierName.type.createTerm(GithubIssueSearchType.pr.name()));
                    it.term(QualifierName.repo.createTerm(repo.getRepositoryPath().toString(true)));

                    switch (this) {
                        case OPEN_AUTHORED -> it.term(QualifierName.author.createTerm("@me"));
                        case OPEN_ASSIGNEE -> it.term(QualifierName.assignee.createTerm("@me"));
                        case OPEN_REVIEWED -> it.term(QualifierName.reviewedBy.createTerm("@me"));
                        case OPEN_TO_BE_REVIEWED -> it.term(QualifierName.reviewRequested.createTerm("@me"));
                        default -> {
                        }
                    }
                });
            }

            static Map<String, @Nullable Object> params(GHRepositoryCoordinates repo) {
                Map<String, @Nullable Object> params = new LinkedHashMap<>();
                for (MetricCountType type : values()) {
                    params.put(type.myParamName, type.constructMetricQuery(repo));
                }
                return params;
            }
        }

        private static class PRSearch extends GHGQLSearchQueryResponse<GHPullRequestShort> {
            @JsonCreator
            PRSearch(@JsonProperty("search") SearchConnection<GHPullRequestShort> search) {
                super(search);
            }
        }

        public static GQLQuery<GraphQLPagedResponseDataDTO<GHPullRequestReviewThread>> reviewThreads(GHRepositoryCoordinates repository,
                                                                                                    long number,
                                                                                                    @Nullable GraphQLRequestPagination pagination) {
            return configure(new GQLQuery.TraversedParsed<GraphQLPagedResponseDataDTO<GHPullRequestReviewThread>>(
                    repository.getServerPath().toGraphQLUrl(), GHGQLQueries.pullRequestReviewThreads,
                    parameters(repository, number, pagination),
                    ThreadsConnection.class, "repository", "pullRequest", "reviewThreads"),
                GithubApiRequestOperation.GraphQLGetReviewThreads, "get review threads");
        }

        private static class ThreadsConnection extends GraphQLConnectionDTO<GHPullRequestReviewThread> {
            @JsonCreator
            ThreadsConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo,
                              @JsonProperty("nodes") List<GHPullRequestReviewThread> nodes) {
                super(pageInfo, nodes);
            }
        }

        public static GQLQuery<GraphQLPagedResponseDataDTO<GHPullRequestCommit>> commits(GHRepositoryCoordinates repository,
                                                                                        long number,
                                                                                        @Nullable GraphQLRequestPagination pagination) {
            return configure(new GQLQuery.TraversedParsed<GraphQLPagedResponseDataDTO<GHPullRequestCommit>>(
                    repository.getServerPath().toGraphQLUrl(), GHGQLQueries.pullRequestCommits,
                    parameters(repository, number, pagination),
                    CommitsConnection.class, "repository", "pullRequest", "commits"),
                GithubApiRequestOperation.GraphQLGetPullRequestCommits, "get pull request commits");
        }

        private static class CommitsConnection extends GraphQLConnectionDTO<GHPullRequestCommit> {
            @JsonCreator
            CommitsConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo, @JsonProperty("nodes") List<GHPullRequestCommit> nodes) {
                super(pageInfo, nodes);
            }
        }

        public static GQLQuery<GraphQLPagedResponseDataDTO<GHPullRequestChangedFile>> files(GHRepositoryCoordinates repository,
                                                                                           long number,
                                                                                           GraphQLRequestPagination pagination) {
            return configure(new GQLQuery.TraversedParsed<GraphQLPagedResponseDataDTO<GHPullRequestChangedFile>>(
                    repository.getServerPath().toGraphQLUrl(), GHGQLQueries.pullRequestFiles,
                    parameters(repository, number, pagination),
                    FilesConnection.class, "repository", "pullRequest", "files"),
                GithubApiRequestOperation.GraphQLGetPullRequestFiles, "get pull request files");
        }

        private static class FilesConnection extends GraphQLConnectionDTO<GHPullRequestChangedFile> {
            @JsonCreator
            FilesConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo,
                            @JsonProperty("nodes") List<GHPullRequestChangedFile> nodes) {
                super(pageInfo, nodes);
            }
        }

        public static GQLQuery<Void> markFileAsViewed(GithubServerPath server, String pullRequestId, String path) {
            return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.markFileAsViewed,
                    vars("pullRequestId", pullRequestId, "path", path),
                    Void.class),
                GithubApiRequestOperation.GraphQLMarkFileAsViewed, "mark file as viewed");
        }

        public static GQLQuery<Void> unmarkFileAsViewed(GithubServerPath server, String pullRequestId, String path) {
            return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.unmarkFileAsViewed,
                    vars("pullRequestId", pullRequestId, "path", path),
                    Void.class),
                GithubApiRequestOperation.GraphQLUnmarkFileAsViewed, "unmark file as viewed");
        }

        public static final class Timeline {
            private Timeline() {
            }

            public static GQLQuery<GraphQLPagedResponseDataDTO<GHPRTimelineItem>> items(GithubServerPath server,
                                                                                       String repoOwner,
                                                                                       String repoName,
                                                                                       long number,
                                                                                       @Nullable GraphQLRequestPagination pagination) {
                return configure(new GQLQuery.TraversedParsed<GraphQLPagedResponseDataDTO<GHPRTimelineItem>>(server.toGraphQLUrl(),
                        GHGQLQueries.pullRequestTimeline,
                        vars("repoOwner", repoOwner,
                            "repoName", repoName,
                            "number", number,
                            "pageSize", pageSize(pagination),
                            "cursor", cursor(pagination),
                            "since", pagination == null ? null : pagination.getSince()),
                        TimelineConnection.class,
                        "repository", "pullRequest", "timelineItems"),
                    GHSchemaPreview.PR_DRAFT.getMimeType(), GithubApiRequestOperation.GraphQLGetPullRequestTimelineItems,
                    "get pull request timeline items");
            }

            private static class TimelineConnection extends GraphQLConnectionDTO<GHPRTimelineItem> {
                @JsonCreator
                TimelineConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo,
                                   @JsonProperty("nodes") List<@Nullable GHPRTimelineItem> nodes) {
                    super(pageInfo, nodes.stream().filter(Objects::nonNull).toList());
                }
            }
        }

        public static final class Review {
            private Review() {
            }

            public static GQLQuery<GHPullRequestPendingReviewDTO> create(GithubServerPath server,
                                                                         String pullRequestId,
                                                                         @Nullable GHPullRequestReviewEvent event,
                                                                         @Nullable String body,
                                                                         @Nullable String commitSha,
                                                                         @Nullable List<GHPullRequestDraftReviewThread> threads) {
                return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.createReview,
                        vars("pullRequestId", pullRequestId,
                            "event", event,
                            "commitOid", commitSha,
                            "threads", threads,
                            "body", body),
                        GHPullRequestPendingReviewDTO.class,
                        "addPullRequestReview", "pullRequestReview"),
                    GithubApiRequestOperation.GraphQLCreateReview, "create review");
            }

            public static GQLQuery<Object> submit(GithubServerPath server, String reviewId, GHPullRequestReviewEvent event, @Nullable String body) {
                return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.submitReview,
                        vars("reviewId", reviewId, "event", event, "body", body),
                        Object.class),
                    GithubApiRequestOperation.GraphQLSubmitReview, "submit review");
            }

            public static GQLQuery<GHPullRequestReview> updateBody(GithubServerPath server, String reviewId, String newText) {
                return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.updateReview,
                        vars("reviewId", reviewId, "body", newText),
                        GHPullRequestReview.class,
                        "updatePullRequestReview", "pullRequestReview"),
                    GithubApiRequestOperation.GraphQLUpdateReview, "update review");
            }

            public static GQLQuery<Object> delete(GithubServerPath server, String reviewId) {
                return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.deleteReview,
                        vars("reviewId", reviewId),
                        Object.class),
                    GithubApiRequestOperation.GraphQLDeleteReview, "delete review");
            }

            public static GQLQuery<GraphQLNodesDTO<GHPullRequestPendingReviewDTO>> pendingReviews(GithubServerPath server, String pullRequestId) {
                return configure(new GQLQuery.TraversedParsed<GraphQLNodesDTO<GHPullRequestPendingReviewDTO>>(server.toGraphQLUrl(),
                        GHGQLQueries.pendingReview,
                        vars("pullRequestId", pullRequestId),
                        PendingReviewNodes.class,
                        "node", "reviews"),
                    GithubApiRequestOperation.GraphQLGetPendingReviews, "get pending reviews");
            }

            private static class PendingReviewNodes extends GraphQLNodesDTO<GHPullRequestPendingReviewDTO> {
                @JsonCreator
                PendingReviewNodes(@JsonProperty("nodes") List<GHPullRequestPendingReviewDTO> nodes) {
                    super(nodes);
                }
            }

            public static GQLQuery<GHPullRequestReviewComment> addComment(GithubServerPath server,
                                                                          String reviewId,
                                                                          String body,
                                                                          String commitSha,
                                                                          String fileName,
                                                                          int diffLine) {
                return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.addReviewComment,
                        vars("reviewId", reviewId,
                            "body", body,
                            "commit", commitSha,
                            "file", fileName,
                            "position", diffLine),
                        GHPullRequestReviewComment.class,
                        "addPullRequestReviewComment", "comment"),
                    GithubApiRequestOperation.GraphQLCreateReviewCommentOnLine, "create review comment on line");
            }

            public static GQLQuery<GHPullRequestReviewComment> addComment(GithubServerPath server, String reviewId, String inReplyTo, String body) {
                return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.addReviewComment,
                        vars("reviewId", reviewId, "inReplyTo", inReplyTo, "body", body),
                        GHPullRequestReviewComment.class,
                        "addPullRequestReviewComment", "comment"),
                    GithubApiRequestOperation.GraphQLCreateReviewComment, "create review comment");
            }

            public static GQLQuery<GHPullRequestPendingReviewDTO> deleteComment(GithubServerPath server, String commentId) {
                return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.deleteReviewComment,
                        vars("id", commentId),
                        GHPullRequestPendingReviewDTO.class,
                        "deletePullRequestReviewComment", "pullRequestReview"),
                    GithubApiRequestOperation.GraphQLDeleteReviewComment, "delete review comment");
            }

            public static GQLQuery<GHPullRequestReviewComment> updateComment(GithubServerPath server, String commentId, String newText) {
                return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.updateReviewComment,
                        vars("id", commentId, "body", newText),
                        GHPullRequestReviewComment.class,
                        "updatePullRequestReviewComment", "pullRequestReviewComment"),
                    GithubApiRequestOperation.GraphQLUpdateReviewComment, "update review comment");
            }

            public static GQLQuery<GHPullRequestReviewThread> addThread(GithubServerPath server,
                                                                        String reviewId,
                                                                        String body,
                                                                        int line,
                                                                        Side side,
                                                                        int startLine,
                                                                        Side startSide,
                                                                        String fileName) {
                Map<String, @Nullable Object> params = vars("pullRequestReviewId", reviewId,
                    "path", fileName,
                    "side", side.name(),
                    "line", line,
                    "body", body);
                if (startLine != line) {
                    params.put("startSide", startSide.name());
                    params.put("startLine", startLine);
                }

                return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.addPullRequestReviewThread,
                        params,
                        GHPullRequestReviewThread.class,
                        "addPullRequestReviewThread", "thread"),
                    GithubApiRequestOperation.GraphQLCreateReviewThread, "create review thread on line");
            }

            public static GQLQuery<GHPullRequestReviewThread> resolveThread(GithubServerPath server, String threadId) {
                return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.resolveReviewThread,
                        vars("threadId", threadId),
                        GHPullRequestReviewThread.class,
                        "resolveReviewThread", "thread"),
                    GithubApiRequestOperation.GraphQLResolveReviewThread, "resolve review thread");
            }

            public static GQLQuery<GHPullRequestReviewThread> unresolveThread(GithubServerPath server, String threadId) {
                return configure(new GQLQuery.TraversedParsed<>(server.toGraphQLUrl(), GHGQLQueries.unresolveReviewThread,
                        vars("threadId", threadId),
                        GHPullRequestReviewThread.class,
                        "unresolveReviewThread", "thread"),
                    GithubApiRequestOperation.GraphQLUnresolveReviewThread, "unresolve review thread");
            }
        }
    }

    private static Map<String, @Nullable Object> parameters(GHRepositoryCoordinates repository,
                                                            long pullRequestNumber,
                                                            @Nullable GraphQLRequestPagination pagination) {
        return vars("repoOwner", repository.getRepositoryPath().getOwner(),
            "repoName", repository.getRepositoryPath().getRepository(),
            "number", pullRequestNumber,
            "pageSize", pageSize(pagination),
            "cursor", cursor(pagination));
    }
}
