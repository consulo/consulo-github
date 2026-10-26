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
import consulo.application.progress.ProgressIndicator;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import git4idea.branch.GitBranchUiHandlerImpl;
import git4idea.branch.GitBranchWorker;
import git4idea.branch.GitBrancher;
import git4idea.commands.Git;
import git4idea.repo.GitRepository;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.util.List;

@ActionImpl(id = "Github.Sync.Fork.Merge")
public class GithubSyncForkMergeAction extends GithubSyncForkAction {
    private static final Logger LOG = GithubUtil.LOG;

    public GithubSyncForkMergeAction() {
        super(GithubLocalize.actionSyncForkMergeText(), GithubLocalize.actionSyncForkMergeDescription());
    }

    @Override
    protected LocalizeValue getProgressTitle() {
        return GithubLocalize.mergeProcess();
    }

    @Override
    protected void integrate(Project project, Git git, GitRepository repository, ProgressIndicator indicator, String upstreamBranch) {
        LOG.info("Merging upstream into current branch");
        indicator.setText(GithubLocalize.mergeProcessMergingUpstream(upstreamBranch));
        new GitBranchWorker(project, git, new GitBranchUiHandlerImpl(project, git, indicator))
            .merge(upstreamBranch, GitBrancher.DeleteOnMergeOption.NOTHING, List.of(repository));
    }
}
