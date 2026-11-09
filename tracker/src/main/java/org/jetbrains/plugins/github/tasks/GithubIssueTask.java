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

import consulo.github.icon.GitHubIconGroup;
import consulo.task.Comment;
import consulo.task.Task;
import consulo.task.TaskRepository;
import consulo.task.TaskState;
import consulo.task.TaskType;
import consulo.ui.image.Image;
import org.jetbrains.plugins.github.api.data.GithubIssueBase;
import org.jetbrains.plugins.github.api.data.GithubIssueState;
import org.jetbrains.plugins.github.api.data.GithubUser;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.List;

/**
 * @author VISTALL
 * @since 2026-11-09
 */
public class GithubIssueTask extends Task {
    private final GithubTaskRepository myRepository;
    private final GithubIssueBase myIssue;

    public GithubIssueTask(GithubTaskRepository repository, GithubIssueBase issue) {
        myRepository = repository;
        myIssue = issue;
    }

    public GithubIssueBase getIssue() {
        return myIssue;
    }

    @Override
    public String getId() {
        return String.valueOf(myIssue.getNumber());
    }

    @Override
    public String getPresentableId() {
        return "#" + myIssue.getNumber();
    }

    @Override
    public String getNumber() {
        return getId();
    }

    @Override
    public @Nullable String getProject() {
        return myRepository.getOptions().repository;
    }

    @Override
    public String getSummary() {
        return myIssue.getTitle();
    }

    @Override
    public @Nullable String getDescription() {
        return myIssue.getBody();
    }

    @Override
    public Comment[] getComments() {
        return Comment.EMPTY_ARRAY;
    }

    @Override
    public Image getIcon() {
        return GitHubIconGroup.github();
    }

    @Override
    public TaskType getType() {
        return TaskType.BUG;
    }

    @Override
    public @Nullable Date getUpdated() {
        return myIssue.getUpdatedAt();
    }

    @Override
    public @Nullable Date getCreated() {
        return myIssue.getCreatedAt();
    }

    @Override
    public boolean isClosed() {
        return myIssue.getState() == GithubIssueState.closed;
    }

    @Override
    public boolean isIssue() {
        return true;
    }

    @Override
    public @Nullable String getIssueUrl() {
        return myIssue.getHtmlUrl();
    }

    @Override
    public TaskRepository getRepository() {
        return myRepository;
    }

    @Override
    public TaskState getState() {
        return isClosed() ? TaskState.RESOLVED : TaskState.OPEN;
    }

    @Override
    public @Nullable String getAssignee() {
        List<GithubUser> assignees = myIssue.getAssignees();
        if (assignees != null && !assignees.isEmpty()) {
            return assignees.get(0).getLogin();
        }
        GithubUser assignee = myIssue.getAssignee();
        return assignee == null ? null : assignee.getLogin();
    }
}
