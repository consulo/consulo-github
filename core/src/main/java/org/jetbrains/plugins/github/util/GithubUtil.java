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
package org.jetbrains.plugins.github.util;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import git4idea.remote.hosting.GitHostingUrlUtil;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.exceptions.GithubParseException;
import org.jspecify.annotations.Nullable;

import java.net.UnknownHostException;
import java.util.List;

/**
 * Various utility methods for the GutHub plugin.
 */
public final class GithubUtil {
    public static final Logger LOG = Logger.getInstance("github");

    public static final String SERVICE_DISPLAY_NAME = "GitHub";

    public static final String ENTERPRISE_SERVICE_DISPLAY_NAME = "GitHub Enterprise";
    public static final String GIT_AUTH_PASSWORD_SUBSTITUTE = "x-oauth-basic";

    private GithubUtil() {
    }

    public static String getErrorTextFromException(Throwable e) {
        if (e instanceof UnknownHostException) {
            return "Unknown host: " + e.getMessage();
        }
        return StringUtil.notNullize(e.getMessage(), "Unknown error");
    }

    /**
     * @deprecated {@link GithubGitHelper}
     */
    @Deprecated
    public static @Nullable GitRepository getGitRepository(Project project, @Nullable VirtualFile file) {
        return GithubGitHelper.findGitRepository(project, file);
    }

    /**
     * @deprecated {@link GithubGitHelper}
     */
    @Deprecated
    private static @Nullable String findGithubRemoteUrl(GitRepository repository) {
        Pair<GitRemote, String> remote = findGithubRemote(repository);
        if (remote == null) {
            return null;
        }
        return remote.getSecond();
    }

    /**
     * @deprecated {@link GithubServerPath}, {@link GithubGitHelper}
     */
    @Deprecated
    private static @Nullable Pair<GitRemote, String> findGithubRemote(GitRepository repository) {
        GithubServerPath server = findSingleAccountServer(repository.getProject());
        if (server == null) {
            return null;
        }

        Pair<GitRemote, String> githubRemote = null;
        for (GitRemote gitRemote : repository.getRemotes()) {
            for (String remoteUrl : gitRemote.getUrls()) {
                if (GitHostingUrlUtil.match(server.toURI(), remoteUrl)) {
                    String remoteName = gitRemote.getName();
                    if ("github".equals(remoteName) || "origin".equals(remoteName)) {
                        return Pair.create(gitRemote, remoteUrl);
                    }
                    if (githubRemote == null) {
                        githubRemote = Pair.create(gitRemote, remoteUrl);
                    }
                    break;
                }
            }
        }
        return githubRemote;
    }

    private static @Nullable GithubServerPath findSingleAccountServer(Project project) {
        AccountManager accountManager = project.getApplication().getInstance(AccountManager.class);
        AccountType<?> accountType = accountManager.findAccountType(GithubAccountType.ID);
        if (accountType == null) {
            return null;
        }
        List<Account> accounts = accountManager.getAccounts(accountType, project);
        if (accounts.size() != 1) {
            return null;
        }
        String serverUrl = accountManager.getServerUrl(accounts.get(0));
        if (serverUrl == null) {
            return GithubServerPath.DEFAULT_SERVER;
        }
        try {
            return GithubServerPath.from(serverUrl);
        }
        catch (GithubParseException e) {
            return null;
        }
    }

    /**
     * @deprecated {@link GithubServerPath}
     */
    @Deprecated
    public static boolean isRepositoryOnGitHub(GitRepository repository) {
        return findGithubRemoteUrl(repository) != null;
    }
}
