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

import consulo.accountManagement.Account;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.task.CustomTaskState;
import consulo.task.Task;
import consulo.task.TaskRepository;
import consulo.task.TaskState;
import consulo.task.issue.IssueAssigneeSupport;
import consulo.task.issue.IssueCapability;
import consulo.task.issue.IssueComment;
import consulo.task.issue.IssueCommentSupport;
import consulo.task.issue.IssueDetails;
import consulo.task.issue.IssueField;
import consulo.task.issue.IssuePage;
import consulo.task.issue.IssueQuery;
import consulo.task.issue.IssueStateSupport;
import consulo.task.issue.IssueTracker;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubIssueBase;
import org.jetbrains.plugins.github.api.data.GithubIssueCommentWithHtml;
import org.jetbrains.plugins.github.api.data.GithubIssueLabel;
import org.jetbrains.plugins.github.api.data.GithubIssueState;
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue;
import org.jetbrains.plugins.github.api.data.GithubUser;
import org.jetbrains.plugins.github.api.data.request.GithubCreateIssueCommentRequest;
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author VISTALL
 * @since 2026-11-09
 */
public class GithubTaskRepository extends TaskRepository {
    private static final Pattern ISSUE_NUMBER = Pattern.compile("#(\\d+)");

    private static final String OPEN = "open";
    private static final String CLOSED = "closed";

    private final Tracker myTracker = new Tracker();

    public GithubTaskRepository(GithubRepositoryType type, Project project, GithubTaskRepositoryOptions options) {
        super(type, project, options);
    }

    @Override
    public GithubTaskRepositoryOptions getOptions() {
        return (GithubTaskRepositoryOptions) super.getOptions();
    }

    @Override
    public IssueTracker getIssueTracker() {
        return myTracker;
    }

    @Override
    public String getPresentableName() {
        GithubTaskRepositoryOptions options = getOptions();
        return options.owner + "/" + options.repository;
    }

    @Override
    public boolean isConfigured() {
        GithubTaskRepositoryOptions options = getOptions();
        return super.isConfigured() && !options.owner.isEmpty() && !options.repository.isEmpty();
    }

    @Override
    public @Nullable String extractId(String taskName) {
        Matcher matcher = ISSUE_NUMBER.matcher(taskName);
        return matcher.find() ? matcher.group(1) : null;
    }

    private GithubServerPath server() {
        return GithubRepositoryType.serverOf(getServerUrl());
    }

    private <T> CompletableFuture<T> execute(GithubApiRequest<T> request) {
        Account account = getAccount();
        if (account == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not logged in to " + getServerUrl()));
        }
        GithubApiRequestExecutor executor = GithubApiRequestExecutorFactory.getInstance().create(account, server());
        return executor.execute(request);
    }

    private String commentUrl(IssueComment comment) {
        GithubTaskRepositoryOptions options = getOptions();
        return server().toApiUrl() + "/repos/" + options.owner + "/" + options.repository + "/issues/comments/" + comment.getId();
    }

    private class Tracker implements IssueTracker, IssueStateSupport, IssueCommentSupport, IssueAssigneeSupport {
        @Override
        public Set<IssueCapability> getCapabilities() {
            return EnumSet.of(IssueCapability.NATIVE_SEARCH, IssueCapability.STATES, IssueCapability.COMMENTS, IssueCapability.ASSIGN);
        }

        @Override
        public CompletableFuture<IssuePage> search(IssueQuery query, int offset, int limit) {
            GithubTaskRepositoryOptions options = getOptions();
            GHRepositoryPath path = new GHRepositoryPath(options.owner, options.repository);
            String text = "is:issue " + StringUtil.notNullize(query.getText()).trim();
            String state = query.isIncludeClosed() ? null : OPEN;
            int pageSize = Math.max(1, Math.min(limit, GithubRequestPagination.DEFAULT_PAGE_SIZE));
            GithubRequestPagination pagination = new GithubRequestPagination(offset / pageSize + 1, pageSize);

            CompletableFuture<@Nullable String> assignee = options.assignedIssuesOnly
                ? execute(GithubApiRequests.CurrentUser.get(server())).thenApply(GithubUser::getLogin)
                : CompletableFuture.completedFuture(null);

            return assignee
                .thenCompose(login -> execute(GithubApiRequests.Search.Issues.get(server(), path, state, login, text, pagination)))
                .thenApply(page -> {
                    List<Task> tasks = new ArrayList<>();
                    for (GithubSearchedIssue issue : page.getItems()) {
                        if (issue.getPullRequestLinks() == null) {
                            tasks.add(new GithubIssueTask(GithubTaskRepository.this, issue));
                        }
                    }
                    return new IssuePage(tasks, page.getNextLink() != null);
                });
        }

