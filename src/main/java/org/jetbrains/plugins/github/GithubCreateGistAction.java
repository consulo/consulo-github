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
package org.jetbrains.plugins.github;

import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.codeEditor.Editor;
import consulo.github.icon.GitHubIconGroup;
import consulo.github.localize.GithubLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@ActionImpl(
    id = "Github.Create.Gist",
    parents = {
        @ActionParentRef(@ActionRef(id = "EditorPopupMenu")),
        @ActionParentRef(@ActionRef(id = "ProjectViewPopupMenu")),
        @ActionParentRef(@ActionRef(id = "EditorTabPopupMenu")),
        @ActionParentRef(@ActionRef(id = "ConsoleEditorPopupMenu"))
    }
)
public class GithubCreateGistAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    private static final Predicate<@Nullable VirtualFile> FILE_WITH_CONTENT = f -> f != null && !(f.getFileType().isBinary());

    public GithubCreateGistAction() {
        super(
            GithubLocalize.actionCreateGistText(),
            GithubLocalize.actionCreateGistDescription(),
            GitHubIconGroup.github()
        );
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null || project.isDefault()) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        AccountManager accountManager = project.getApplication().getInstance(AccountManager.class);
        AccountType<?> accountType = accountManager.findAccountType(GithubAccountType.ID);
        GHHostedRepositoriesManager hostedRepositoriesManager = GHHostedRepositoriesManager.getInstance(project);
        if (hostedRepositoriesManager.getKnownRepositoriesState().get().isEmpty() &&
            (accountType == null || accountManager.getAccounts(accountType, project).isEmpty())) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        Editor editor = e.getData(Editor.KEY);
        VirtualFile file = e.getData(VirtualFile.KEY);
        VirtualFile[] files = e.getData(VirtualFile.KEY_OF_ARRAY);
        boolean hasFilesWithContent = FILE_WITH_CONTENT.test(file) || (files != null && ContainerUtil.exists(files, FILE_WITH_CONTENT));
        boolean isTerminal = editor != null && editor.isViewer();
        boolean isDirectory = (file != null && file.isDirectory());

        if (!isTerminal && !isDirectory && (!hasFilesWithContent || editor != null && editor.getDocument().getTextLength() == 0)) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        e.getPresentation().setEnabledAndVisible(true);
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null || project.isDefault()) {
            return;
        }

        Editor editor = e.getData(Editor.KEY);
        VirtualFile file = e.getData(VirtualFile.KEY);
        VirtualFile[] files = e.getData(VirtualFile.KEY_OF_ARRAY);
        if (editor == null && file == null && files == null) {
            return;
        }
        List<VirtualFile> allFiles = new ArrayList<>();

        if (files != null) {
            for (VirtualFile f : files) {
                collectFilesRecursively(f, allFiles);
            }
        }

        GithubCreateGistService service = GithubCreateGistService.getInstance(project);
        service.createGistAction(
            editor,
            FILE_WITH_CONTENT.test(file) ? file : null,
            filterFilesWithContent(allFiles.toArray(VirtualFile.EMPTY_ARRAY))
        );
    }

    private static void collectFilesRecursively(VirtualFile file, List<VirtualFile> collection) {
        if (file.isDirectory()) {
            VirtualFile[] children = file.getChildren();
            for (VirtualFile child : children) {
                collectFilesRecursively(child, collection);
            }
        }
        else {
            if (FILE_WITH_CONTENT.test(file)) {
                collection.add(file);
            }
        }
    }

    private static VirtualFile[] filterFilesWithContent(VirtualFile[] files) {
        return ContainerUtil.filter(files, FILE_WITH_CONTENT).toArray(VirtualFile.EMPTY_ARRAY);
    }
}
