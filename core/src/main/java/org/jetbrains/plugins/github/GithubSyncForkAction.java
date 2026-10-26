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

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountAuthorizeConfig;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.accountManagement.authorize.TokenAuthorizeConfig;
import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressBuilderFactory;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.document.FileDocumentManager;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.dialog.DialogService;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.concurrent.coroutine.step.CodeExecution;
import consulo.versionControlSystem.VcsException;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.remote.hosting.GitHostingUrlUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.data.GithubRepoDetailed;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.authentication.ui.GithubChooseAccountDialog;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jetbrains.plugins.github.util.GithubGitHelper;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static git4idea.fetch.GitFetchSupport.fetchSupport;

public abstract class GithubSyncForkAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    private static final Logger LOG = GithubUtil.LOG;
    private static final String UPSTREAM_REMOTE_NAME = "upstream";
    private static final String ORIGIN_REMOTE_NAME = "origin";

    protected GithubSyncForkAction(LocalizeValue text, LocalizeValue description) {
        super(text, description);
    }

    protected abstract LocalizeValue getProgressTitle();

    /**
     * Integrates {@code upstreamBranch} - the fetched default branch of the parent repository - into the current branch.
     */
    protected abstract void integrate(Project project, Git git, GitRepository repository, ProgressIndicator indicator, String upstreamBranch);

    @Override
    public void update(AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(isEnabledAndVisible(e));
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        FileDocumentManager.getInstance().saveAllDocuments();

        Project project = e.getRequiredData(Project.KEY);

        GitRepositoryManager gitRepositoryManager = GitUtil.getRepositoryManager(project);

        if (gitRepositoryManager.getRepositories().size() > 1) {
            GithubNotifications.showError(project,
                GithubLocalize.rebaseError(),
                GithubLocalize.rebaseErrorMultiRepoNotSupported());
            return;
        }

        GHHostedRepositoriesManager ghRepositoriesManager = GHHostedRepositoriesManager.getInstance(project);

        Set<GHGitRepositoryMapping> repositories = ghRepositoriesManager.getKnownRepositories();
        GHGitRepositoryMapping originMapping = null;
        for (GHGitRepositoryMapping mapping : repositories) {
            if (mapping.getRemote().remote().getName().equals(ORIGIN_REMOTE_NAME)) {
                originMapping = mapping;
                break;
            }
        }
        if (originMapping == null) {
            GithubNotifications.showError(project,
                GithubLocalize.rebaseError(),
                GithubLocalize.rebaseErrorRemoteOriginNotFound());
            return;
        }

        AccountManager accountManager = project.getApplication().getInstance(AccountManager.class);
        AccountType<?> accountType = accountManager.findAccountType(GithubAccountType.ID);
        GithubServerPath serverPath = originMapping.getRepository().getServerPath();
        List<Account> accounts = new ArrayList<>();
        for (Account account : accountManager.getAccounts(accountType, project)) {
            if (serverPath.equals(GithubAccountType.toServerPath(accountManager.getServerUrl(account)))) {
                accounts.add(account);
            }
        }

        CompletableFuture<@Nullable Account> githubAccount;
        if (accounts.isEmpty()) {
            AccountAuthorizeConfig config = serverPath.isGithubDotCom() ? null : findTokenConfig(accountManager, accountType);
            githubAccount = accountManager.requestLogin(accountType, config, project, null);
        }
        else if (accounts.size() == 1) {
            githubAccount = CompletableFuture.completedFuture(accounts.get(0));
        }
        else {
            GithubChooseAccountDialog chooseAccountDialog = new GithubChooseAccountDialog(accounts,
                GithubLocalize.accountChooseFor(serverPath.toString()),
                false,
                true);
            githubAccount = project.getApplication().getInstance(DialogService.class)
                .build(chooseAccountDialog)
                .showAsync()
                .<@Nullable Account>thenApply(value -> chooseAccountDialog.getAccount())
                .exceptionally(error -> null);
        }

        GHGitRepositoryMapping mapping = originMapping;
        githubAccount.whenCompleteAsync((account, error) -> {
            if (account == null) {
                GithubNotifications.showError(project,
                    GithubLocalize.rebaseError(),
                    GithubLocalize.rebaseErrorNoSuitableAccountFound());
                return;
            }

            new SyncForkTask(this, project, Git.getInstance(), account, GithubAccountType.toServerPath(accountManager.getServerUrl(account)),
                mapping.getRemote().repository(),
                mapping.getRepository().getRepositoryPath()).queue();
        }, UIAccess.current());
    }

    private static @Nullable AccountAuthorizeConfig findTokenConfig(AccountManager accountManager, AccountType<?> accountType) {
        for (AccountAuthorizeConfig config : accountManager.getAuthorizeConfigs(accountType)) {
            if (config instanceof TokenAuthorizeConfig) {
                return config;
            }
        }
        return null;
    }

    static boolean isEnabledAndVisible(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        if (project == null || project.isDefault()) {
            return false;
        }

        Set<GHGitRepositoryMapping> repositories = GHHostedRepositoriesManager.getInstance(project).getKnownRepositories();
        return !repositories.isEmpty();
    }

    private static class SyncForkTask {
        private final GithubSyncForkAction myAction;
        private final Project myProject;
        private final Git myGit;
        private final Account myAccount;
        private final GithubServerPath myServer;
        private final GitRepository myRepository;
        private final GHRepositoryPath myRepoPath;

        SyncForkTask(GithubSyncForkAction action,
                     Project project,
                     Git git,
                     Account account,
                     GithubServerPath server,
                     GitRepository repository,
                     GHRepositoryPath repoPath) {
            myAction = action;
            myProject = project;
            myGit = git;
            myAccount = account;
            myServer = server;
            myRepository = repository;
            myRepoPath = repoPath;
        }

        void queue() {
            myProject.getApplication().getInstance(ProgressBuilderFactory.class)
                .newProgressBuilder(myProject, myAction.getProgressTitle())
                .execute(myProject.getUIAccess(), () -> Coroutine.first(CodeExecution.<Object, Object>apply((input, continuation) -> {
                    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
                    run(indicator != null ? indicator : new EmptyProgressIndicator());
                    return null;
                })));
        }

        private void run(ProgressIndicator indicator) {
            GithubApiRequestExecutor executor = GithubApiRequestExecutorFactory.getInstance().create(myAccount, myServer);

            myRepository.update();

            GithubRepo parentRepo = validateRepoAndLoadParent(executor, indicator);
            if (parentRepo == null) {
                return;
            }

            GitRemote parentRemote = configureParentRemote(indicator, parentRepo.getFullPath());
            if (parentRemote == null) {
                return;
            }

            String branchName = parentRepo.getDefaultBranch();
            if (branchName == null) {
                GithubNotifications.showError(myProject,
                    GithubLocalize.rebaseError(),
                    GithubLocalize.rebaseErrorNoDefaultBranch());
                return;
            }

            if (!fetchParent(indicator, parentRemote)) {
                return;
            }

            myAction.integrate(myProject, myGit, myRepository, indicator, parentRemote.getName() + "/" + branchName);
        }

        private @Nullable GithubRepo validateRepoAndLoadParent(GithubApiRequestExecutor executor, ProgressIndicator indicator) {
            try {
                GithubRepoDetailed repositoryInfo =
                    executor.execute(indicator,
                        GithubApiRequests.Repos.get(myServer, myRepoPath.getOwner(), myRepoPath.getRepository()));
                if (repositoryInfo == null) {
                    GithubNotifications.showError(myProject,
                        GithubLocalize.rebaseError(),
                        GithubLocalize.rebaseErrorRepoNotFound(myRepoPath.toString()));
                    return null;
                }

                GithubRepo parentRepo = repositoryInfo.getParent();
                if (!repositoryInfo.isFork() || parentRepo == null) {
                    GithubNotifications.showWarningURL(myProject,
                        GithubLocalize.rebaseError(),
                        "GitHub repository ", "'" + repositoryInfo.getName() + "'", " is not a fork",
                        repositoryInfo.getHtmlUrl());
                    return null;
                }
                return parentRepo;
            }
            catch (IOException e) {
                GithubNotifications.showError(myProject,
                    GithubLocalize.cannotLoadRepoInfo(),
                    e);
                return null;
            }
        }

        private @Nullable GitRemote configureParentRemote(ProgressIndicator indicator, GHRepositoryPath parentRepoPath) {
            LOG.info("Configuring upstream remote");
            indicator.setText(GithubLocalize.rebaseProcessConfiguringUpstreamRemote());

            GitRemote upstreamRemote = findRemote(parentRepoPath);
            if (upstreamRemote != null) {
                LOG.info("Correct upstream remote already exists");
                return upstreamRemote;
            }

            LOG.info("Adding GitHub parent as a remote host");
            indicator.setText(GithubLocalize.rebaseProcessAddingGithubParentAsRemoteHost());
            String parentRepoUrl = GithubGitHelper.getInstance().getRemoteUrl(myServer, parentRepoPath);
            try {
                myGit.addRemote(myRepository, UPSTREAM_REMOTE_NAME, parentRepoUrl).throwOnError();
            }
            catch (VcsException e) {
                GithubNotifications.showError(myProject,
                    GithubLocalize.rebaseError(),
                    GithubLocalize.cannotConfigureRemote(UPSTREAM_REMOTE_NAME, String.valueOf(e.getMessage())));
                return null;
            }
            myRepository.update();
            upstreamRemote = findRemote(parentRepoPath);
            if (upstreamRemote == null) {
                GithubNotifications.showError(myProject,
                    GithubLocalize.rebaseError(),
                    GithubLocalize.rebaseErrorUpstreamNotFound(UPSTREAM_REMOTE_NAME));
            }
            return upstreamRemote;
        }

        private @Nullable GitRemote findRemote(GHRepositoryPath repoPath) {
            for (GitRemote remote : myRepository.getRemotes()) {
                String url = remote.getFirstUrl();
                if (url == null || !GitHostingUrlUtil.match(myServer.toURI(), url)) {
                    continue;
                }

                GHRepositoryPath remotePath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
                if (repoPath.equals(remotePath)) {
                    return remote;
                }
            }
            return null;
        }

        private boolean fetchParent(ProgressIndicator indicator, GitRemote remote) {
            LOG.info("Fetching upstream");
            indicator.setText(GithubLocalize.rebaseProcessFetchingUpstream());
            return fetchSupport(myProject).fetch(myRepository, remote).showNotificationIfFailed();
        }
    }
}
