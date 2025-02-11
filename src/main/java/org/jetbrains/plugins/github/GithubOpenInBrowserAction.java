/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.github.icon.GitHubIconGroup;
import consulo.language.editor.PlatformDataKeys;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.Presentation;
import consulo.versionControlSystem.change.Change;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.virtualFileSystem.VirtualFile;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author oleg
 * @since 2010-12-10
 */
@ActionImpl(id = "Github.Open.In.Browser", parents = {
    @ActionParentRef(@ActionRef(id = "RevealGroup")),
})
public class GithubOpenInBrowserAction extends DumbAwareAction {
    public static final String CANNOT_OPEN_IN_BROWSER = "Cannot open in browser";

    public GithubOpenInBrowserAction() {
        super("GitHub", "Open corresponding link in browser", GitHubIconGroup.github_icon());
    }

    @Override
    @RequiredUIAccess
    public void update(final AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        Presentation presentation = e.getPresentation();
        if (project == null || project.isDefault() || virtualFile == null) {
            presentation.setEnabledAndVisible(false);
            return;
        }
        GitRepositoryManager manager = GitUtil.getRepositoryManager(project);

        final GitRepository gitRepository = manager.getRepositoryForFile(virtualFile);
        if (gitRepository == null) {
            presentation.setEnabledAndVisible(false);
            return;
        }

        if (!GithubUtil.isRepositoryOnGitHub(gitRepository)) {
            presentation.setEnabledAndVisible(false);
            return;
        }

        ChangeListManager changeListManager = ChangeListManager.getInstance(project);
        if (changeListManager.isUnversioned(virtualFile)) {
            presentation.setEnabled(false);
            presentation.setVisible(true);
            return;
        }

        Change change = changeListManager.getChange(virtualFile);
        if (change != null && change.getType() == Change.Type.NEW) {
            presentation.setEnabled(false);
            presentation.setVisible(true);
            return;
        }

        presentation.setEnabledAndVisible(true);
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(final AnActionEvent e) {
        final Project project = e.getData(PlatformDataKeys.PROJECT);
        final VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        final Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (virtualFile == null || project == null || project.isDisposed()) {
            return;
        }

        String urlToOpen = getGithubUrl(project, virtualFile, editor, false);
        if (urlToOpen != null) {
            Platform.current().openInBrowser(urlToOpen);
        }
    }

    @Nullable
    public static String getGithubUrl(
        @Nonnull Project project,
        @Nonnull VirtualFile virtualFile,
        @Nullable Editor editor,
        boolean quiet
    ) {

        GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
        final GitRepository repository = manager.getRepositoryForFile(virtualFile);
        if (repository == null) {
            if (!quiet) {
                StringBuilder details = new StringBuilder("file: " + virtualFile.getPresentableUrl() + "; Git " +
                    "repositories: ");
                for (GitRepository repo : manager.getRepositories()) {
                    details.append(repo.getPresentableUrl()).append("; ");
                }
                showError(project, "Can't find git repository", details.toString(), quiet);
            }
            return null;
        }

        final String githubRemoteUrl = GithubUtil.findGithubRemoteUrl(repository);
        if (githubRemoteUrl == null) {
            showError(project, "Can't find github remote", null, quiet);
            return null;
        }

        final String rootPath = repository.getRoot().getPath();
        final String path = virtualFile.getPath();
        if (!path.startsWith(rootPath)) {
            showError(project, "File is not under repository root", "Root: " + rootPath + ", file: " + path, quiet);
            return null;
        }

        String branch = getBranchNameOnRemote(project, repository, quiet);
        if (branch == null) {
            return null;
        }

        String relativePath = path.substring(rootPath.length());
        String urlToOpen = makeUrlToOpen(editor, relativePath, branch, githubRemoteUrl);
        if (urlToOpen == null) {
            showError(project, "Can't create properly url", githubRemoteUrl, quiet);
            return null;
        }

        return urlToOpen;
    }

    @Nullable
    private static String makeUrlToOpen(
        @Nullable Editor editor,
        @Nonnull String relativePath,
        @Nonnull String branch,
        @Nonnull String githubRemoteUrl
    ) {
        final StringBuilder builder = new StringBuilder();
        final String githubRepoUrl = GithubUrlUtil.makeGithubRepoUrlFromRemoteUrl(githubRemoteUrl);
        if (githubRepoUrl == null) {
            return null;
        }
        builder.append(githubRepoUrl).append("/tree/").append(branch).append(relativePath);

        if (editor != null && editor.getDocument().getLineCount() >= 1) {
            // lines are counted internally from 0, but from 1 on github
            SelectionModel selectionModel = editor.getSelectionModel();
            final int begin = editor.getDocument().getLineNumber(selectionModel.getSelectionStart()) + 1;
            final int selectionEnd = selectionModel.getSelectionEnd();
            int end = editor.getDocument().getLineNumber(selectionEnd) + 1;
            if (editor.getDocument().getLineStartOffset(end - 1) == selectionEnd) {
                end -= 1;
            }
            builder.append("#L").append(begin).append('-').append(end);
        }

        return builder.toString();
    }

    @Nullable
    public static String getBranchNameOnRemote(
        @Nonnull Project project,
        @Nonnull GitRepository repository,
        boolean quiet
    ) {
        GitLocalBranch currentBranch = repository.getCurrentBranch();
        if (currentBranch == null) {
            showError(
                project,
                "Can't open the file on GitHub when repository is on detached HEAD. Please checkout a branch.",
                null,
                quiet
            );
            return null;
        }

        GitRemoteBranch tracked = currentBranch.findTrackedBranch(repository);
        if (tracked == null) {
            showError(
                project,
                "Can't open the file on GitHub when current branch doesn't have a tracked branch.",
                "Current branch: " + currentBranch + ", tracked info: " + repository.getBranchTrackInfos(),
                quiet
            );
            return null;
        }

        return tracked.getNameForRemoteOperations();
    }

    private static void showError(
        @Nonnull Project project,
        @Nonnull String message,
        @Nullable String details,
        boolean quiet
    ) {
        if (!quiet) {
            if (details == null) {
                GithubNotifications.showError(project, CANNOT_OPEN_IN_BROWSER, message);
            }
            else {
                GithubNotifications.showError(project, CANNOT_OPEN_IN_BROWSER, message, details);
            }
        }
    }
}