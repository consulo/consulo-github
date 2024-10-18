/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.google.gson.*;
import consulo.http.HttpProxyManager;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubJsonException;
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubSslSupport;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.util.*;

/**
 * @author Kirill Likhodedov
 */
public class GithubApiUtil {
    public static final String DEFAULT_GITHUB_HOST = "github.com";

    private static final int CONNECTION_TIMEOUT = 5000;
    private static final String PER_PAGE = "per_page=100";
    private static final Logger LOG = GithubUtil.LOG;

    private static final Header ACCEPT_HTML_BODY_MARKUP = new Header("Accept", "application/vnd.github.v3.html+json");
    private static final Header ACCEPT_NEW_SEARCH_API = new Header("Accept", "application/vnd.github.preview");

    @Nonnull
    private static final Gson gson = initGson();

    private static Gson initGson() {
        GsonBuilder builder = new GsonBuilder();
        builder.setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        return builder.create();
    }

    private enum HttpVerb {
        GET,
        POST,
        DELETE,
        HEAD
    }

    @Nullable
    private static JsonElement postRequest(
        @Nonnull GithubAuthData auth,
        @Nonnull String path,
        @Nullable String requestBody,
        @Nonnull Header... headers
    ) throws IOException {
        return request(auth, path, requestBody, Arrays.asList(headers), HttpVerb.POST).getJsonElement();
    }

    @Nullable
    private static JsonElement deleteRequest(
        @Nonnull GithubAuthData auth,
        @Nonnull String path,
        @Nonnull Header... headers
    ) throws IOException {
        return request(auth, path, null, Arrays.asList(headers), HttpVerb.DELETE).getJsonElement();
    }

    @Nullable
    private static JsonElement getRequest(
        @Nonnull GithubAuthData auth,
        @Nonnull String path,
        @Nonnull Header... headers
    ) throws IOException {
        return request(auth, path, null, Arrays.asList(headers), HttpVerb.GET).getJsonElement();
    }

    @Nonnull
    private static ResponsePage request(
        @Nonnull GithubAuthData auth,
        @Nonnull String path,
        @Nullable String requestBody,
        @Nonnull Collection<Header> headers,
        @Nonnull HttpVerb verb
    ) throws IOException {
        HttpMethod method = null;
        try {
            String uri = GithubUrlUtil.getApiUrl(auth.getHost()) + path;
            method = doREST(auth, uri, requestBody, headers, verb);

            checkStatusCode(method);

            InputStream resp = method.getResponseBodyAsStream();
            if (resp == null) {
                return new ResponsePage();
            }

            JsonElement ret = parseResponse(resp);
            if (ret.isJsonNull()) {
                return new ResponsePage();
            }

            Header header = method.getResponseHeader("Link");
            if (header != null) {
                String value = header.getValue();
                int end = value.indexOf(">; rel=\"next\"");
                int begin = value.lastIndexOf('<', end);
                if (begin >= 0 && end >= 0) {
                    String newPath = GithubUrlUtil.removeProtocolPrefix(value.substring(begin + 1, end));
                    int index = newPath.indexOf('/');

                    return new ResponsePage(ret, newPath.substring(index));
                }
            }

            return new ResponsePage(ret);
        }
        finally {
            if (method != null) {
                method.releaseConnection();
            }
        }
    }

