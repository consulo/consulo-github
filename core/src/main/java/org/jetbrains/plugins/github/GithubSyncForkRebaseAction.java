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
package org.jetbrains.plugins.github;

import consulo.annotation.component.ActionImpl;
import consulo.application.AccessToken;
import consulo.application.progress.ProgressIndicator;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.versionControlSystem.distributed.DvcsUtil;
import consulo.virtualFileSystem.VirtualFile;
import git4idea.GitActivity;
import git4idea.commands.Git;
import git4idea.config.GitVcsSettings;
import git4idea.rebase.GitRebaser;
import git4idea.repo.GitRepository;
import git4idea.update.GitUpdateResult;
import git4idea.util.GitPreservingProcess;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.util.Collections;
import java.util.List;

@ActionImpl(id = "Github.Sync.Fork.Rebase")
public class GithubSyncForkRebaseAction extends GithubSyncForkAction {
    private static final Logger LOG = GithubUtil.LOG;

    public GithubSyncForkRebaseAction() {
        super(GithubLocalize.actionSyncForkRebaseText(), GithubLocalize.actionSyncForkRebaseDescription());
    }

    @Override
    protected LocalizeValue getProgressTitle() {
        return GithubLocalize.rebaseProcess();
    }

    @Override
    protected void integrate(Project project, Git git, GitRepository repository, ProgressIndicator indicator, String onto) {
        LOG.info("Rebasing current branch");
        indicator.setText(GithubLocalize.rebaseProcessRebasingBranchOnto(onto));
        try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(project, "Rebase", GitActivity.Rebase)) {
            List<VirtualFile> rootsToSave = Collections.singletonList(repository.getRoot());
            GitVcsSettings.UpdateChangesPolicy saveMethod = GitVcsSettings.getInstance(project).updateChangesPolicy();
            GitPreservingProcess process =
                new GitPreservingProcess(project, git, rootsToSave, GithubLocalize.rebaseProcessOperationTitle().get(), onto,
                    saveMethod, indicator,
                    () -> doRebaseCurrentBranch(project, git, repository, indicator, onto));
            process.execute();
        }
    }

    private static void doRebaseCurrentBranch(Project project, Git git, GitRepository repository, ProgressIndicator indicator, String onto) {
        VirtualFile root = repository.getRoot();

        GitUpdateResult result = new GitRebaser(project, git, indicator).rebase(root, Collections.singletonList(onto), null, null);
        repository.update();

        if (result == GitUpdateResult.NOTHING_TO_UPDATE ||
            result == GitUpdateResult.SUCCESS ||
            result == GitUpdateResult.SUCCESS_WITH_RESOLVED_CONFLICTS) {
            root.refresh(false, true);
            GithubNotifications.showInfo(project,
                GithubLocalize.rebaseProcessSuccess(),
                LocalizeValue.empty());
        }
    }
}
