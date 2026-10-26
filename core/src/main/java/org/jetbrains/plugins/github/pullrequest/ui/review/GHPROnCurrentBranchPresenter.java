// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.review;

import consulo.platform.base.icon.PlatformIconGroup;
import consulo.annotation.component.ExtensionImpl;
import consulo.github.icon.GitHubIconGroup;
import consulo.github.localize.GithubLocalize;
import consulo.review.util.ComputedResult;
import consulo.ui.image.Image;
import consulo.util.lang.StringUtil;
import git4idea.branch.GitBranchSyncStatus;
import git4idea.branch.GitBranchUtil;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitBranchPopupActions;
import git4idea.ui.branch.GitCurrentBranchPresenter;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@ExtensionImpl
public class GHPROnCurrentBranchPresenter implements GitCurrentBranchPresenter {
    @Override
    public @Nullable Presentation getPresentation(GitRepository repository) {
        GHPRBranchWidgetViewModel vm = GHPROnCurrentBranchShowAction.getCurrentVm(repository.getProject());
        if (vm == null) {
            return null;
        }
        String currentBranchName = Objects.requireNonNull(StringUtil.escapeMnemonics(GitBranchUtil.getDisplayableBranchText(repository,
            branchName -> GitBranchPopupActions.truncateBranchName(branchName, repository.getProject()))));

        String fullBranchName = GitBranchUtil.getDisplayableBranchText(repository);

        GitBranchSyncStatus syncStatus = GitBranchSyncStatus.calcForCurrentBranch(repository);
        long number = vm.getId().number();
        if (vm.getUpdateRequired().get()) {
            return new PresentationData(
                PlatformIconGroup.generalWarning(),
                GithubLocalize.pullRequestOnBranch(number, currentBranchName),
                GithubLocalize.pullRequestOnBranchOutOfSync(number, fullBranchName),
                syncStatus
            );
        }

        ComputedResult<?> changesResult = vm.getDataLoadingState().get();
        if (changesResult.isInProgress()) {
            return new PresentationData(
                Image.busy(),
                GithubLocalize.pullRequestOnBranch(number, currentBranchName),
                GithubLocalize.pullRequestOnBranchLoading(number),
                syncStatus
            );
        }
        if (changesResult.isSuccess()) {
            return new PresentationData(
                GitHubIconGroup.github(),
                GithubLocalize.pullRequestOnBranch(number, currentBranchName),
                GithubLocalize.pullRequestOnBranchDescription(number, fullBranchName),
                syncStatus
            );
        }
        return new PresentationData(
            GitHubIconGroup.github(),
            GithubLocalize.pullRequestOnBranch(number, currentBranchName),
            GithubLocalize.pullRequestOnBranchError(number, fullBranchName),
            syncStatus
        );
    }
}
