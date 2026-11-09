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
import consulo.accountManagement.AccountManager;
import consulo.annotation.component.ExtensionImpl;
import consulo.github.icon.GitHubIconGroup;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.task.TaskRepository;
import consulo.task.TaskRepositoryType;
import consulo.task.TaskState;
import consulo.task.ui.BaseRepositoryEditor;
import consulo.task.ui.TaskRepositoryEditor;
import consulo.ui.CheckBox;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.exceptions.GithubParseException;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jetbrains.plugins.github.util.GithubUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Issues of the GitHub repositories the project's git remotes point at - one tracker per repository and account of
 * the same server, found the way the pull request view finds them.
 *
 * @author VISTALL
 * @since 2026-11-09
 */
@ExtensionImpl
public final class GithubRepositoryType extends TaskRepositoryType<GithubTaskRepositoryOptions> {
    @Override
    public String getId() {
        return "GitHub";
    }

    @Override
    public LocalizeValue getPresentableName() {
        return LocalizeValue.of(GithubUtil.SERVICE_DISPLAY_NAME);
    }

    @Override
    public Image getIcon() {
        return GitHubIconGroup.github();
    }

    @Override
    public String getAccountTypeId() {
        return GithubAccountType.ID;
    }

    @Override
    public GithubTaskRepositoryOptions createOptions() {
        return new GithubTaskRepositoryOptions();
    }

    @Override
    public TaskRepository createRepository(Project project, GithubTaskRepositoryOptions options) {
        return new GithubTaskRepository(this, project, options);
    }

    @Override
    public EnumSet<TaskState> getPossibleTaskStates() {
        return EnumSet.of(TaskState.OPEN, TaskState.RESOLVED);
    }

    @Override
    public List<GithubTaskRepositoryOptions> detect(Project project, Account account) {
        GithubServerPath server = serverOf(project.getApplication().getInstance(AccountManager.class).getServerUrl(account));

        Set<GHRepositoryCoordinates> repositories = new LinkedHashSet<>();
        for (GHGitRepositoryMapping mapping : GHHostedRepositoriesManager.getInstance(project).getKnownRepositoriesState().get()) {
            GHRepositoryCoordinates repository = mapping.getRepository();
            if (repository.getServerPath().equals(server)) {
                repositories.add(repository);
            }
        }

        List<GithubTaskRepositoryOptions> result = new ArrayList<>();
        for (GHRepositoryCoordinates repository : repositories) {
            GithubTaskRepositoryOptions options = createOptions();
            options.owner = repository.getRepositoryPath().getOwner();
            options.repository = repository.getRepositoryPath().getRepository();
            result.add(options);
        }
        return result;
    }

    @Override
    public String getDetectionKey(GithubTaskRepositoryOptions options) {
        return super.getDetectionKey(options) + ":" + options.owner + "/" + options.repository;
    }

    static GithubServerPath serverOf(@Nullable String serverUrl) {
        if (StringUtil.isEmpty(serverUrl)) {
            return GithubServerPath.DEFAULT_SERVER;
        }
        try {
            return GithubServerPath.from(serverUrl);
        }
        catch (GithubParseException e) {
            return GithubServerPath.DEFAULT_SERVER;
        }
    }

    @Override
    public TaskRepositoryEditor createEditor(GithubTaskRepositoryOptions options,
                                             Project project,
                                             Consumer<GithubTaskRepositoryOptions> changeListener) {
        return new BaseRepositoryEditor<>(project, this, options, changeListener) {
            @RequiredUIAccess
            @Override
            protected void addCustomRows(FormBuilder builder) {
                TextBox ownerBox = TextBox.create(myOptions.owner);
                ownerBox.addValueListener(event -> {
                    myOptions.owner = StringUtil.notNullize(event.getValue()).trim();
                    fireChanged();
                });
                builder.addLabeled(GithubLocalize.trackerOwner(), ownerBox);

                TextBox repositoryBox = TextBox.create(myOptions.repository);
                repositoryBox.addValueListener(event -> {
                    myOptions.repository = StringUtil.notNullize(event.getValue()).trim();
                    fireChanged();
                });
                builder.addLabeled(GithubLocalize.trackerRepository(), repositoryBox);

                CheckBox assignedBox = CheckBox.create(GithubLocalize.trackerAssignedOnly(), myOptions.assignedIssuesOnly);
                assignedBox.addValueListener(event -> {
                    myOptions.assignedIssuesOnly = event.getValue();
                    fireChanged();
                });
                builder.addLabeled(LocalizeValue.empty(), assignedBox);
            }
        };
    }
}