        @Override
        public CompletableFuture<@Nullable Task> findIssue(String id) {
            String number = StringUtil.trimStart(id.trim(), "#");
            GithubTaskRepositoryOptions options = getOptions();
            return execute(GithubApiRequests.Repos.Issues.get(server(), options.owner, options.repository, number))
                .thenApply(issue -> issue == null ? null : new GithubIssueTask(GithubTaskRepository.this, issue));
        }

        @Override
        public CompletableFuture<IssueDetails> loadDetails(Task task) {
            GithubIssueBase issue = ((GithubIssueTask) task).getIssue();
            return execute(GithubApiRequests.Repos.Issues.Comments.get(issue.getCommentsUrl())).thenApply(page -> {
                List<IssueComment> comments = new ArrayList<>();
                for (GithubIssueCommentWithHtml comment : page.getItems()) {
                    GithubUser user = comment.getUser();
                    comments.add(new IssueComment(
                        String.valueOf(comment.getId()),
                        user == null ? null : user.getLogin(),
                        comment.getCreatedAt(),
                        StringUtil.notNullize(StringUtil.unescapeXml(StringUtil.stripHtml(StringUtil.notNullize(comment.getBodyHtml()), true)))
                    ));
                }
                return new IssueDetails(task, fields(issue), comments, List.of(), List.of(), List.of(), List.of());
            });
        }

        private List<IssueField> fields(GithubIssueBase issue) {
            List<IssueField> fields = new ArrayList<>();
            add(fields, GithubLocalize.trackerFieldAuthor(), issue.getUser() == null ? null : issue.getUser().getLogin());

            List<String> assignees = new ArrayList<>();
            List<GithubUser> users = issue.getAssignees();
            if (users != null) {
                for (GithubUser user : users) {
                    assignees.add(user.getLogin());
                }
            }
            add(fields, GithubLocalize.trackerFieldAssignees(), String.join(", ", assignees));

            List<String> labels = new ArrayList<>();
            List<GithubIssueLabel> issueLabels = issue.getLabels();
            if (issueLabels != null) {
                for (GithubIssueLabel label : issueLabels) {
                    labels.add(label.getName());
                }
            }
            add(fields, GithubLocalize.trackerFieldLabels(), String.join(", ", labels));
            return fields;
        }

        private void add(List<IssueField> fields, LocalizeValue name, @Nullable String value) {
            if (!StringUtil.isEmptyOrSpaces(value)) {
                fields.add(new IssueField(name, value));
            }
        }

        @Override
        public CompletableFuture<Set<CustomTaskState>> getAvailableStates(Task task) {
            Set<CustomTaskState> states = new LinkedHashSet<>();
            if (task.isClosed()) {
                states.add(new CustomTaskState(OPEN, GithubLocalize.trackerStateOpen().get()));
            }
            else {
                states.add(new CustomTaskState(CLOSED, GithubLocalize.trackerStateClosed().get()));
            }
            return CompletableFuture.completedFuture(states);
        }

        @Override
        public CompletableFuture<?> setState(Task task, CustomTaskState state) {
            boolean open = OPEN.equals(state.getId()) || state.asPredefined() == TaskState.OPEN;
            GithubTaskRepositoryOptions options = getOptions();
            return execute(GithubApiRequests.Repos.Issues.updateState(server(), options.owner, options.repository, task.getId(), open));
        }

        @Override
        public CompletableFuture<?> addComment(Task task, String text) {
            GithubTaskRepositoryOptions options = getOptions();
            return execute(GithubApiRequests.Repos.Issues.Comments.create(server(), options.owner, options.repository, task.getId(), text));
        }

        @Override
        public CompletableFuture<?> editComment(Task task, IssueComment comment, String text) {
            return execute(GithubApiRequest.Patch.json(commentUrl(comment), new GithubCreateIssueCommentRequest(text), Object.class));
        }

        @Override
        public CompletableFuture<?> deleteComment(Task task, IssueComment comment) {
            return execute(GithubApiRequest.Delete.json(commentUrl(comment), Void.class));
        }

        @Override
        public CompletableFuture<List<String>> getAssignableUsers(Task task) {
            GithubTaskRepositoryOptions options = getOptions();
            return execute(GithubApiRequests.Repos.Assignees.get(server(), options.owner, options.repository, new GithubRequestPagination(1, 100)))
                .thenApply(page -> {
                    List<String> logins = new ArrayList<>();
                    for (GithubUser user : page.getItems()) {
                        logins.add(user.getLogin());
                    }
                    return logins;
                });
        }

        @Override
        public CompletableFuture<?> assign(Task task, @Nullable String user) {
            GithubTaskRepositoryOptions options = getOptions();
            return execute(GithubApiRequests.Repos.Issues.updateAssignees(server(), options.owner, options.repository, task.getId(),
                user == null ? List.of() : List.of(user)));
        }
    }
}
