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

import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.github.localize.GithubLocalize;
import consulo.language.file.FileTypeManager;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@ExtensionImpl(order = "last")
public class DefaultGithubGistContentsCollector implements GithubGistContentsCollector {
    private static final Logger LOG = GithubUtil.LOG;

    @Override
    public @Nullable String getGistFileName(@Nullable Editor editor, VirtualFile @Nullable [] files) {
        VirtualFile onlyFile = files != null && files.length == 1 && !files[0].isDirectory() ? files[0] : null;
        if (onlyFile != null) {
            return onlyFile.getName();
        }
        if (editor != null) {
            return "";
        }
        return null;
    }

    @Override
    public List<GithubGistRequest.FileContent> collectContents(GistEventData gistEventData) {
        Project project = gistEventData.project();
        Editor editor = gistEventData.editor();
        VirtualFile file = gistEventData.file();
        VirtualFile[] files = gistEventData.files();

        if (editor != null) {
            List<GithubGistRequest.FileContent> contents = getContentFromEditor(editor, file);
            if (contents != null) {
                return contents;
            }
        }

        VirtualFile[] myFiles = files != null ? files : file != null ? new VirtualFile[]{file} : null;
        if (myFiles != null) {
            List<GithubGistRequest.FileContent> result = new ArrayList<>();
            for (VirtualFile vf : myFiles) {
                result.addAll(getContentFromFile(vf, project, null));
            }
            return result;
        }

        LOG.error("File, files and editor can't be null all at once!");
        throw new IllegalStateException("File, files and editor can't be null all at once!");
    }

    protected @Nullable List<GithubGistRequest.FileContent> getContentFromEditor(Editor editor, @Nullable VirtualFile file) {
        String selectedText = getSelectedText(editor);
        String text = selectedText != null ? selectedText : ReadAction.compute(() -> editor.getDocument().getText());
        if (StringUtil.isEmptyOrSpaces(text)) {
            return null;
        }

        String fileName = file != null ? file.getName() : "";
        return List.of(new GithubGistRequest.FileContent(fileName, text));
    }

    protected @Nullable String getSelectedText(Editor editor) {
        return ReadAction.compute(() -> editor.getSelectionModel().getSelectedText());
    }

    private List<GithubGistRequest.FileContent> getContentFromFile(VirtualFile file, Project project, @Nullable String prefix) {
        if (file.isDirectory()) {
            return getContentFromDirectory(file, project, prefix);
        }
        if (file.getFileType().isBinary()) {
            GithubNotifications.showWarning(
                project,
                GithubLocalize.cannotCreateGist(),
                GithubLocalize.createGistErrorBinaryFile(file.getName())
            );
            return List.of();
        }
        String content = project.getUIAccess().giveAndWaitIfNeed(() -> WriteAction.compute(() -> getFileContents(file)));
        if (content == null) {
            GithubNotifications.showWarning(
                project,
                GithubLocalize.cannotCreateGist(),
                GithubLocalize.createGistErrorContentRead(file.getName())
            );
            return List.of();
        }
        if (StringUtil.isEmptyOrSpaces(content)) {
            return List.of();
        }
        String filename = addPrefix(file.getName(), prefix, false);
        return List.of(new GithubGistRequest.FileContent(filename, content));
    }

    private @Nullable String getFileContents(VirtualFile file) {
        try {
            return getFileContentInternal(file);
        }
        catch (IOException e) {
            LOG.info("Couldn't read contents of the file " + file, e);
            return null;
        }
    }

    protected @Nullable String getFileContentInternal(VirtualFile file) throws IOException {
        FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
        Document document = fileDocumentManager.getDocument(file);
        if (document != null) {
            fileDocumentManager.saveDocument(document);
            return document.getText();
        }
        else {
            return new String(file.contentsToByteArray(), file.getCharset());
        }
    }

    private List<GithubGistRequest.FileContent> getContentFromDirectory(VirtualFile dir, Project project, @Nullable String prefix) {
        List<GithubGistRequest.FileContent> contents = new ArrayList<>();
        for (VirtualFile file : dir.getChildren()) {
            if (!isFileIgnored(file, project)) {
                String pref = addPrefix(dir.getName(), prefix, true);
                contents.addAll(getContentFromFile(file, project, pref));
            }
        }
        return contents;
    }

    private String addPrefix(String name, @Nullable String prefix, boolean addTrailingSlash) {
        StringBuilder pref = new StringBuilder(prefix != null ? prefix : "");
        pref.append(name);
        if (addTrailingSlash) {
            pref.append('_');
        }
        return pref.toString();
    }

    private boolean isFileIgnored(VirtualFile file, Project project) {
        ChangeListManager manager = ChangeListManager.getInstance(project);
        return manager.isIgnoredFile(file) || FileTypeManager.getInstance().isFileIgnored(file);
    }
}