    @Nonnull
    private static HttpMethod doREST(
        @Nonnull final GithubAuthData auth,
        @Nonnull final String uri,
        @Nullable final String requestBody,
        @Nonnull final Collection<Header> headers,
        @Nonnull final HttpVerb verb
    ) throws IOException {
        HttpClient client = getHttpClient(auth.getBasicAuth(), auth.isUseProxy());
        return GithubSslSupport.getInstance().executeSelfSignedCertificateAwareRequest(client, uri, uri1 -> {
            HttpMethod method;
            switch (verb) {
                case POST:
                    method = new PostMethod(uri1);
                    if (requestBody != null) {
                        ((PostMethod)method).setRequestEntity(new StringRequestEntity(requestBody,
                            "application/json", "UTF-8"
                        ));
                    }
                    break;
                case GET:
                    method = new GetMethod(uri1);
                    break;
                case DELETE:
                    method = new DeleteMethod(uri1);
                    break;
                case HEAD:
                    method = new HeadMethod(uri1);
                    break;
                default:
                    throw new IllegalStateException("Wrong HttpVerb: unknown method: " + verb.toString());
            }
            GithubAuthData.TokenAuth tokenAuth = auth.getTokenAuth();
            if (tokenAuth != null) {
                method.addRequestHeader("Authorization", "token " + tokenAuth.getToken());
            }
            for (Header header : headers) {
                method.addRequestHeader(header);
            }
            return method;
        });
    }

