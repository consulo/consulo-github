// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import consulo.http.page.LinkHttpHeaderValue;
import org.jetbrains.plugins.github.api.GithubApiRequest.Delete;
import org.jetbrains.plugins.github.api.GithubApiRequest.Get;
import org.jetbrains.plugins.github.api.GithubApiRequest.Patch;
import org.jetbrains.plugins.github.api.GithubApiRequest.Post;
import org.jetbrains.plugins.github.api.GithubApiRequest.Put;
import org.jetbrains.plugins.github.api.data.GHCommitsCompareResult;
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser;
import org.jetbrains.plugins.github.api.data.GithubBranch;
import org.jetbrains.plugins.github.api.data.GithubContent;
import org.jetbrains.plugins.github.api.data.GithubGist;
import org.jetbrains.plugins.github.api.data.GithubIssue;
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml;
import org.jetbrains.plugins.github.api.data.GithubIssueLabel;
import org.jetbrains.plugins.github.api.data.GithubIssueState;
import org.jetbrains.plugins.github.api.data.GithubOrg;
import org.jetbrains.plugins.github.api.data.GithubPullRequestMergeMethod;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed;
import org.jetbrains.plugins.github.api.data.GithubResponsePage;
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue;
import org.jetbrains.plugins.github.api.data.GithubTagInfo;
import org.jetbrains.plugins.github.api.data.GithubUser;
import org.jetbrains.plugins.github.api.data.GithubUserWithPermissions;
import org.jetbrains.plugins.github.api.data.commit.GHCommitFile;
import org.jetbrains.plugins.github.api.data.commit.GHCommitFiles;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRestIdOnly;
import org.jetbrains.plugins.github.api.data.request.Affiliation;
import org.jetbrains.plugins.github.api.data.request.GithubAssigneesCollectionRequest;
import org.jetbrains.plugins.github.api.data.request.GithubChangeIssueStateRequest;
import org.jetbrains.plugins.github.api.data.request.GithubCreateIssueCommentRequest;
import org.jetbrains.plugins.github.api.data.request.GithubCreateIssueRequest;
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest;
import org.jetbrains.plugins.github.api.data.request.GithubLabelsCollectionRequest;
import org.jetbrains.plugins.github.api.data.request.GithubPullRequestMergeRebaseRequest;
import org.jetbrains.plugins.github.api.data.request.GithubPullRequestMergeRequest;
import org.jetbrains.plugins.github.api.data.request.GithubPullUpdateRequest;
import org.jetbrains.plugins.github.api.data.request.GithubRepoRequest;
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination;
import org.jetbrains.plugins.github.api.data.request.GithubReviewersCollectionRequest;
import org.jetbrains.plugins.github.api.data.request.Type;
import org.jetbrains.plugins.github.api.data.request.Visibility;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery.QualifierName;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder.searchQuery;
import static org.jetbrains.plugins.github.api.util.GithubApiUrlQueryBuilder.paginationQuery;
import static org.jetbrains.plugins.github.api.util.GithubApiUrlQueryBuilder.urlQuery;

/**
 * Collection of factory methods for API requests used in plugin
 * TODO: improve url building (DSL?)
 */
public final class GithubApiRequests {
    private GithubApiRequests() {
    }

    public static GithubApiRequest<byte[]> getBytes(String url) {
        return new Get<>(url) {
            @Override
            public byte[] extractResult(GithubApiResponse response) throws IOException {
                return response.handleBody(it -> it.readAllBytes());
            }
        };
    }

    public static final class CurrentUser {
        static final String URL_SUFFIX = "/user";

        private CurrentUser() {
        }

        public static GithubApiRequest<GithubAuthenticatedUser> get(GithubServerPath server) {
            return get(getUrl(server, URL_SUFFIX));
        }

        public static GithubApiRequest<GithubAuthenticatedUser> get(String url) {
            return Get.json(url, GithubAuthenticatedUser.class)
                .withOperation(GithubApiRequestOperation.RestGetAuthenticatedUser)
                .withOperationName("get profile information");
        }

        public static GithubApiRequest<byte[]> getAvatar(String url) {
            return getBytes(url)
                .withOperation(GithubApiRequestOperation.RestGetAvatar)
                .withOperationName("get profile avatar");
        }

        public static final class Repos {
            static final String URL_SUFFIX = "/repos";

            private Repos() {
            }

            public static GithubApiPagesLoader.Request<GithubRepo> pages(GithubServerPath server) {
                return pages(server, null, null, null, null);
            }

            public static GithubApiPagesLoader.Request<GithubRepo> pages(GithubServerPath server,
                                                                        @Nullable Type type,
                                                                        @Nullable Visibility visibility,
                                                                        @Nullable Set<Affiliation> affiliations,
                                                                        @Nullable GithubRequestPagination pagination) {
                return new GithubApiPagesLoader.Request<>(get(server, type, visibility, affiliations, pagination), Repos::get);
            }

