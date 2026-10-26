// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.comment;

import consulo.application.concurrent.ApplicationConcurrency;
import consulo.project.Project;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.VcsException;
import consulo.versionControlSystem.change.VcsDirtyScopeManager;
import consulo.versionControlSystem.change.patch.AppliedPatch;
import consulo.versionControlSystem.change.patch.ApplyPatchStatus;
import consulo.versionControlSystem.change.patch.PatchApplyService;
import consulo.versionControlSystem.change.patch.TextFilePatch;
import consulo.versionControlSystem.util.VcsUtil;
import consulo.virtualFileSystem.VirtualFile;
import git4idea.GitUtil;
import git4idea.checkin.GitCheckinEnvironment;
import git4idea.index.GitIndexUtil;
import git4idea.repo.GitRepository;
import git4idea.util.GitFileUtils;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class GHSuggestedChangeApplier {
    private GHSuggestedChangeApplier() {
    }

    public static CompletableFuture<ApplyPatchStatus> applySuggestedChange(Project project,
                                                                          GitRepository repository,
                                                                          TextFilePatch suggestedChangePatch) {
        return CompletableFuture.supplyAsync(
            () -> project.getInstance(PatchApplyService.class).apply(repository.getRoot(), List.of(suggestedChangePatch), false),
            project.getUIAccess()
        ).thenCompose(Function.identity());
    }

    public static CompletableFuture<ApplyPatchStatus> commitSuggestedChanges(Project project,
                                                                            GitRepository repository,
                                                                            TextFilePatch suggestedChangePatch,
                                                                            String commitMessage) {
        // Apply patch
        return applySuggestedChange(project, repository, suggestedChangePatch).thenApplyAsync(patchStatus -> {
            if (patchStatus == ApplyPatchStatus.ALREADY_APPLIED) {
                return patchStatus;
            }
            try {
                return doCommitSuggestedChanges(project, repository, suggestedChangePatch, commitMessage);
            }
            catch (VcsException e) {
                GithubUtil.LOG.error("Failed to commit suggested change", e);
                return ApplyPatchStatus.FAILURE;
            }
        }, project.getApplication().getInstance(ApplicationConcurrency.class).executor());
    }

    private static ApplyPatchStatus doCommitSuggestedChanges(Project project,
                                                             GitRepository repository,
                                                             TextFilePatch suggestedChangePatch,
                                                             String commitMessage) throws VcsException {
        // Create suggested change revision
        String beforeName = suggestedChangePatch.getBeforeName();
        String afterName = suggestedChangePatch.getAfterName();
        FilePath beforeLocalFilePath = VcsUtil.getFilePath(repository.getRoot(), beforeName);
        FilePath afterLocalFilePath = VcsUtil.getFilePath(repository.getRoot(), afterName);

        byte[] bytes = GitFileUtils.getFileContent(project, repository.getRoot(), GitUtil.HEAD, beforeName);
        String revisionContent = new String(bytes, beforeLocalFilePath.getCharset(project));
        AppliedPatch appliedPatch = project.getInstance(PatchApplyService.class).applyToText(revisionContent, suggestedChangePatch.getHunks());
        if (appliedPatch == null || appliedPatch.getStatus() != ApplyPatchStatus.SUCCESS) {
            return appliedPatch != null ? appliedPatch.getStatus() : ApplyPatchStatus.FAILURE;
        }

        VirtualFile virtualFile = beforeLocalFilePath.getVirtualFile();
        if (virtualFile == null) {
            return ApplyPatchStatus.FAILURE;
        }
        byte[] fileContent = GitCheckinEnvironment.convertDocumentContentToBytesWithBOM(repository, appliedPatch.getPatchedText(), virtualFile);
        GitIndexUtil.StagedFile stagedFile = GitIndexUtil.listStaged(repository, beforeLocalFilePath);
        if (stagedFile == null) {
            return ApplyPatchStatus.FAILURE;
        }
        GitIndexUtil.write(repository, beforeLocalFilePath, fileContent, stagedFile.isExecutable());

        // Commit suggested change
        GitCheckinEnvironment.ChangedPath suggestedChangedPath = new GitCheckinEnvironment.ChangedPath(beforeLocalFilePath, afterLocalFilePath);

        List<VcsException> exceptions = new ArrayList<>(GitCheckinEnvironment.commitUsingIndex(project,
            repository,
            List.of(suggestedChangedPath),
            Set.of(suggestedChangedPath),
            commitMessage));

        if (!exceptions.isEmpty()) {
            List<String> messages = new ArrayList<>();
            for (VcsException exception : exceptions) {
                messages.addAll(List.of(exception.getMessages()));
            }
            GithubUtil.LOG.error("Failed to commit suggested change: " + String.join("\n", messages));
            return ApplyPatchStatus.FAILURE;
        }

        repository.update();
        VcsDirtyScopeManager.getInstance(project).dirDirtyRecursively(repository.getRoot());

        return ApplyPatchStatus.SUCCESS;
    }
}