    @Nonnull
    private static HttpClient getHttpClient(@Nullable GithubAuthData.BasicAuth basicAuth, boolean useProxy) {
        final HttpClient client = new HttpClient();
        HttpConnectionManagerParams params = client.getHttpConnectionManager().getParams();
        params.setConnectionTimeout(CONNECTION_TIMEOUT); //set connection timeout (how long it takes to connect to
        // remote host)
        params.setSoTimeout(CONNECTION_TIMEOUT); //set socket timeout (how long it takes to retrieve data from remote
        // host)

        client.getParams().setContentCharset("UTF-8");
        // Configure proxySettings if it is required
        final HttpProxyManager proxySettings = HttpProxyManager.getInstance();
        if (useProxy && proxySettings.isHttpProxyEnabled() && !StringUtil.isEmptyOrSpaces(proxySettings.getProxyHost())) {
            client.getHostConfiguration().setProxy(proxySettings.getProxyHost(), proxySettings.getProxyPort());
            if (proxySettings.isProxyAuthenticationEnabled()) {
                client.getState().setProxyCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(proxySettings.getProxyLogin(), proxySettings.getPlainProxyPassword())
                );
            }
        }
        if (basicAuth != null) {
            client.getParams().setCredentialCharset("UTF-8");
            client.getParams().setAuthenticationPreemptive(true);
            client.getState().setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(basicAuth.getLogin(), basicAuth.getPassword())
            );
        }
        return client;
    }

    private static void checkStatusCode(@Nonnull HttpMethod method) throws IOException {
        int code = method.getStatusCode();
        switch (code) {
            case HttpStatus.SC_OK:
            case HttpStatus.SC_CREATED:
            case HttpStatus.SC_ACCEPTED:
            case HttpStatus.SC_NO_CONTENT:
                return;
            case HttpStatus.SC_BAD_REQUEST:
            case HttpStatus.SC_UNAUTHORIZED:
            case HttpStatus.SC_PAYMENT_REQUIRED:
            case HttpStatus.SC_FORBIDDEN:
                throw new GithubAuthenticationException("Request response: " + getErrorMessage(method));
            default:
                throw new GithubStatusCodeException(code + ": " + getErrorMessage(method), code);
        }
    }

    @Nonnull
    private static String getErrorMessage(@Nonnull HttpMethod method) {
        try {
            InputStream resp = method.getResponseBodyAsStream();
            if (resp != null) {
                GithubErrorMessageRaw error = fromJson(parseResponse(resp), GithubErrorMessageRaw.class);
                return method.getStatusText() + " - " + error.getMessage();
            }
        }
        catch (IOException e) {
            LOG.info(e);
        }
        return method.getStatusText();
    }

    @Nonnull
    private static JsonElement parseResponse(@Nonnull InputStream githubResponse) throws IOException {
        try (Reader reader = new InputStreamReader(githubResponse)) {
            return new JsonParser().parse(reader);
        }
        catch (JsonSyntaxException jse) {
            throw new GithubJsonException("Couldn't parse GitHub response", jse);
        }
    }

    private static class ResponsePage {
        @Nullable
        private final JsonElement response;
        @Nullable
        private final String nextPage;

        public ResponsePage() {
            this(null, null);
        }

        public ResponsePage(@Nullable JsonElement response) {
            this(response, null);
        }

        public ResponsePage(@Nullable JsonElement response, @Nullable String next) {
            this.response = response;
            this.nextPage = next;
        }

        @Nullable
        public JsonElement getJsonElement() {
            return response;
        }

        @Nullable
        public String getNextPage() {
            return nextPage;
        }
    }

    /*
     * Json API
     */

    static <Raw extends DataConstructor, Result> Result createDataFromRaw(
        @Nonnull Raw rawObject,
        @Nonnull Class<Result> resultClass
    ) throws GithubJsonException {
        try {
            return rawObject.create(resultClass);
        }
        catch (Exception e) {
            throw new GithubJsonException("Json parse error", e);
        }
    }

    public static class PagedRequest<T> {
        @Nullable
        private String myNextPage;
        @Nonnull
        private final Collection<Header> myHeaders;
        @Nonnull
        private final Class<T> myResult;
        @Nonnull
        private final Class<? extends DataConstructor[]> myRawArray;

        @SuppressWarnings("NullableProblems")
        public PagedRequest(
            @Nonnull String path,
            @Nonnull Class<T> result,
            @Nonnull Class<? extends DataConstructor[]> rawArray,
            @Nonnull Header... headers
        ) {
            myNextPage = path;
            myResult = result;
            myRawArray = rawArray;
            myHeaders = Arrays.asList(headers);
        }

        @Nonnull
        public List<T> next(@Nonnull GithubAuthData auth) throws IOException {
            if (myNextPage == null) {
                throw new NoSuchElementException();
            }

            String page = myNextPage;
            myNextPage = null;

            ResponsePage response = request(auth, page, null, myHeaders, HttpVerb.GET);

            if (response.getJsonElement() == null) {
                throw new HttpException("Empty response");
            }

            if (!response.getJsonElement().isJsonArray()) {
                throw new GithubJsonException(
                    "Wrong json type: expected JsonArray",
                    new Exception(response.getJsonElement().toString())
                );
            }

            myNextPage = response.getNextPage();

            List<T> result = new ArrayList<>();
            for (DataConstructor raw : fromJson(response.getJsonElement().getAsJsonArray(), myRawArray)) {
                result.add(createDataFromRaw(raw, myResult));
            }
            return result;
        }

        public boolean hasNext() {
            return myNextPage != null;
        }

        @Nonnull
        public List<T> getAll(@Nonnull GithubAuthData auth) throws IOException {
            List<T> result = new ArrayList<>();
            while (hasNext()) {
                result.addAll(next(auth));
            }
            return result;
        }
    }

    @Nonnull
    private static <T> T fromJson(@Nullable JsonElement json, @Nonnull Class<T> classT) throws IOException {
        if (json == null) {
            throw new GithubJsonException("Unexpected empty response");
        }

        T res;
        try {
            //cast as workaround for early java 1.6 bug
            //noinspection RedundantCast
            res = (T)gson.fromJson(json, classT);
        }
        catch (ClassCastException | JsonParseException e) {
            throw new GithubJsonException("Parse exception while converting JSON to object " + classT.toString(), e);
        }
        if (res == null) {
            throw new GithubJsonException("Empty Json response");
        }
        return res;
    }

    /*
     * Github API
     */

    @Nonnull
    public static Collection<String> getTokenScopes(@Nonnull GithubAuthData auth) throws IOException {
        HttpMethod method = null;
        try {
            String uri = GithubUrlUtil.getApiUrl(auth.getHost()) + "/user";
            method = doREST(auth, uri, null, Collections.<Header>emptyList(), HttpVerb.HEAD);

            checkStatusCode(method);

            Header header = method.getResponseHeader("X-OAuth-Scopes");
            if (header == null) {
                throw new HttpException("No scopes header");
            }

            Collection<String> scopes = new ArrayList<>();
            for (HeaderElement elem : header.getElements()) {
                scopes.add(elem.getName());
            }
            return scopes;
        }
        finally {
            if (method != null) {
                method.releaseConnection();
            }
        }
    }

    @Nonnull
    public static String getScopedToken(
        @Nonnull GithubAuthData auth,
        @Nonnull Collection<String> scopes,
        @Nullable String note
    ) throws IOException {
        String path = "/authorizations";

        GithubAuthorizationRequest request = new GithubAuthorizationRequest(new ArrayList<>(scopes), note, null);
        GithubAuthorization response = createDataFromRaw(
            fromJson(
                postRequest(auth, path, gson.toJson(request)),
                GithubAuthorizationRaw.class
            ),
            GithubAuthorization.class
        );

        return response.getToken();
    }

    @Nonnull
    public static String getReadOnlyToken(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo,
        @Nullable String note
    ) throws IOException {
        GithubRepo repository = getDetailedRepoInfo(auth, user, repo);

        List<String> scopes = repository.isPrivate()
            ? Collections.singletonList("repo")
            : Collections.<String>emptyList();

        return getScopedToken(auth, scopes, note);
    }

    @Nonnull
    public static GithubUser getCurrentUser(@Nonnull GithubAuthData auth) throws IOException {
        JsonElement result = getRequest(auth, "/user");
        return createDataFromRaw(fromJson(result, GithubUserRaw.class), GithubUser.class);
    }

    @Nonnull
    public static GithubUserDetailed getCurrentUserDetailed(@Nonnull GithubAuthData auth) throws IOException {
        JsonElement result = getRequest(auth, "/user");
        return createDataFromRaw(fromJson(result, GithubUserRaw.class), GithubUserDetailed.class);
    }

    @Nonnull
    public static List<GithubRepo> getUserRepos(@Nonnull GithubAuthData auth) throws IOException {
        String path = "/user/repos?" + PER_PAGE;

        PagedRequest<GithubRepo> request = new PagedRequest<>(path, GithubRepo.class, GithubRepoRaw[].class);

        return request.getAll(auth);
    }

    @Nonnull
    public static List<GithubRepo> getUserRepos(@Nonnull GithubAuthData auth, @Nonnull String user) throws IOException {
        String path = "/users/" + user + "/repos?" + PER_PAGE;

        PagedRequest<GithubRepo> request = new PagedRequest<>(path, GithubRepo.class, GithubRepoRaw[].class);

        return request.getAll(auth);
    }

    @Nonnull
    public static List<GithubRepo> getAvailableRepos(@Nonnull GithubAuthData auth) throws IOException {
        List<GithubRepo> repos = new ArrayList<>();

        repos.addAll(getUserRepos(auth));

        String path = "/user/orgs?" + PER_PAGE;
        PagedRequest<GithubOrg> request = new PagedRequest<>(path, GithubOrg.class, GithubOrgRaw[].class);

        for (GithubOrg org : request.getAll(auth)) {
            String pathOrg = "/orgs/" + org.getLogin() + "/repos?type=member&" + PER_PAGE;
            PagedRequest<GithubRepo> requestOrg = new PagedRequest<>(pathOrg, GithubRepo.class, GithubRepoRaw[].class);
            repos.addAll(requestOrg.getAll(auth));
        }

        String pathWatched = "/user/subscriptions?" + PER_PAGE;
        PagedRequest<GithubRepo> requestWatched = new PagedRequest<>(pathWatched, GithubRepo.class, GithubRepoRaw[].class);
        repos.addAll(requestWatched.getAll(auth));

        return repos;
    }

    @Nonnull
    public static GithubRepoDetailed getDetailedRepoInfo(
        @Nonnull GithubAuthData auth,
        @Nonnull String owner,
        @Nonnull String name
    ) throws IOException {
        final String request = "/repos/" + owner + "/" + name;

        JsonElement jsonObject = getRequest(auth, request);

        return createDataFromRaw(fromJson(jsonObject, GithubRepoRaw.class), GithubRepoDetailed.class);
    }

    public static void deleteGithubRepository(
        @Nonnull GithubAuthData auth,
        @Nonnull String username,
        @Nonnull String repo
    ) throws IOException {
        String path = "/repos/" + username + "/" + repo;
        deleteRequest(auth, path);
    }

    public static void deleteGist(@Nonnull GithubAuthData auth, @Nonnull String id) throws IOException {
        String path = "/gists/" + id;
        deleteRequest(auth, path);
    }

    @Nonnull
    public static GithubGist getGist(@Nonnull GithubAuthData auth, @Nonnull String id) throws IOException {
        String path = "/gists/" + id;
        JsonElement result = getRequest(auth, path);

        return createDataFromRaw(fromJson(result, GithubGistRaw.class), GithubGist.class);
    }

    @Nonnull
    public static GithubGist createGist(
        @Nonnull GithubAuthData auth,
        @Nonnull List<GithubGist.FileContent> contents,
        @Nonnull String description,
        boolean isPrivate
    ) throws IOException {
        String request = gson.toJson(new GithubGistRequest(contents, description, !isPrivate));
        return createDataFromRaw(
            fromJson(postRequest(auth, "/gists", request), GithubGistRaw.class),
            GithubGist.class
        );
    }

    @Nonnull
    public static GithubPullRequest createPullRequest(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo,
        @Nonnull String title,
        @Nonnull String description,
        @Nonnull String from,
        @Nonnull String onto
    ) throws IOException {
        String request = gson.toJson(new GithubPullRequestRequest(title, description, from, onto));
        return createDataFromRaw(
            fromJson(
                postRequest(auth, "/repos/" + user + "/" + repo + "/pulls", request),
                GithubPullRequestRaw.class
            ),
            GithubPullRequest.class
        );
    }

    @Nonnull
    public static GithubRepo createRepo(
        @Nonnull GithubAuthData auth,
        @Nonnull String name,
        @Nonnull String description,
        boolean isPublic
    ) throws IOException {
        String path = "/user/repos";

        GithubRepoRequest request = new GithubRepoRequest(name, description, isPublic);

        return createDataFromRaw(
            fromJson(postRequest(auth, path, gson.toJson(request)), GithubRepoRaw.class),
            GithubRepo.class
        );
    }

    @Nonnull
    public static List<GithubIssue> getIssuesAssigned(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo,
        @Nullable String assigned
    ) throws IOException {
        String path;
        if (StringUtil.isEmptyOrSpaces(assigned)) {
            path = "/repos/" + user + "/" + repo + "/issues?" + PER_PAGE;
        }
        else {
            path = "/repos/" + user + "/" + repo + "/issues?assignee=" + assigned + "&" + PER_PAGE;
        }

        PagedRequest<GithubIssue> request = new PagedRequest<>(path, GithubIssue.class, GithubIssueRaw[].class);

        return request.getAll(auth);
    }

    @Nonnull
    public static List<GithubIssue> getIssuesQueried(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo,
        @Nullable String query
    ) throws IOException {
        query = URLEncoder.encode("@" + user + "/" + repo + " " + query, "UTF-8");
        String path = "/search/issues?q=" + query;

        //TODO: remove header after end of preview period. ~ october 2013
        //TODO: Use bodyHtml for issues - preview does not support this feature
        JsonElement result = getRequest(auth, path, ACCEPT_NEW_SEARCH_API);

        return createDataFromRaw(
            fromJson(result, GithubIssuesSearchResultRaw.class),
            GithubIssuesSearchResult.class
        ).getIssues();
    }

    @Nonnull
    public static GithubIssue getIssue(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo,
        @Nonnull String id
    ) throws IOException {
        String path = "/repos/" + user + "/" + repo + "/issues/" + id;

        JsonElement result = getRequest(auth, path);

        return createDataFromRaw(fromJson(result, GithubIssueRaw.class), GithubIssue.class);
    }

    @Nonnull
    public static List<GithubIssueComment> getIssueComments(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo,
        long id
    ) throws IOException {
        String path = "/repos/" + user + "/" + repo + "/issues/" + id + "/comments?" + PER_PAGE;

        PagedRequest<GithubIssueComment> request =
            new PagedRequest<>(path, GithubIssueComment.class, GithubIssueCommentRaw[].class, ACCEPT_HTML_BODY_MARKUP);

        return request.getAll(auth);
    }

    @Nonnull
    public static GithubCommitDetailed getCommit(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo,
        @Nonnull String sha
    ) throws IOException {
        String path = "/repos/" + user + "/" + repo + "/commits/" + sha;

        JsonElement result = getRequest(auth, path);
        return createDataFromRaw(fromJson(result, GithubCommitRaw.class), GithubCommitDetailed.class);
    }

    @Nonnull
    public static GithubPullRequest getPullRequest(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo,
        int id
    ) throws IOException {
        String path = "/repos/" + user + "/" + repo + "/pulls/" + id;
        return createDataFromRaw(
            fromJson(
                getRequest(auth, path, ACCEPT_HTML_BODY_MARKUP),
                GithubPullRequestRaw.class
            ),
            GithubPullRequest.class
        );
    }

    @Nonnull
    public static List<GithubPullRequest> getPullRequests(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo
    ) throws IOException {
        String path = "/repos/" + user + "/" + repo + "/pulls?" + PER_PAGE;

        PagedRequest<GithubPullRequest> request =
            new PagedRequest<>(path, GithubPullRequest.class, GithubPullRequestRaw[].class, ACCEPT_HTML_BODY_MARKUP);

        return request.getAll(auth);
    }

    @Nonnull
    public static PagedRequest<GithubPullRequest> getPullRequests(@Nonnull String user, @Nonnull String repo) {
        String path = "/repos/" + user + "/" + repo + "/pulls?" + PER_PAGE;

        return new PagedRequest<>(path, GithubPullRequest.class, GithubPullRequestRaw[].class, ACCEPT_HTML_BODY_MARKUP);
    }

    @Nonnull
    public static List<GithubCommit> getPullRequestCommits(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo,
        long id
    ) throws IOException {
        String path = "/repos/" + user + "/" + repo + "/pulls/" + id + "/commits?" + PER_PAGE;

        PagedRequest<GithubCommit> request = new PagedRequest<>(path, GithubCommit.class, GithubCommitRaw[].class);

        return request.getAll(auth);
    }

    @Nonnull
    public static List<GithubFile> getPullRequestFiles(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo,
        long id
    ) throws IOException {
        String path = "/repos/" + user + "/" + repo + "/pulls/" + id + "/files?" + PER_PAGE;

        PagedRequest<GithubFile> request = new PagedRequest<>(path, GithubFile.class, GithubFileRaw[].class);

        return request.getAll(auth);
    }

    @Nonnull
    public static List<GithubBranch> getRepoBranches(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo
    ) throws IOException {
        String path = "/repos/" + user + "/" + repo + "/branches?" + PER_PAGE;

        PagedRequest<GithubBranch> request = new PagedRequest<>(path, GithubBranch.class, GithubBranchRaw[].class);

        return request.getAll(auth);
    }

    @Nullable
    public static GithubRepo findForkByUser(
        @Nonnull GithubAuthData auth,
        @Nonnull String user,
        @Nonnull String repo,
        @Nonnull String forkUser
    ) throws IOException {
        String path = "/repos/" + user + "/" + repo + "/forks?" + PER_PAGE;

        PagedRequest<GithubRepo> request = new PagedRequest<>(path, GithubRepo.class, GithubRepoRaw[].class);

        while (request.hasNext()) {
            for (GithubRepo fork : request.next(auth)) {
                if (StringUtil.equalsIgnoreCase(fork.getUserName(), forkUser)) {
                    return fork;
                }
            }
        }

        return null;
    }
}