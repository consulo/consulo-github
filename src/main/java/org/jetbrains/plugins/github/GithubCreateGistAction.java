/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import consulo.application.Application;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.application.util.function.Computable;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.github.icon.GitHubIconGroup;
import consulo.language.editor.PlatformDataKeys;
import consulo.language.file.FileTypeManager;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.versionControlSystem.change.ChangeListManager;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubGist;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationCanceledException;
import org.jetbrains.plugins.github.ui.GithubCreateGistDialog;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.jetbrains.plugins.github.api.GithubGist.FileContent;

/**
 * @author oleg
 * @since 2011-09-27
 */
public class GithubCreateGistAction extends DumbAwareAction {
    private static final Logger LOG = GithubUtil.LOG;
    private static final String FAILED_TO_CREATE_GIST = "Can't create Gist";

    protected GithubCreateGistAction() {
        super("Create Gist...", "Create GitHub Gist", GitHubIconGroup.github_icon());
    }

    @RequiredUIAccess
    @Override
    public void update(@Nonnull final AnActionEvent e) {
        Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project == null || project.isDefault()) {
            e.getPresentation().setVisible(false);
            e.getPresentation().setEnabled(false);
            return;
        }
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);

        if ((editor == null && file == null && files == null)
            || (editor != null && editor.getDocument().getTextLength() == 0)) {
            GithubUtil.setVisibleEnabled(e, false, false);
            return;
        }
        GithubUtil.setVisibleEnabled(e, true, true);
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(@Nonnull final AnActionEvent e) {
        final Project project = e.getData(PlatformDataKeys.PROJECT);
        if (project == null || project.isDefault()) {
            return;
        }

        final Editor editor = e.getData(PlatformDataKeys.EDITOR);
        final VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
        final VirtualFile[] files = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
        if (editor == null && file == null && files == null) {
            return;
        }

        createGistAction(project, editor, file, files);
    }

    @RequiredUIAccess
    static void createGistAction(
        @Nonnull final Project project,
        @Nullable final Editor editor,
        @Nullable final VirtualFile file,
        @Nullable final VirtualFile[] files
    ) {
        // Ask for description and other params
        final GithubCreateGistDialog dialog = new GithubCreateGistDialog(project, editor, files, file);
        dialog.show();
        if (!dialog.isOK()) {
            return;
        }

        GithubAuthData auth = GithubAuthData.createAnonymous();
        if (!dialog.isAnonymous()) {
            try {
                auth = getValidAuthData(project);
            }
            catch (GithubAuthenticationCanceledException e) {
                return;
            }
            catch (IOException e) {
                GithubNotifications.showError(project, "Can't create gist", e);
                return;
            }
        }

        final Ref<String> url = new Ref<>();
        final GithubAuthData finalAuth = auth;
        new Task.Backgroundable(project, "Creating Gist...") {
            @Override
            public void run(@Nonnull ProgressIndicator indicator) {
                List<FileContent> contents = collectContents(project, editor, file, files);
                String gistUrl =
                    createGist(project, finalAuth, contents, dialog.isPrivate(), dialog.getDescription(), dialog.getFileName());
                url.set(gistUrl);
            }

            @RequiredUIAccess
            @Override
            public void onSuccess() {
                if (url.isNull()) {
                    return;
                }
                if (dialog.isOpenInBrowser()) {
                    Platform.current().openInBrowser(url.get());
                }
                else {
                    GithubNotifications.showInfoURL(project, "Gist Created Successfully", "Your gist url", url.get());
                }
            }
        }.queue();
    }

    @Nonnull
    private static GithubAuthData getValidAuthData(@Nonnull final Project project) throws IOException {
        return GithubUtil.computeValueInModal(
            project,
            "Access to GitHub",
            indicator -> GithubUtil.getValidAuthDataFromConfig(project, indicator)
        );
    }

    @Nonnull
    static List<FileContent> collectContents(
        @Nonnull Project project,
        @Nullable Editor editor,
        @Nullable VirtualFile file,
        @Nullable VirtualFile[] files
    ) {
        if (editor != null) {
            String content = getContentFromEditor(editor);
            if (content == null) {
                return Collections.emptyList();
            }
            if (file != null) {
                return Collections.singletonList(new FileContent(file.getName(), content));
            }
            else {
                return Collections.singletonList(new FileContent("", content));
            }
        }
        if (files != null) {
            List<FileContent> contents = new ArrayList<>();
            for (VirtualFile vf : files) {
                contents.addAll(getContentFromFile(vf, project, null));
            }
            return contents;
        }

        if (file != null) {
            return getContentFromFile(file, project, null);
        }

        LOG.error("File, files and editor can't be null all at once!");
        throw new IllegalStateException("File, files and editor can't be null all at once!");
    }

    @Nullable
    static String createGist(
        @Nonnull Project project,
        @Nonnull GithubAuthData auth,
        @Nonnull List<FileContent> contents,
        boolean isPrivate,
        @Nonnull String description,
        @Nullable String filename
    ) {
        if (contents.isEmpty()) {
            GithubNotifications.showWarning(project, FAILED_TO_CREATE_GIST, "Can't create empty gist");
            return null;
        }
        if (contents.size() == 1 && filename != null) {
            FileContent entry = contents.iterator().next();
            contents = Collections.singletonList(new FileContent(filename, entry.getContent()));
        }
        try {
            GithubGist gist = GithubApiUtil.createGist(auth, contents, description, isPrivate);
            return gist.getHtmlUrl();
        }
        catch (IOException e) {
            GithubNotifications.showError(project, FAILED_TO_CREATE_GIST, e);
            return null;
        }
    }

    @Nullable
    private static String getContentFromEditor(@Nonnull final Editor editor) {
        String text = Application.get().runReadAction(new Computable<String>() {
            @Nullable
            @Override
            public String compute() {
                return editor.getSelectionModel().getSelectedText();
            }
        });

        if (text == null) {
            text = editor.getDocument().getText();
        }

        if (StringUtil.isEmptyOrSpaces(text)) {
            return null;
        }
        return text;
    }

    @Nonnull
    private static List<FileContent> getContentFromFile(
        @Nonnull final VirtualFile file,
        @Nonnull Project project,
        @Nullable String prefix
    ) {
        if (file.isDirectory()) {
            return getContentFromDirectory(file, project, prefix);
        }
        Document document = Application.get()
            .runReadAction((Supplier<Document>)() -> FileDocumentManager.getInstance().getDocument(file));
        String content;
        if (document != null) {
            content = document.getText();
        }
        else {
            content = readFile(file);
        }
        if (content == null) {
            GithubNotifications.showWarning(project, FAILED_TO_CREATE_GIST, "Couldn't read the contents of the file " + file);
            return Collections.emptyList();
        }
        if (StringUtil.isEmptyOrSpaces(content)) {
            return Collections.emptyList();
        }
        String filename = addPrefix(file.getName(), prefix, false);
        return Collections.singletonList(new FileContent(filename, content));
    }

    @Nonnull
    private static List<FileContent> getContentFromDirectory(
        @Nonnull VirtualFile dir,
        @Nonnull Project project,
        @Nullable String prefix
    ) {
        List<FileContent> contents = new ArrayList<>();
        for (VirtualFile file : dir.getChildren()) {
            if (!isFileIgnored(file, project)) {
                String pref = addPrefix(dir.getName(), prefix, true);
                contents.addAll(getContentFromFile(file, project, pref));
            }
        }
        return contents;
    }

    @Nullable
    private static String readFile(@Nonnull final VirtualFile file) {
        return Application.get().runReadAction(new Computable<String>() {
            @Nullable
            @Override
            public String compute() {
                try {
                    return new String(file.contentsToByteArray(), file.getCharset());
                }
                catch (IOException e) {
                    LOG.info("Couldn't read contents of the file " + file, e);
                    return null;
                }
            }
        });
    }

    private static String addPrefix(@Nonnull String name, @Nullable String prefix, boolean addTrailingSlash) {
        String pref = prefix == null ? "" : prefix;
        pref += name;
        if (addTrailingSlash) {
            pref += "_";
        }
        return pref;
    }

    private static boolean isFileIgnored(@Nonnull VirtualFile file, @Nonnull Project project) {
        ChangeListManager manager = ChangeListManager.getInstance(project);
        return manager.isIgnoredFile(file) || FileTypeManager.getInstance().isFileIgnored(file);
    }
}