            public static GithubApiRequest<GithubResponsePage<GithubRepo>> get(GithubServerPath server) {
                return get(server, null, null, null, null);
            }

            public static GithubApiRequest<GithubResponsePage<GithubRepo>> get(GithubServerPath server,
                                                                               @Nullable Type type,
                                                                               @Nullable Visibility visibility,
                                                                               @Nullable Set<Affiliation> affiliations,
                                                                               @Nullable GithubRequestPagination pagination) {
                if (type != null && (visibility != null || affiliations != null)) {
                    throw new IllegalArgumentException("Param 'type' should not be used together with 'visibility' or 'affiliation'");
                }

                return get(getUrl(server, CurrentUser.URL_SUFFIX, URL_SUFFIX, urlQuery(it -> {
                    it.param(Type.KEY, type == null ? null : type.getValue());
                    it.param(Visibility.KEY, visibility == null ? null : visibility.getValue());
                    it.param(Affiliation.KEY, affiliations == null ? null : Affiliation.combine(affiliations));
                    it.param(pagination);
                })));
            }

            public static GithubApiRequest<GithubResponsePage<GithubRepo>> get(String url) {
                return Get.jsonPage(url, GithubRepo.class)
                    .withOperation(GithubApiRequestOperation.RestGetRepositoriesForUser)
                    .withOperationName("get user repositories");
            }

            public static GithubApiRequest<GithubRepo> create(GithubServerPath server, String name, String description, boolean isPrivate) {
                return create(server, name, description, isPrivate, null);
            }

            public static GithubApiRequest<GithubRepo> create(GithubServerPath server,
                                                              String name,
                                                              String description,
                                                              boolean isPrivate,
                                                              @Nullable Boolean autoInit) {
                return Post.json(getUrl(server, CurrentUser.URL_SUFFIX, URL_SUFFIX),
                        new GithubRepoRequest(name, description, isPrivate, autoInit), GithubRepo.class)
                    .withOperation(GithubApiRequestOperation.RestCreateRepository)
                    .withOperationName("create user repository");
            }
        }

        public static final class Orgs {
            static final String URL_SUFFIX = "/orgs";

            private Orgs() {
            }

            public static GithubApiPagesLoader.Request<GithubOrg> pages(GithubServerPath server) {
                return pages(server, null);
            }

            public static GithubApiPagesLoader.Request<GithubOrg> pages(GithubServerPath server, @Nullable GithubRequestPagination pagination) {
                return new GithubApiPagesLoader.Request<>(get(server, pagination), Orgs::get);
            }

            public static GithubApiRequest<GithubResponsePage<GithubOrg>> get(GithubServerPath server,
                                                                              @Nullable GithubRequestPagination pagination) {
                return get(getUrl(server, CurrentUser.URL_SUFFIX, URL_SUFFIX, paginationQuery(pagination)));
            }

            public static GithubApiRequest<GithubResponsePage<GithubOrg>> get(String url) {
                return Get.jsonPage(url, GithubOrg.class)
                    .withOperation(GithubApiRequestOperation.RestGetOrganizations)
                    .withOperationName("get user organizations");
            }
        }
    }

    public static final class Organisations {
        static final String URL_SUFFIX = "/orgs";

        private Organisations() {
        }

        public static final class Repos {
            static final String URL_SUFFIX = "/repos";

            private Repos() {
            }

            public static GithubApiPagesLoader.Request<GithubRepo> pages(GithubServerPath server,
                                                                        String organisation,
                                                                        @Nullable GithubRequestPagination pagination) {
                return new GithubApiPagesLoader.Request<>(get(server, organisation, pagination), Repos::get);
            }

            public static GithubApiRequest<GithubResponsePage<GithubRepo>> get(GithubServerPath server, String organisation) {
                return get(server, organisation, null);
            }

            public static GithubApiRequest<GithubResponsePage<GithubRepo>> get(GithubServerPath server,
                                                                               String organisation,
                                                                               @Nullable GithubRequestPagination pagination) {
                return get(getUrl(server, Organisations.URL_SUFFIX, "/", organisation, URL_SUFFIX, paginationQuery(pagination)));
            }

            public static GithubApiRequest<GithubResponsePage<GithubRepo>> get(String url) {
                return Get.jsonPage(url, GithubRepo.class)
                    .withOperation(GithubApiRequestOperation.RestGetRepositoriesForOrganization)
                    .withOperationName("get organisation repositories");
            }

