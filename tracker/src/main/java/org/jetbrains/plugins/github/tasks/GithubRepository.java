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
package org.jetbrains.plugins.github.tasks;

import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.component.ProcessCanceledException;
import consulo.github.icon.GitHubIconGroup;
import consulo.task.BaseRepository;
import consulo.task.Comment;
import consulo.task.Task;
import consulo.task.TaskRepository;
import consulo.task.TaskState;
import consulo.task.TaskType;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Tag;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubIssue;
import org.jetbrains.plugins.github.api.data.GithubIssueBase;
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml;
import org.jetbrains.plugins.github.api.data.GithubIssueLabel;
import org.jetbrains.plugins.github.api.data.GithubIssueState;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubJsonException;
import org.jetbrains.plugins.github.exceptions.GithubRateLimitExceededException;
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException;
import org.jetbrains.plugins.github.issue.GithubIssuesLoadingHelper;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Tag("GitHub")
public final class GithubRepository extends BaseRepository {
    private Pattern myPattern = Pattern.compile("($^)");
    private String myRepoAuthor = "";
    private String myRepoName = "";
    private String myUser = "";
    private boolean myAssignedIssuesOnly = false;

    public GithubRepository() {
    }

    GithubRepository(GithubRepository other) {
        super(other);
        setRepoName(other.myRepoName);
        setRepoAuthor(other.myRepoAuthor);
        setAssignedIssuesOnly(other.myAssignedIssuesOnly);
    }

    public GithubRepository(GithubRepositoryType type) {
        super(type);
        setUrl("https://" + GithubServerPath.DEFAULT_HOST);
    }

    @Override
    public CancellableConnection createCancellableConnection() {
        return new CancellableConnection() {
            private final GithubApiRequestExecutor myExecutor = getExecutor();
            private final ProgressIndicator myIndicator = new EmptyProgressIndicator();

            @Override
            protected void doTest() throws Exception {
                try {
                    myExecutor.execute(myIndicator, GithubApiRequests.Repos.get(getServer(), getRepoAuthor(), getRepoName()));
                }
                catch (ProcessCanceledException ignore) {
                }
            }

            @Override
            public void cancel() {
                myIndicator.cancel();
            }
        };
    }

    @Override
    public boolean isConfigured() {
        return super.isConfigured() &&
            !StringUtil.isEmptyOrSpaces(getRepoAuthor()) &&
            !StringUtil.isEmptyOrSpaces(getRepoName()) &&
            !StringUtil.isEmptyOrSpaces(getPassword());
    }

    @Override
    public String getPresentableName() {
        final String name = super.getPresentableName();
        return name +
            (!StringUtil.isEmpty(getRepoAuthor()) ? "/" + getRepoAuthor() : "") +
            (!StringUtil.isEmpty(getRepoName()) ? "/" + getRepoName() : "");
    }

