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
package com.github.yunabraska.githubworkflow.run;

import org.jetbrains.plugins.github.api.GithubApiContentHelper;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubApiResponse;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author VISTALL
 * @since 2026-10-26
 */
public final class WorkflowRunApiRequests {
    private static final String API_VERSION_HEADER = "X-GitHub-Api-Version";
    private static final String API_VERSION = "2026-03-10";

    private WorkflowRunApiRequests() {
    }

    public static GithubApiRequest<JsonNode> dispatch(GithubServerPath server,
                                                      String owner,
                                                      String repo,
                                                      String workflowId,
                                                      Map<String, Object> body) {
        String url = workflowUrl(server, owner, repo, workflowId) + "/dispatches";
        return versioned(server, new GithubApiRequest.Post<JsonNode>(GithubApiContentHelper.JSON_MIME_TYPE, url, GithubApiContentHelper.V3_JSON_MIME_TYPE) {
            @Override
            public String getBody() throws IOException {
                return GithubApiContentHelper.toJson(body);
            }

            @Override
            public JsonNode extractResult(GithubApiResponse response) throws IOException {
                return readJson(response);
            }
        }).withOperationName("dispatch workflow");
    }

    public static GithubApiRequest<JsonNode> run(GithubServerPath server, String owner, String repo, long runId) {
        return getJson(server, runUrl(server, owner, repo, runId)).withOperationName("get workflow run");
    }

    public static GithubApiRequest<JsonNode> workflowRuns(GithubServerPath server,
                                                          String owner,
                                                          String repo,
                                                          String workflowId,
                                                          String branch,
                                                          int perPage) {
        String url = workflowUrl(server, owner, repo, workflowId) + "/runs?branch=" + encode(branch) + "&event=workflow_dispatch&per_page=" + perPage;
        return getJson(server, url).withOperationName("get workflow runs");
    }

    public static GithubApiRequest<JsonNode> jobs(GithubServerPath server, String owner, String repo, long runId, int perPage) {
        return getJson(server, runUrl(server, owner, repo, runId) + "/jobs?per_page=" + perPage).withOperationName("get workflow jobs");
    }

    public static GithubApiRequest<String> jobLogs(GithubServerPath server, String owner, String repo, long jobId) {
        String url = repoUrl(server, owner, repo) + "/actions/jobs/" + jobId + "/logs";
        return versioned(server, new GithubApiRequest.Get<String>(url) {
            @Override
            public String extractResult(GithubApiResponse response) throws IOException {
                return response.readBody(WorkflowRunApiRequests::readText);
            }
        }).withOperationName("get workflow job logs");
    }

    public static GithubApiRequest<JsonNode> artifacts(GithubServerPath server, String owner, String repo, long runId, int perPage) {
        return getJson(server, runUrl(server, owner, repo, runId) + "/artifacts?per_page=" + perPage).withOperationName("get workflow artifacts");
    }

    public static GithubApiRequest<byte[]> artifactZip(GithubServerPath server, String owner, String repo, long artifactId) {
        String url = repoUrl(server, owner, repo) + "/actions/artifacts/" + artifactId + "/zip";
        return versioned(server, GithubApiRequests.getBytes(url)).withOperationName("download workflow artifact");
    }

    public static GithubApiRequest<JsonNode> cancel(GithubServerPath server, String owner, String repo, long runId) {
        return postEmpty(server, runUrl(server, owner, repo, runId) + "/cancel").withOperationName("cancel workflow run");
    }

    public static GithubApiRequest<JsonNode> rerun(GithubServerPath server, String owner, String repo, long runId, boolean failedOnly) {
        String url = runUrl(server, owner, repo, runId) + (failedOnly ? "/rerun-failed-jobs" : "/rerun");
        return postEmpty(server, url).withOperationName(failedOnly ? "rerun failed workflow jobs" : "rerun workflow run");
    }

    public static GithubApiRequest<JsonNode> delete(GithubServerPath server, String owner, String repo, long runId) {
        String url = runUrl(server, owner, repo, runId);
        return versioned(server, new GithubApiRequest.Delete<JsonNode>(GithubApiContentHelper.JSON_MIME_TYPE, url, GithubApiContentHelper.V3_JSON_MIME_TYPE) {
            @Override
            public @Nullable String getBody() {
                return null;
            }

            @Override
            public JsonNode extractResult(GithubApiResponse response) throws IOException {
                return readJson(response);
            }
        }).withOperationName("delete workflow run");
    }

    private static GithubApiRequest<JsonNode> getJson(GithubServerPath server, String url) {
        return versioned(server, new GithubApiRequest.Get<JsonNode>(url, GithubApiContentHelper.V3_JSON_MIME_TYPE) {
            @Override
            public JsonNode extractResult(GithubApiResponse response) throws IOException {
                return readJson(response);
            }
        });
    }

    private static GithubApiRequest<JsonNode> postEmpty(GithubServerPath server, String url) {
        return versioned(server, new GithubApiRequest.Post<JsonNode>(GithubApiContentHelper.JSON_MIME_TYPE, url, GithubApiContentHelper.V3_JSON_MIME_TYPE) {
            @Override
            public @Nullable String getBody() {
                return null;
            }

            @Override
            public JsonNode extractResult(GithubApiResponse response) throws IOException {
                return readJson(response);
            }
        });
    }

    private static <T> GithubApiRequest<T> versioned(GithubServerPath server, GithubApiRequest<T> request) {
        if (server.isGithubDotCom() || server.isGheDataResidency()) {
            request.getAdditionalHeaders().put(API_VERSION_HEADER, API_VERSION);
        }
        return request;
    }

    private static JsonNode readJson(GithubApiResponse response) throws IOException {
        String text = response.readBody(WorkflowRunApiRequests::readText);
        if (text.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        return GithubApiContentHelper.fromJson(text, JsonNode.class);
    }

    private static String readText(Reader reader) throws IOException {
        StringWriter writer = new StringWriter();
        reader.transferTo(writer);
        return writer.toString();
    }

    private static String repoUrl(GithubServerPath server, String owner, String repo) {
        return server.toApiUrl() + "/repos/" + encode(owner) + "/" + encode(repo);
    }

    private static String runUrl(GithubServerPath server, String owner, String repo, long runId) {
        return repoUrl(server, owner, repo) + "/actions/runs/" + runId;
    }

    private static String workflowUrl(GithubServerPath server, String owner, String repo, String workflowId) {
        return repoUrl(server, owner, repo) + "/actions/workflows/" + encode(workflowId);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