            public static GithubApiRequest<GithubRepo> create(GithubServerPath server,
                                                              String organisation,
                                                              String name,
                                                              String description,
                                                              boolean isPrivate) {
                return Post.json(getUrl(server, Organisations.URL_SUFFIX, "/", organisation, URL_SUFFIX),
                        new GithubRepoRequest(name, description, isPrivate, null), GithubRepo.class)
                    .withOperation(GithubApiRequestOperation.RestCreateRepositoryInOrganization)
                    .withOperationName("create organisation repository");
            }
        }
    }

    public static final class Repos {
        static final String URL_SUFFIX = "/repos";

        private Repos() {
        }

        public static GithubApiRequest<@Nullable GithubRepoDetailed> get(GithubServerPath server, String username, String repoName) {
            return Get.Optional.json(getUrl(server, URL_SUFFIX, "/" + username + "/" + repoName), GithubRepoDetailed.class)
                .withOperation(GithubApiRequestOperation.RestGetRepository)
                .withOperationName("get information for repository " + username + "/" + repoName);
        }

        public static GithubApiRequest<@Nullable GithubRepoDetailed> get(String url) {
            return Get.Optional.json(url, GithubRepoDetailed.class)
                .withOperation(GithubApiRequestOperation.RestGetRepositoryByUrl)
                .withOperationName("get information for repository " + url);
        }

        public static GithubApiRequest<Void> delete(GithubServerPath server, String username, String repoName) {
            return delete(getUrl(server, URL_SUFFIX, "/" + username + "/" + repoName))
                .withOperation(GithubApiRequestOperation.RestDeleteRepository)
                .withOperationName("delete repository " + username + "/" + repoName);
        }

        public static GithubApiRequest<Void> delete(String url) {
            return Delete.json(url, Void.class)
                .withOperation(GithubApiRequestOperation.RestDeleteRepositoryByUrl)
                .withOperationName("delete repository at " + url);
        }

        public static final class Content {
            static final String URL_SUFFIX = "/contents";

            private Content() {
            }

            public static GithubApiRequest<GithubResponsePage<GithubContent>> list(GithubServerPath server,
                                                                                   String username,
                                                                                   String repoName,
                                                                                   String path,
                                                                                   @Nullable String ref,
                                                                                   GithubRequestPagination pagination) {
                return list(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, "/" + path, urlQuery(it -> {
                    if (ref != null) {
                        it.param("ref", ref);
                    }
                    it.param(pagination);
                })));
            }

            public static GithubApiRequest<GithubContent> get(GithubServerPath server, String username, String repoName, String path) {
                return get(server, username, repoName, path, null);
            }

            public static GithubApiRequest<GithubContent> get(GithubServerPath server,
                                                              String username,
                                                              String repoName,
                                                              String path,
                                                              @Nullable String ref) {
                return get(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, "/" + path, urlQuery(it -> {
                    if (ref != null) {
                        it.param("ref", ref);
                    }
                })));
            }

            public static GithubApiRequest<GithubResponsePage<GithubContent>> list(String url) {
                return Get.jsonPage(url, GithubContent.class)
                    .withOperation(GithubApiRequestOperation.RestGetContents)
                    .withOperationName("get content");
            }

