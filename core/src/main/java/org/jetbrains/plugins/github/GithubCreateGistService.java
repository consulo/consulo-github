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

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountFeature;
import consulo.accountManagement.AccountFeatureState;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.progress.ProgressBuilderFactory;
import consulo.codeEditor.Editor;
import consulo.component.ProcessCanceledException;
import consulo.github.localize.GithubLocalize;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.review.snippet.PathHandlingMode;
import consulo.ui.UIAccess;
import consulo.ui.UIAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.CopyPasteManager;
import consulo.ui.ex.dialog.DialogService;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.concurrent.coroutine.step.CodeExecution;
import consulo.util.concurrent.coroutine.step.CompletableFutureStep;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest.FileContent;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.authentication.accounts.GithubGistsFeature;
import org.jetbrains.plugins.github.ui.GithubCreateGistDialog;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubSettings;
import org.jetbrains.plugins.github.util.GithubUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GithubCreateGistService {
    private static final Logger LOG = GithubUtil.LOG;

    private final Project myProject;
    private final AccountManager myAccountManager;
    private final ProgressBuilderFactory myProgressBuilderFactory;

    @Inject
    public GithubCreateGistService(Project project, AccountManager accountManager, ProgressBuilderFactory progressBuilderFactory) {
        myProject = project;
        myAccountManager = accountManager;
        myProgressBuilderFactory = progressBuilderFactory;
    }

    public static GithubCreateGistService getInstance(Project project) {
        return project.getInstance(GithubCreateGistService.class);
    }

    @RequiredUIAccess
    public void createGistAction(@Nullable Editor editor, @Nullable VirtualFile file, VirtualFile[] files) {
        GithubSettings settings = GithubSettings.getInstance();
        // Ask for description and other params
        String fileName = GithubGistContentsCollector.findGistFileName(editor, files);
        GithubCreateGistDialog dialog = new GithubCreateGistDialog(
            myProject,
            fileName,
            settings.isPrivateGist(),
            settings.isOpenInBrowserGist(),
            settings.isCopyURLGist()
        );
        UIAccess uiAccess = UIAccess.current();
        myProject.getApplication().getInstance(DialogService.class).build(dialog).showAsync().whenCompleteAsync((value, error) -> {
            if (error != null || value == null) {
                return;
            }

            settings.setPrivateGist(dialog.isSecret());
            settings.setOpenInBrowserGist(dialog.isOpenInBrowser());
            settings.setCopyURLGist(dialog.isCopyURL());

            Account account = Objects.requireNonNull(dialog.getAccount());

            getOrRequestAccount(account).whenCompleteAsync((authorizedAccount, authError) -> {
                if (authorizedAccount == null) {
                    return;
                }
                createGist(authorizedAccount, editor, file, files, dialog, uiAccess);
            }, uiAccess);
        }, uiAccess);
    }

    @RequiredUIAccess
    private CompletableFuture<@Nullable Account> getOrRequestAccount(Account account) {
        AccountType<?> type = myAccountManager.findAccountType(GithubAccountType.ID);
        AccountFeature<?> gistsFeature = type == null ? null : myAccountManager.getFeatures(type)
            .stream()
            .filter(it -> it instanceof GithubGistsFeature)
            .findFirst()
            .orElse(null);
        if (gistsFeature != null && myAccountManager.getState(gistsFeature, account) == AccountFeatureState.AUTH_FAILED) {
            return myAccountManager.requestAuthorization(account, gistsFeature, myProject, null);
        }
        return CompletableFuture.completedFuture(account);
    }

    @RequiredUIAccess
    private void createGist(
        Account account,
        @Nullable Editor editor,
        @Nullable VirtualFile file,
        VirtualFile[] files,
        GithubCreateGistDialog dialog,
        UIAccess uiAccess
    ) {
        GithubServerPath server = GithubAccountType.toServerPath(myAccountManager.getServerUrl(account));
        GithubApiRequestExecutor requestExecutor = GithubApiRequestExecutorFactory.getInstance().create(account, server);
        PathHandlingMode pathMode = Objects.requireNonNull(dialog.getPathMode());
        boolean isSecret = dialog.isSecret();
        boolean isCopyURL = dialog.isCopyURL();
        boolean isOpenInBrowser = dialog.isOpenInBrowser();
        String description = dialog.getDescription();
        String gistFileName = dialog.getFileName();

        myProgressBuilderFactory.newProgressBuilder(myProject, GithubLocalize.createGistProcess())
            .cancelable()
            .execute(uiAccess, () -> Coroutine
                .first(CodeExecution.<Object, List<FileContent>>apply(input ->
                    renameContents(GithubGistContentsCollector.collectContents(myProject, editor, file, files), files, pathMode)
                ))
                .then(CompletableFutureStep.<List<FileContent>, @Nullable String>await(contents ->
                    createGist(myProject, requestExecutor, server, contents, isSecret, description, gistFileName)
                ))
                .then(UIAction.<@Nullable String, Object>apply(gistUrl -> {
                    // onSuccess
                    if (gistUrl == null) {
                        return null;
                    }
                    if (isCopyURL) {
                        CopyPasteManager.getInstance().setText(gistUrl);
                    }
                    if (isOpenInBrowser) {
                        Platform.current().openInBrowser(gistUrl);
                    }
                    else {
                        GithubNotifications.showInfoURL(
                            myProject,
                            GithubLocalize.createGistSuccess(),
                            GithubLocalize.createGistDialogOpenBrowser().get(),
                            gistUrl
                        );
                    }
                    return null;
                }))
            )
            .whenComplete((result, error) -> {
                Throwable cause = unwrap(error);
                if (cause != null && !(cause instanceof ProcessCanceledException) && !(cause instanceof CancellationException)) {
                    LOG.error("Failed to create gist", cause);
                }
            });
    }

    public CompletableFuture<@Nullable String> createGist(
        Project project,
        GithubApiRequestExecutor executor,
        GithubServerPath server,
        List<FileContent> contents,
        boolean isSecret,
        String description,
        @Nullable String filename
    ) {
        if (contents.isEmpty()) {
            GithubNotifications.showWarning(
                project,
                GithubLocalize.cannotCreateGist(),
                GithubLocalize.createGistErrorEmpty()
            );
            return CompletableFuture.completedFuture(null);
        }

        List<FileContent> gistContents = contents.size() == 1 && filename != null
            ? new ArrayList<>(List.of(new FileContent(filename, contents.get(0).getContent())))
            : contents;

        return executor.execute(GithubApiRequests.Gists.create(server, gistContents, description, !isSecret))
            .handle((gist, error) -> {
                Throwable cause = unwrap(error);
                if (cause == null) {
                    return gist.getHtmlUrl();
                }
                if (cause instanceof IOException e) {
                    GithubNotifications.showError(project, GithubLocalize.cannotCreateGist(), e);
                    return null;
                }
                throw new CompletionException(cause);
            });
    }

    private List<FileContent> renameContents(List<FileContent> contents, VirtualFile[] files, PathHandlingMode pathHandlingMode) {
        Function<VirtualFile, String> fileNameExtractor =
            PathHandlingMode.getFileNameExtractor(myProject, Arrays.asList(files), pathHandlingMode);

        int size = Math.min(contents.size(), files.length);
        List<FileContent> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String newFileName = fileNameExtractor.apply(files[i]);
            result.add(new FileContent(newFileName.replace("/", "\\"), contents.get(i).getContent()));
        }
        return result;
    }

    private static @Nullable Throwable unwrap(@Nullable Throwable error) {
        Throwable cause = error;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