    @Override
    public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed) throws Exception {
        try {
            return getIssues(query, offset + limit, withClosed);
        }
        catch (GithubRateLimitExceededException e) {
            return Task.EMPTY_ARRAY;
        }
        catch (GithubAuthenticationException | GithubStatusCodeException e) {
            throw new Exception(e.getMessage(), e); // Wrap to show error message
        }
        catch (GithubJsonException e) {
            throw new Exception("Bad response format", e);
        }
    }

    @Override
    public Task[] getIssues(@Nullable String query, int offset, int limit, boolean withClosed, ProgressIndicator cancelled)
        throws Exception {
        return getIssues(query, offset, limit, withClosed);
    }

    private Task[] getIssues(@Nullable String query, int max, boolean withClosed) throws Exception {
        GithubApiRequestExecutor executor = getExecutor();
        ProgressIndicator indicator = getProgressIndicator();
        GithubServerPath server = getServer();

        String assigned = null;
        if (myAssignedIssuesOnly) {
            if (StringUtil.isEmptyOrSpaces(myUser)) {
                myUser = executor.execute(indicator, GithubApiRequests.CurrentUser.get(server)).getLogin();
            }
            assigned = myUser;
        }

        List<? extends GithubIssueBase> issues;
        if (StringUtil.isEmptyOrSpaces(query)) {
            // search queries have way smaller request number limit
            issues = GithubIssuesLoadingHelper.load(executor, indicator, server, getRepoAuthor(), getRepoName(), withClosed, max, assigned);
        }
        else {
            issues = GithubIssuesLoadingHelper.search(executor, indicator, server, getRepoAuthor(), getRepoName(), withClosed, assigned, query);
        }
        List<Task> tasks = new ArrayList<>();

        for (GithubIssueBase issue : issues) {
            List<GithubIssueCommentWithHtml> comments = GithubApiPagesLoader
                .loadAll(executor, indicator, GithubApiRequests.Repos.Issues.Comments.pages(issue.getCommentsUrl()));
            tasks.add(createTask(issue, comments));
        }

        return tasks.toArray(Task.EMPTY_ARRAY);
    }

    public Task createTask(GithubIssueBase issue, List<GithubIssueCommentWithHtml> comments) {
        return new Task() {
            private final String myRepoName = getRepoName();
            private final Comment[] myComments =
                ContainerUtil.map2Array(comments, Comment.class, comment -> new GithubComment(comment.getCreatedAt(),
                    comment.getUser().getLogin(),
                    comment.getBodyHtml(),
                    comment.getUser().getAvatarUrl(),
                    comment.getUser().getHtmlUrl()));

            @Override
            public boolean isIssue() {
                return true;
            }

            @Override
            public String getIssueUrl() {
                return issue.getHtmlUrl();
            }

            @Override
            public String getId() {
                return myRepoName + "-" + issue.getNumber();
            }

            @Override
            public String getSummary() {
                return issue.getTitle();
            }

            @Override
            public String getDescription() {
                return issue.getBody();
            }

            @Override
            public Comment[] getComments() {
                return myComments;
            }

            @Override
            public Image getIcon() {
                return GitHubIconGroup.github();
            }

            @Override
            public TaskType getType() {
                List<GithubIssueLabel> labels = issue.getLabels();
                if (labels == null || labels.isEmpty()) {
                    return TaskType.OTHER;
                }

                // Map GitHub's default labels to TaskType
                for (GithubIssueLabel label : labels) {
                    String name = label.getName().toLowerCase(Locale.getDefault());
                    switch (name) {
                        case "bug":
                            return TaskType.BUG;
                        case "enhancement":
                            return TaskType.FEATURE;
                    }
                }

                return TaskType.OTHER;
            }

            @Override
            public Date getUpdated() {
                return issue.getUpdatedAt();
            }

            @Override
            public Date getCreated() {
                return issue.getCreatedAt();
            }

            @Override
            public boolean isClosed() {
                return issue.getState() == GithubIssueState.closed;
            }

            @Override
            public TaskRepository getRepository() {
                return GithubRepository.this;
            }

            @Override
            public String getPresentableName() {
                return getId() + ": " + getSummary();
            }
        };
    }

    @Override
    public @Nullable String extractId(String taskName) {
        Matcher matcher = myPattern.matcher(taskName);
        return matcher.find() ? matcher.group(1) : null;
    }

    @Override
    public @Nullable Task findTask(String id) throws Exception {
        final int index = id.lastIndexOf("-");
        if (index < 0) {
            return null;
        }
        final String numericId = id.substring(index + 1);
        GithubApiRequestExecutor executor = getExecutor();
        ProgressIndicator indicator = getProgressIndicator();
        GithubIssue issue = executor.execute(indicator,
            GithubApiRequests.Repos.Issues.get(getServer(), getRepoAuthor(), getRepoName(), numericId));
        if (issue == null) {
            return null;
        }
        List<GithubIssueCommentWithHtml> comments = GithubApiPagesLoader
            .loadAll(executor, indicator, GithubApiRequests.Repos.Issues.Comments.pages(issue.getCommentsUrl()));
        return createTask(issue, comments);
    }

    @Override
    public void setTaskState(Task task, TaskState state) throws Exception {
        boolean isOpen = switch (state) {
            case OPEN -> true;
            case RESOLVED -> false;
            default -> throw new IllegalStateException("Unknown state: " + state);
        };
        GithubApiRequestExecutor executor = getExecutor();
        GithubServerPath server = getServer();
        String repoAuthor = getRepoAuthor();
        String repoName = getRepoName();

        ProgressIndicator indicator = getProgressIndicator();
        executor.execute(indicator,
            GithubApiRequests.Repos.Issues.updateState(server, repoAuthor, repoName, task.getNumber(), isOpen));
    }

    @Override
    public BaseRepository clone() {
        return new GithubRepository(this);
    }

    public String getRepoName() {
        return myRepoName;
    }

    public void setRepoName(String repoName) {
        myRepoName = repoName;
        myPattern = Pattern.compile("(" + StringUtil.escapeToRegexp(repoName) + "\\-\\d+)");
    }

    public String getRepoAuthor() {
        return myRepoAuthor;
    }

    public void setRepoAuthor(String repoAuthor) {
        myRepoAuthor = repoAuthor;
    }

    public String getUser() {
        return myUser;
    }

    public void setUser(String user) {
        myUser = user;
    }

    /**
     * Stores access token
     */
    @Override
    public void setPassword(String password) {
        super.setPassword(password);
        setUser("");
    }

    public boolean isAssignedIssuesOnly() {
        return myAssignedIssuesOnly;
    }

    public void setAssignedIssuesOnly(boolean value) {
        myAssignedIssuesOnly = value;
    }

    private GithubApiRequestExecutor getExecutor() {
        return GithubApiRequestExecutorFactory.getInstance().create(myUseProxy, getServer(), getPassword());
    }

    private static ProgressIndicator getProgressIndicator() {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator == null) {
            indicator = new EmptyProgressIndicator();
        }
        return indicator;
    }

    private GithubServerPath getServer() {
        return GithubServerPath.from(getUrl());
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) {
            return false;
        }
        if (!(o instanceof GithubRepository that)) {
            return false;
        }

        if (!Objects.equals(getRepoAuthor(), that.getRepoAuthor())) {
            return false;
        }
        if (!Objects.equals(getRepoName(), that.getRepoName())) {
            return false;
        }
        if (!Comparing.equal(isAssignedIssuesOnly(), that.isAssignedIssuesOnly())) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return StringUtil.stringHashCode(getRepoName()) +
            31 * StringUtil.stringHashCode(getRepoAuthor());
    }

    @Override
    protected int getFeatures() {
        return super.getFeatures() | TaskRepository.STATE_UPDATING;
    }
}
