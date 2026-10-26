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

import consulo.annotation.component.ExtensionImpl;
import consulo.github.icon.GitHubIconGroup;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.task.BaseRepositoryType;
import consulo.task.TaskRepository;
import consulo.task.TaskState;
import consulo.task.ui.TaskRepositoryEditor;
import consulo.ui.image.Image;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.util.EnumSet;
import java.util.function.Consumer;

@ExtensionImpl
public final class GithubRepositoryType extends BaseRepositoryType<GithubRepository> {
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
    public TaskRepository createRepository() {
        return new GithubRepository(this);
    }

    @Override
    public Class<GithubRepository> getRepositoryClass() {
        return GithubRepository.class;
    }

    @Override
    public TaskRepositoryEditor createEditor(GithubRepository repository,
                                             Project project,
                                             Consumer<GithubRepository> changeListener) {
        return new GithubRepositoryEditor(project, repository, changeListener);
    }

    @Override
    public EnumSet<TaskState> getPossibleTaskStates() {
        return EnumSet.of(TaskState.OPEN, TaskState.RESOLVED);
    }
}
