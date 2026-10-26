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
package org.jetbrains.plugins.github.share;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.document.FileDocumentManager;
import consulo.github.localize.GithubLocalize;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.dialog.DialogService;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import git4idea.remote.hosting.GitShareProjectService;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.data.request.Type;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException;
import org.jetbrains.plugins.github.ui.GithubShareDialog;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jetbrains.plugins.github.util.GithubGitHelper;
import org.jetbrains.plugins.github.util.GithubSettings;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public final class GHShareProjectUtil {
    private GHShareProjectUtil() {
    }

    @RequiredUIAccess
    public static void shareProjectOnGithub(Project project, @Nullable VirtualFile file) {
        GitRepository gitRepository = GithubGitHelper.findGitRepository(project, file);
        VirtualFile root = gitRepository != null ? gitRepository.getRoot() : project.getBaseDir();
        if (root == null) {
            return;
        }
        String projectName = project.getName();
        shareProjectOnGithub(project, gitRepository, root, projectName);
    }

    @RequiredUIAccess
    public static void shareProjectOnGithub(Project project,
                                            @Nullable GitRepository gitRepository,
                                            VirtualFile root,
                                            String projectName) {
        FileDocumentManager.getInstance().saveAllDocuments();

        String githubServiceName = GithubLocalize.settingsConfigurableDisplayName().get();

        GitShareProjectService gitSpService = GitShareProjectService.getInstance(project);
        GHHostedRepositoriesManager projectManager = GHHostedRepositoriesManager.getInstance(project);
        UIAccess uiAccess = UIAccess.current();
        gitSpService.showExistingRemotesDialog(githubServiceName, gitRepository, projectManager.getKnownRepositories())
            .thenComposeAsync(proceed -> {
                if (!proceed) {
                    return CompletableFuture.<GithubShareDialog.@Nullable Result>completedFuture(null);
                }
                return showShareProjectDialog(project, gitRepository, projectName);
            }, uiAccess)
            .thenAcceptAsync(shareDialogResult -> {
                if (shareDialogResult == null) {
                    return;
                }

                Account account = Objects.requireNonNull(shareDialogResult.getAccount());
                GithubServerPath server = serverPath(project, account);
                GithubApiRequestExecutor requestExecutor = GithubApiRequestExecutorFactory.getInstance().create(account, server);

                gitSpService.performShareProject(
                    githubServiceName, gitRepository, root, shareDialogResult.getRepositoryName(), shareDialogResult.getRemoteName(),
                    () -> createRepo(requestExecutor, server, shareDialogResult),
                    GithubRepo::getHtmlUrl,
                    it -> {
                        String sshUrl = it.getSshUrl();
                        return GithubSettings.getInstance().isCloneGitUsingSsh() && sshUrl != null ? sshUrl : it.getCloneUrl();
                    }
                );
            }, uiAccess);
    }

    private static CompletableFuture<GithubRepo> createRepo(GithubApiRequestExecutor requestExecutor,
                                                            GithubServerPath server,
                                                            GithubShareDialog.Result shareDialogResult) {
        String name = shareDialogResult.getRepositoryName();
        String description = shareDialogResult.getDescription();
        boolean isPrivate = shareDialogResult.isPrivate();

        return requestExecutor.execute(GithubApiRequests.CurrentUser.Repos.create(server, name, description, isPrivate));
    }

    @RequiredUIAccess
    private static CompletableFuture<GithubShareDialog.@Nullable Result> showShareProjectDialog(Project project,
                                                                                           @Nullable GitRepository gitRepository,
                                                                                           String projectName) {
        Map<Account, CompletableFuture<Pair<Boolean, Set<String>>>> loadedInfo = Collections.synchronizedMap(new HashMap<>());

        Set<String> remotes = new LinkedHashSet<>();
        if (gitRepository != null) {
            for (GitRemote remote : gitRepository.getRemotes()) {
                remotes.add(remote.getName());
            }
        }

        GithubShareDialog shareDialog = new GithubShareDialog(
            project,
            remotes,
            (account, comp) -> getOrLoad(loadedInfo, account, () -> loadEnsuringTokenExistsToken(project, account, comp)),
            projectName
        );

        return project.getApplication().getInstance(DialogService.class)
            .build(shareDialog)
            .showAsync()
            .<GithubShareDialog.@Nullable Result>thenApply(value -> shareDialog.getResult())
            .exceptionally(error -> null);
    }

    private static CompletableFuture<Pair<Boolean, Set<String>>> getOrLoad(Map<Account, CompletableFuture<Pair<Boolean, Set<String>>>> loadedInfo,
                                                                           Account account,
                                                                           Supplier<CompletableFuture<Pair<Boolean, Set<String>>>> loader) {
        CompletableFuture<Pair<Boolean, Set<String>>> cached = loadedInfo.get(account);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<Pair<Boolean, Set<String>>> future = loader.get();
        loadedInfo.put(account, future);
        future.whenComplete((info, error) -> {
            if (error != null) {
                loadedInfo.remove(account, future);
            }
        });
        return future;
    }

    private static CompletableFuture<Pair<Boolean, Set<String>>> loadEnsuringTokenExistsToken(Project project,
                                                                                             Account account,
                                                                                             @Nullable Component comp) {
        return loadAccountInfo(project, account).handle((info, error) -> {
            if (error == null) {
                return CompletableFuture.completedFuture(info);
            }
            Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
            if (!(cause instanceof GithubMissingTokenException) && !(cause instanceof GithubAuthenticationException)) {
                return CompletableFuture.<Pair<Boolean, Set<String>>>failedFuture(cause);
            }
            return CompletableFuture.supplyAsync(() -> accountManager(project).requestAuthorization(account, null, project, comp),
                    project.getUIAccess())
                .thenCompose(it -> it)
                .thenCompose(authorized -> {
                    if (authorized == null) {
                        return CompletableFuture.<Pair<Boolean, Set<String>>>failedFuture(cause);
                    }
                    return loadAccountInfo(project, authorized);
                });
        }).thenCompose(it -> it);
    }

    private static CompletableFuture<Pair<Boolean, Set<String>>> loadAccountInfo(Project project, Account account) {
        GithubServerPath server = serverPath(project, account);
        GithubApiRequestExecutor requestExecutor = GithubApiRequestExecutorFactory.getInstance().create(account, server);

        CompletableFuture<GithubAuthenticatedUser> user = requestExecutor.execute(GithubApiRequests.CurrentUser.get(server));
        CompletableFuture<List<GithubRepo>> repos = GithubApiPagesLoader.loadAll(requestExecutor,
            GithubApiRequests.CurrentUser.Repos.pages(server, Type.OWNER, null, null, null));
        return user.thenCombine(repos, (details, list) -> {
            Set<String> names = new LinkedHashSet<>();
            for (GithubRepo repo : list) {
                names.add(repo.getName());
            }
            return Pair.create(details.canCreatePrivateRepo(), names);
        });
    }

    private static GithubServerPath serverPath(Project project, Account account) {
        return GithubAccountType.toServerPath(accountManager(project).getServerUrl(account));
    }

    private static AccountManager accountManager(Project project) {
        return project.getApplication().getInstance(AccountManager.class);
    }
}
