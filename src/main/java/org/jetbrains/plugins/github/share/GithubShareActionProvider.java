// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.share;

import consulo.annotation.component.ExtensionImpl;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.versionControlSystem.ui.ShareProjectActionProvider;
import consulo.virtualFileSystem.VirtualFile;
import git4idea.repo.GitRepositoryManager;

@ExtensionImpl(order = "first")
public class GithubShareActionProvider implements ShareProjectActionProvider {
    @Override
    public LocalizeValue getHostServiceName() {
        return GithubLocalize.settingsConfigurableDisplayName();
    }

    @Override
    public AnAction getAction() {
        return ActionManager.getInstance().getAction("Github.Share");
    }

    @Override
    public boolean isApplicableForRoot(Project project, VirtualFile root) {
        return GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root) != null;
    }
}
