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
package org.jetbrains.plugins.github.util;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jspecify.annotations.Nullable;

/**
 * Utilities for Github-Git interactions
 */
@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class GithubGitHelper {
    private final GithubSettings mySettings;

    @Inject
    public GithubGitHelper(GithubSettings settings) {
        mySettings = settings;
    }

    public static GithubGitHelper getInstance() {
        return Application.get().getInstance(GithubGitHelper.class);
    }

    public String getRemoteUrl(GithubServerPath server, GHRepositoryPath repoPath) {
        return getRemoteUrl(server, repoPath.getOwner(), repoPath.getRepository());
    }

    public String getRemoteUrl(GithubServerPath server, String user, String repo) {
        String suffix = server.getSuffix();
        if (mySettings.isCloneGitUsingSsh()) {
            return "git@" + server.getHost() + ":" + (suffix == null ? "" : suffix.substring(1)) + "/" + user + "/" + repo + ".git";
        }
        else {
            return "https://" + server.getHost() + (suffix == null ? "" : suffix) + "/" + user + "/" + repo + ".git";
        }
    }

    public static @Nullable GitRepository findGitRepository(Project project) {
        return findGitRepository(project, null);
    }

    public static @Nullable GitRepository findGitRepository(Project project, @Nullable VirtualFile file) {
        GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
        VirtualFile target = file != null ? file : project.getBaseDir();
        if (target == null) {
            return null;
        }
        return manager.getRepositoryForFileQuick(target);
    }
}