            public static GithubApiRequest<GithubContent> get(String url) {
                return Get.json(url, GithubContent.class)
                    .withOperation(GithubApiRequestOperation.RestGetContent)
                    .withOperationName("get file");
            }
        }

        // used externally
        public static final class Branches {
            static final String URL_SUFFIX = "/branches";

            private Branches() {
            }

            public static GithubApiPagesLoader.Request<GithubBranch> pages(GithubServerPath server, String username, String repoName) {
                return new GithubApiPagesLoader.Request<>(get(server, username, repoName), Branches::get);
            }

            public static GithubApiRequest<GithubResponsePage<GithubBranch>> get(GithubServerPath server, String username, String repoName) {
                return get(server, username, repoName, null);
            }

            public static GithubApiRequest<GithubResponsePage<GithubBranch>> get(GithubServerPath server,
                                                                                 String username,
                                                                                 String repoName,
                                                                                 @Nullable GithubRequestPagination pagination) {
                return get(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, paginationQuery(pagination)));
            }

            public static GithubApiRequest<GithubResponsePage<GithubBranch>> get(String url) {
                return Get.jsonPage(url, GithubBranch.class)
                    .withOperation(GithubApiRequestOperation.RestGetBranches)
                    .withOperationName("get branches");
            }
        }

        public static final class Tags {
            static final String URL_SUFFIX = "/tags";

            private Tags() {
            }

            public static GithubApiPagesLoader.Request<GithubTagInfo> pages(GithubServerPath server, String username, String repoName) {
                return new GithubApiPagesLoader.Request<>(get(server, username, repoName), Tags::get);
            }

            public static GithubApiRequest<GithubResponsePage<GithubTagInfo>> get(GithubServerPath server, String username, String repoName) {
                return get(server, username, repoName, null);
            }

            public static GithubApiRequest<GithubResponsePage<GithubTagInfo>> get(GithubServerPath server,
                                                                                  String username,
                                                                                  String repoName,
                                                                                  @Nullable GithubRequestPagination pagination) {
                return get(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, paginationQuery(pagination)));
            }

            public static GithubApiRequest<GithubResponsePage<GithubTagInfo>> get(String url) {
                return Get.jsonPage(url, GithubTagInfo.class)
                    .withOperation(GithubApiRequestOperation.RestGetTags)
                    .withOperationName("get tags");
            }
        }

        public static final class Commits {
            static final String URL_SUFFIX = "/commits";

            private Commits() {
            }

            public static GithubApiRequest<GHCommitsCompareResult> compare(GHRepositoryCoordinates repository, String refA, String refB) {
                return Get.json(getUrl(repository, "/compare/" + refA + "..." + refB), GHCommitsCompareResult.class)
                    .withOperation(GithubApiRequestOperation.RestGetRefComparison)
                    .withOperationName("compare refs");
            }

            public static GithubApiRequest<GithubResponsePage<GHCommitFile>> getDiffFiles(GHRepositoryCoordinates repository, String ref) {
                return getDiffFiles(getUrl(repository, URL_SUFFIX, "/" + ref));
            }

            public static GithubApiRequest<GithubResponsePage<GHCommitFile>> getDiffFiles(String url) {
                return new Get<GithubResponsePage<GHCommitFile>>(url) {
                    @Override
                    public GithubResponsePage<GHCommitFile> extractResult(GithubApiResponse response) throws IOException {
                        List<GHCommitFile> list = response.readBody(it -> GithubApiContentHelper.readJsonObject(it, GHCommitFiles.class)).getFiles();
                        String header = response.findHeader(LinkHttpHeaderValue.HEADER_NAME);
                        return new GithubResponsePage<>(list, header == null ? null : LinkHttpHeaderValue.parse(header));
                    }
                }
                    .withOperation(GithubApiRequestOperation.RestGetCommitDiffFiles)
                    .withOperationName("get files for ref");
            }

            public static GithubApiRequest<String> getDiff(GHRepositoryCoordinates repository, String refA, String refB) {
                return new Get<String>(getUrl(repository, "/compare/" + refA + "..." + refB), GithubApiContentHelper.V3_DIFF_JSON_MIME_TYPE) {
                    @Override
                    public String extractResult(GithubApiResponse response) throws IOException {
                        return response.handleBody(it -> {
                            try (Reader reader = new InputStreamReader(it, StandardCharsets.UTF_8)) {
                                StringBuilder builder = new StringBuilder();
                                char[] buffer = new char[8192];
                                int read;
                                while ((read = reader.read(buffer)) != -1) {
                                    builder.append(buffer, 0, read);
                                }
                                return builder.toString();
                            }
                        });
                    }
                }
                    .withOperation(GithubApiRequestOperation.RestGetCommitDiff)
                    .withOperationName("get diff between refs");
            }
        }

        public static final class Assignees {
            static final String URL_SUFFIX = "/assignees";

            private Assignees() {
            }

            public static GithubApiPagesLoader.Request<GithubUser> pages(GithubServerPath server, String username, String repoName) {
                return new GithubApiPagesLoader.Request<>(get(server, username, repoName), Assignees::get);
            }

            public static GithubApiRequest<GithubResponsePage<GithubUser>> get(GithubServerPath server, String username, String repoName) {
                return get(server, username, repoName, null);
            }

            public static GithubApiRequest<GithubResponsePage<GithubUser>> get(GithubServerPath server,
                                                                               String username,
                                                                               String repoName,
                                                                               @Nullable GithubRequestPagination pagination) {
                return get(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, paginationQuery(pagination)));
            }

            public static GithubApiRequest<GithubResponsePage<GithubUser>> get(String url) {
                return Get.jsonPage(url, GithubUser.class)
                    .withOperation(GithubApiRequestOperation.RestGetRepositoryAssignees)
                    .withOperationName("get assignees");
            }
        }

        public static final class Labels {
            static final String URL_SUFFIX = "/labels";

            private Labels() {
            }

            public static GithubApiPagesLoader.Request<GithubIssueLabel> pages(GithubServerPath server, String username, String repoName) {
                return new GithubApiPagesLoader.Request<>(get(server, username, repoName), Labels::get);
            }

            public static GithubApiRequest<GithubResponsePage<GithubIssueLabel>> get(GithubServerPath server, String username, String repoName) {
                return get(server, username, repoName, null);
            }

            public static GithubApiRequest<GithubResponsePage<GithubIssueLabel>> get(GithubServerPath server,
                                                                                     String username,
                                                                                     String repoName,
                                                                                     @Nullable GithubRequestPagination pagination) {
                return get(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, paginationQuery(pagination)));
            }

            public static GithubApiRequest<GithubResponsePage<GithubIssueLabel>> get(String url) {
                return Get.jsonPage(url, GithubIssueLabel.class)
                    .withOperation(GithubApiRequestOperation.RestGetRepositoryLabels)
                    .withOperationName("get labels");
            }
        }

        public static final class Collaborators {
            static final String URL_SUFFIX = "/collaborators";

            private Collaborators() {
            }

            public static GithubApiPagesLoader.Request<GithubUserWithPermissions> pages(GithubServerPath server, String username, String repoName) {
                return new GithubApiPagesLoader.Request<>(get(server, username, repoName), Collaborators::get);
            }

            public static GithubApiRequest<GithubResponsePage<GithubUserWithPermissions>> get(GithubServerPath server,
                                                                                              String username,
                                                                                              String repoName) {
                return get(server, username, repoName, null);
            }

            public static GithubApiRequest<GithubResponsePage<GithubUserWithPermissions>> get(GithubServerPath server,
                                                                                              String username,
                                                                                              String repoName,
                                                                                              @Nullable GithubRequestPagination pagination) {
                return get(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, paginationQuery(pagination)));
            }

            public static GithubApiRequest<GithubResponsePage<GithubUserWithPermissions>> get(String url) {
                return Get.jsonPage(url, GithubUserWithPermissions.class)
                    .withOperation(GithubApiRequestOperation.RestGetRepositoryCollaborators)
                    .withOperationName("get collaborators");
            }

            public static GithubApiRequest<Void> add(GithubServerPath server, String username, String repoName, String collaborator) {
                return Put.json(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, "/", collaborator), Void.class)
                    .withOperation(GithubApiRequestOperation.RestAddCollaboratorToRepository)
                    .withOperationName("add collaborator");
            }
        }

        public static final class Contributors {
            static final String URL_SUFFIX = "/contributors";

            private Contributors() {
            }

            public static GithubApiPagesLoader.Request<GithubUser> pages(GithubServerPath server, String username, String repoName) {
                return new GithubApiPagesLoader.Request<>(get(server, username, repoName, null), Contributors::get);
            }

            public static GithubApiRequest<GithubResponsePage<GithubUser>> get(GithubServerPath server,
                                                                               String username,
                                                                               String repoName,
                                                                               @Nullable GithubRequestPagination pagination) {
                return get(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, paginationQuery(pagination)));
            }

            private static GithubApiRequest<GithubResponsePage<GithubUser>> get(String url) {
                return Get.jsonPage(url, GithubUser.class)
                    .withOperation(GithubApiRequestOperation.RestGetRepositoryContributors)
                    .withOperationName("get contributors");
            }
        }

        public static final class Issues {
            static final String URL_SUFFIX = "/issues";

            private Issues() {
            }

            public static Post<GithubIssue> create(GithubServerPath server, String username, String repoName, String title) {
                return create(server, username, repoName, title, null, null, null, null);
            }

            public static Post<GithubIssue> create(GithubServerPath server,
                                                   String username,
                                                   String repoName,
                                                   String title,
                                                   @Nullable String body,
                                                   @Nullable Long milestone,
                                                   @Nullable List<String> labels,
                                                   @Nullable List<String> assignees) {
                Post<GithubIssue> request = Post.json(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX),
                    new GithubCreateIssueRequest(title, body, milestone, labels, assignees), GithubIssue.class);
                request.withOperation(GithubApiRequestOperation.RestCreateIssue);
                request.withOperationName("create issue");
                return request;
            }

            public static GithubApiPagesLoader.Request<GithubIssue> pages(GithubServerPath server,
                                                                         String username,
                                                                         String repoName,
                                                                         @Nullable String state,
                                                                         @Nullable String assignee) {
                return new GithubApiPagesLoader.Request<>(get(server, username, repoName, state, assignee, null), Issues::get);
            }

            public static GithubApiRequest<GithubResponsePage<GithubIssue>> get(GithubServerPath server,
                                                                                String username,
                                                                                String repoName,
                                                                                @Nullable String state,
                                                                                @Nullable String assignee,
                                                                                @Nullable GithubRequestPagination pagination) {
                return get(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, urlQuery(it -> {
                    it.param("state", state);
                    it.param("assignee", assignee);
                    it.param(pagination);
                })));
            }

            public static GithubApiRequest<GithubResponsePage<GithubIssue>> get(String url) {
                return Get.jsonPage(url, GithubIssue.class)
                    .withOperation(GithubApiRequestOperation.RestGetIssues)
                    .withOperationName("get issues in repository");
            }

            public static Get.Optional<GithubIssue> get(GithubServerPath server, String username, String repoName, String id) {
                Get.Optional<GithubIssue> request = Get.Optional.json(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, "/", id),
                    GithubIssue.class);
                request.withOperation(GithubApiRequestOperation.RestGetIssue);
                request.withOperationName("get issue");
                return request;
            }

            public static GithubApiRequest<GithubIssue> updateState(GithubServerPath server,
                                                                    String username,
                                                                    String repoName,
                                                                    String id,
                                                                    boolean open) {
                return Patch.json(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, "/", id),
                        new GithubChangeIssueStateRequest(open ? "open" : "closed"), GithubIssue.class)
                    .withOperation(GithubApiRequestOperation.RestUpdateIssueState)
                    .withOperationName("update issue state");
            }

            public static GithubApiRequest<GithubIssue> updateAssignees(GithubServerPath server,
                                                                        String username,
                                                                        String repoName,
                                                                        String id,
                                                                        Collection<String> assignees) {
                return Patch.json(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, "/", id),
                        new GithubAssigneesCollectionRequest(assignees), GithubIssue.class)
                    .withOperation(GithubApiRequestOperation.RestUpdateIssueAssignees)
                    .withOperationName("update assignees for issue");
            }

            public static final class Comments {
                static final String URL_SUFFIX = "/comments";

                private Comments() {
                }

                public static Post<GithubIssueCommentWithHtml> create(GHRepositoryCoordinates repository, long issueId, String body) {
                    return create(repository.getServerPath(), repository.getRepositoryPath().getOwner(),
                        repository.getRepositoryPath().getRepository(), String.valueOf(issueId), body);
                }

                public static Post<GithubIssueCommentWithHtml> create(GithubServerPath server,
                                                                      String username,
                                                                      String repoName,
                                                                      String issueId,
                                                                      String body) {
                    Post<GithubIssueCommentWithHtml> request = Post.json(
                        getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, Issues.URL_SUFFIX, "/", issueId, URL_SUFFIX),
                        new GithubCreateIssueCommentRequest(body),
                        GithubIssueCommentWithHtml.class,
                        GithubApiContentHelper.V3_HTML_JSON_MIME_TYPE);
                    request.withOperation(GithubApiRequestOperation.RestCreateIssueComment);
                    request.withOperationName("create comment for issue");
                    return request;
                }

                public static GithubApiPagesLoader.Request<GithubIssueCommentWithHtml> pages(GithubServerPath server,
                                                                                            String username,
                                                                                            String repoName,
                                                                                            String issueId) {
                    return new GithubApiPagesLoader.Request<>(get(server, username, repoName, issueId, null), Comments::get);
                }

                public static GithubApiPagesLoader.Request<GithubIssueCommentWithHtml> pages(String url) {
                    return new GithubApiPagesLoader.Request<>(get(url), Comments::get);
                }

                public static GithubApiRequest<GithubResponsePage<GithubIssueCommentWithHtml>> get(GithubServerPath server,
                                                                                                   String username,
                                                                                                   String repoName,
                                                                                                   String issueId,
                                                                                                   @Nullable GithubRequestPagination pagination) {
                    return get(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, Issues.URL_SUFFIX, "/", issueId, URL_SUFFIX,
                        urlQuery(it -> it.param(pagination))));
                }

                public static GithubApiRequest<GithubResponsePage<GithubIssueCommentWithHtml>> get(String url) {
                    return Get.jsonPage(url, GithubIssueCommentWithHtml.class, GithubApiContentHelper.V3_HTML_JSON_MIME_TYPE)
                        .withOperation(GithubApiRequestOperation.RestGetIssueComment)
                        .withOperationName("get comments for issue");
                }
            }

            public static final class Labels {
                static final String URL_SUFFIX = "/labels";

                private Labels() {
                }

                public static GithubApiRequest<List<GithubIssueLabel>> replace(GithubServerPath server,
                                                                               String username,
                                                                               String repoName,
                                                                               String issueId,
                                                                               Collection<String> labels) {
                    return Put.jsonList(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, Issues.URL_SUFFIX, "/", issueId, URL_SUFFIX),
                            new GithubLabelsCollectionRequest(labels), GithubIssueLabel.class)
                        .withOperation(GithubApiRequestOperation.RestUpdateIssueLabels)
                        .withOperationName("replace issue labels");
                }
            }
        }

        public static final class PullRequests {
            static final String URL_SUFFIX = "/pulls";

            private PullRequests() {
            }

            public static GithubApiRequest<GithubResponsePage<GHPullRequestRestIdOnly>> find(GHRepositoryCoordinates repository,
                                                                                             @Nullable GithubIssueState state,
                                                                                             @Nullable String baseRef,
                                                                                             @Nullable String headRef,
                                                                                             @Nullable GithubRequestPagination pagination) {
                return Get.jsonPage(getUrl(repository, URL_SUFFIX, urlQuery(it -> {
                        it.param("state", state == null ? null : state.toString());
                        it.param("base", baseRef);
                        it.param("head", headRef);
                        it.param(pagination);
                    })), GHPullRequestRestIdOnly.class)
                    .withOperation(GithubApiRequestOperation.RestGetPullRequests)
                    .withOperationName("find pull requests");
            }

            public static GithubApiRequest<Object> update(GithubServerPath serverPath,
                                                          String username,
                                                          String repoName,
                                                          long number,
                                                          @Nullable String title,
                                                          @Nullable String body,
                                                          @Nullable GithubIssueState state,
                                                          @Nullable String base,
                                                          @Nullable Boolean maintainerCanModify) {
                return Patch.json(getUrl(serverPath, Repos.URL_SUFFIX, "/" + username + "/" + repoName, URL_SUFFIX, "/" + number),
                        new GithubPullUpdateRequest(title, body, state, base, maintainerCanModify), Object.class)
                    .withOperation(GithubApiRequestOperation.RestUpdatePullRequest)
                    .withOperationName("update pull request " + number);
            }

            public static GithubApiRequest<Void> merge(GithubServerPath server,
                                                       GHRepositoryPath repoPath,
                                                       long number,
                                                       String commitSubject,
                                                       String commitBody,
                                                       String headSha) {
                return Put.json(getUrl(server, Repos.URL_SUFFIX, "/" + repoPath, URL_SUFFIX, "/" + number, "/merge"),
                        new GithubPullRequestMergeRequest(commitSubject, commitBody, headSha, GithubPullRequestMergeMethod.merge), Void.class)
                    .withOperation(GithubApiRequestOperation.RestMergePullRequest)
                    .withOperationName("merge pull request " + number);
            }

            public static GithubApiRequest<Void> squashMerge(GithubServerPath server,
                                                             GHRepositoryPath repoPath,
                                                             long number,
                                                             String commitSubject,
                                                             String commitBody,
                                                             String headSha) {
                return Put.json(getUrl(server, Repos.URL_SUFFIX, "/" + repoPath, URL_SUFFIX, "/" + number, "/merge"),
                        new GithubPullRequestMergeRequest(commitSubject, commitBody, headSha, GithubPullRequestMergeMethod.squash), Void.class)
                    .withOperation(GithubApiRequestOperation.RestSquashMergePullRequest)
                    .withOperationName("squash and merge pull request " + number);
            }

            public static GithubApiRequest<Void> rebaseMerge(GithubServerPath server, GHRepositoryPath repoPath, long number, String headSha) {
                return Put.json(getUrl(server, Repos.URL_SUFFIX, "/" + repoPath, URL_SUFFIX, "/" + number, "/merge"),
                        new GithubPullRequestMergeRebaseRequest(headSha), Void.class)
                    .withOperation(GithubApiRequestOperation.RestRebaseMergePullRequest)
                    .withOperationName("rebase and merge pull request " + number);
            }

            public static GithubApiRequest<GithubResponsePage<GHCommitFile>> getDiffFiles(GHRepositoryCoordinates repository, GHPRIdentifier id) {
                return getDiffFiles(getUrl(repository, URL_SUFFIX, "/" + id.number(), "/files"));
            }

            public static GithubApiRequest<GithubResponsePage<GHCommitFile>> getDiffFiles(String url) {
                return Get.jsonPage(url, GHCommitFile.class)
                    .withOperation(GithubApiRequestOperation.RestGetPullRequestDiffFiles)
                    .withOperationName("get files for pull request");
            }

            public static final class Reviewers {
                static final String URL_SUFFIX = "/requested_reviewers";

                private Reviewers() {
                }

                public static GithubApiRequest<Void> add(GithubServerPath server,
                                                         String username,
                                                         String repoName,
                                                         long number,
                                                         Collection<String> reviewers,
                                                         List<String> teamReviewers) {
                    return Post.json(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, PullRequests.URL_SUFFIX, "/" + number,
                            URL_SUFFIX), new GithubReviewersCollectionRequest(reviewers, teamReviewers), Void.class)
                        .withOperation(GithubApiRequestOperation.RestAddReviewerToPullRequest)
                        .withOperationName("add reviewer");
                }

                public static GithubApiRequest<Void> remove(GithubServerPath server,
                                                            String username,
                                                            String repoName,
                                                            long number,
                                                            Collection<String> reviewers,
                                                            List<String> teamReviewers) {
                    return Delete.json(getUrl(server, Repos.URL_SUFFIX, "/" + username + "/" + repoName, PullRequests.URL_SUFFIX, "/" + number,
                            URL_SUFFIX), new GithubReviewersCollectionRequest(reviewers, teamReviewers), Void.class)
                        .withOperation(GithubApiRequestOperation.RestRemoveReviewerFromPullRequest)
                        .withOperationName("remove reviewer");
                }
            }
        }
    }

    public static final class Gists {
        static final String URL_SUFFIX = "/gists";

        private Gists() {
        }

        public static GithubApiRequest<GithubGist> create(GithubServerPath server,
                                                          List<GithubGistRequest.FileContent> contents,
                                                          String description,
                                                          boolean isPublic) {
            return Post.json(getUrl(server, URL_SUFFIX), new GithubGistRequest(contents, description, isPublic), GithubGist.class)
                .withOperation(GithubApiRequestOperation.RestCreateGist)
                .withOperationName("create gist");
        }

        public static GithubApiRequest<@Nullable GithubGist> get(GithubServerPath server, String id) {
            return Get.Optional.json(getUrl(server, URL_SUFFIX, "/" + id), GithubGist.class)
                .withOperation(GithubApiRequestOperation.RestGetGist)
                .withOperationName("get gist " + id);
        }

        public static GithubApiRequest<Void> delete(GithubServerPath server, String id) {
            return Delete.json(getUrl(server, URL_SUFFIX, "/" + id), Void.class)
                .withOperation(GithubApiRequestOperation.RestDeleteGist)
                .withOperationName("delete gist " + id);
        }
    }

    public static final class Search {
        static final String URL_SUFFIX = "/search";

        private Search() {
        }

        public static final class Issues {
            static final String URL_SUFFIX = "/issues";

            private Issues() {
            }

            public static GithubApiPagesLoader.Request<GithubSearchedIssue> pages(GithubServerPath server,
                                                                                 @Nullable GHRepositoryPath repoPath,
                                                                                 @Nullable String state,
                                                                                 @Nullable String assignee,
                                                                                 @Nullable String query) {
                return new GithubApiPagesLoader.Request<>(get(server, repoPath, state, assignee, query, null), Issues::get);
            }

            public static GithubApiRequest<GithubResponsePage<GithubSearchedIssue>> get(GithubServerPath server,
                                                                                        @Nullable GHRepositoryPath repoPath,
                                                                                        @Nullable String state,
                                                                                        @Nullable String assignee,
                                                                                        @Nullable String query,
                                                                                        @Nullable GithubRequestPagination pagination) {
                return get(getUrl(server, Search.URL_SUFFIX, URL_SUFFIX, urlQuery(it -> {
                    it.param("q", searchQuery(q -> {
                        q.term(QualifierName.repo.createTerm(repoPath == null ? "" : repoPath.toString()));
                        q.term(QualifierName.state.createTerm(Objects.requireNonNullElse(state, "")));
                        q.term(QualifierName.assignee.createTerm(Objects.requireNonNullElse(assignee, "")));
                        q.query(query);
                    }));
                    it.param("advanced_search", "true");
                    it.param(pagination);
                })));
            }

            public static GithubApiRequest<GithubResponsePage<GithubSearchedIssue>> get(String url) {
                return Get.jsonSearchPage(url, GithubSearchedIssue.class)
                    .withOperation(GithubApiRequestOperation.RestSearchIssues)
                    .withOperationName("search issues in repository");
            }
        }
    }

    public static final class Emojis {
        static final String URL_SUFFIX = "/emojis";

        private Emojis() {
        }

        public static GithubApiRequest<Map<String, String>> loadNameToUrlMap(GithubServerPath server) {
            return new Get.JsonMap<String, String>(getUrl(server, URL_SUFFIX))
                .withOperation(GithubApiRequestOperation.RestGetEmojiMap)
                .withOperationName("load emoji name-to-url map");
        }

        public static GithubApiRequest<byte[]> loadImage(String url) {
            return getBytes(url)
                .withOperation(GithubApiRequestOperation.RestGetEmojiImage)
                .withOperationName("load emoji image");
        }
    }

    private static String getUrl(GithubServerPath server, String suffix) {
        return server.toApiUrl() + suffix;
    }

    private static String getUrl(GHRepositoryCoordinates repository, String... suffixes) {
        String[] all = new String[suffixes.length + 3];
        all[0] = Repos.URL_SUFFIX;
        all[1] = "/";
        all[2] = repository.getRepositoryPath().toString();
        System.arraycopy(suffixes, 0, all, 3, suffixes.length);
        return getUrl(repository.getServerPath(), all);
    }

    public static String getUrl(GithubServerPath server, String... suffixes) {
        StringBuilder builder = new StringBuilder(server.toApiUrl());
        for (String suffix : suffixes) {
            builder.append(suffix);
        }
        return builder.toString();
    }
}
