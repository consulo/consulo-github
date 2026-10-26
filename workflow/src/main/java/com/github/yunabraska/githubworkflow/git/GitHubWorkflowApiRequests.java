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
package com.github.yunabraska.githubworkflow.git;

import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestOperation;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubBranch;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.data.GithubResponsePage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * @author VISTALL
 * @since 2026-10-26
 */
public final class GitHubWorkflowApiRequests {
    private GitHubWorkflowApiRequests() {
    }

    public static GithubApiRequest<GitHubWorkflowContent> content(GithubServerPath server,
                                                                 String owner,
                                                                 String repo,
                                                                 String path,
                                                                 String ref,
                                                                 boolean workflow) {
        String url = repoUrl(server, owner, repo) + "/contents/" + encodePath(path) + "?ref=" + encode(ref);
        return GithubApiRequest.Get.json(url, GitHubWorkflowContent.class)
            .withOperation(workflow ? GithubApiRequestOperation.RestGetRemoteWorkflowFile : GithubApiRequestOperation.RestGetRemoteActionsFile)
            .withOperationName("get workflow metadata");
    }

    public static GithubApiRequest<GithubResponsePage<GithubBranch>> branches(GithubServerPath server, String owner, String repo, int perPage) {
        return GithubApiRequest.Get.jsonPage(repoUrl(server, owner, repo) + "/branches" + perPageQuery(perPage), GithubBranch.class)
            .withOperation(GithubApiRequestOperation.RestGetBranches)
            .withOperationName("get branches");
    }

    public static GithubApiRequest<GithubResponsePage<GithubBranch>> tags(GithubServerPath server, String owner, String repo, int perPage) {
        return GithubApiRequest.Get.jsonPage(repoUrl(server, owner, repo) + "/tags" + perPageQuery(perPage), GithubBranch.class)
            .withOperation(GithubApiRequestOperation.RestGetTags)
            .withOperationName("get tags");
    }

    public static GithubApiRequest<GithubResponsePage<GithubRepo>> userRepos(GithubServerPath server, String owner, int perPage) {
        return GithubApiRequest.Get.jsonPage(server.toApiUrl() + "/users/" + encode(owner) + "/repos" + perPageQuery(perPage), GithubRepo.class)
            .withOperation(GithubApiRequestOperation.RestGetRepositoriesForUser)
            .withOperationName("get user repositories");
    }

    public static GithubApiRequest<GithubResponsePage<GithubRepo>> orgRepos(GithubServerPath server, String owner, int perPage) {
        return GithubApiRequest.Get.jsonPage(server.toApiUrl() + "/orgs/" + encode(owner) + "/repos" + perPageQuery(perPage), GithubRepo.class)
            .withOperation(GithubApiRequestOperation.RestGetRepositoriesForOrganization)
            .withOperationName("get organization repositories");
    }

    private static String repoUrl(GithubServerPath server, String owner, String repo) {
        return server.toApiUrl() + "/repos/" + encode(owner) + "/" + encode(repo);
    }

    private static String perPageQuery(int perPage) {
        return perPage > 0 ? "?per_page=" + perPage : "";
    }

    private static String encodePath(String path) {
        return Arrays.stream(path.split("/")).map(GitHubWorkflowApiRequests::encode).collect(Collectors.joining("/"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
