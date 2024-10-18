/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.action.AnActionEvent;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.function.ThrowableConsumer;
import consulo.util.lang.function.ThrowableFunction;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import git4idea.GitUtil;
import git4idea.config.GitVcsApplicationSettings;
import git4idea.config.GitVersion;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubUserDetailed;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationCanceledException;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.ui.GithubBasicLoginDialog;
import org.jetbrains.plugins.github.ui.GithubLoginDialog;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * Various utility methods for the GutHub plugin.
 *
 * @author oleg
 * @author Kirill Likhodedov
 * @author Aleksey Pivovarov
 */
public class GithubUtil {
    public static final Logger LOG = Logger.getInstance("github");

    // TODO: these functions ugly inside and out
    @Nonnull
    public static GithubAuthData runAndGetValidAuth(
        @Nullable Project project,
        @Nonnull ProgressIndicator indicator,
        @Nonnull ThrowableConsumer<GithubAuthData, IOException> task
    ) throws IOException {
        GithubAuthData auth = GithubSettings.getInstance().getAuthData();
        try {
            if (auth.getAuthType() == GithubAuthData.AuthType.ANONYMOUS) {
                throw new GithubAuthenticationException("Bad authentication type");
            }
            task.consume(auth);
            return auth;
        }
        catch (GithubAuthenticationException e) {
            auth = getValidAuthData(project, indicator);
            task.consume(auth);
            return auth;
        }
        catch (IOException e) {
            if (checkSSLCertificate(e, auth.getHost(), indicator)) {
                return runAndGetValidAuth(project, indicator, task);
            }
            throw e;
        }
    }

    @Nonnull
    public static <T> T runWithValidAuth(
        @Nullable Project project,
        @Nonnull ProgressIndicator indicator,
        @Nonnull ThrowableFunction<GithubAuthData, T, IOException> task
    ) throws IOException {
        GithubAuthData auth = GithubSettings.getInstance().getAuthData();
        try {
            if (auth.getAuthType() == GithubAuthData.AuthType.ANONYMOUS) {
                throw new GithubAuthenticationException("Bad authentication type");
            }
            return task.apply(auth);
        }
        catch (GithubAuthenticationException e) {
            auth = getValidAuthData(project, indicator);
            return task.apply(auth);
        }
        catch (IOException e) {
            if (checkSSLCertificate(e, auth.getHost(), indicator)) {
                return runWithValidAuth(project, indicator, task);
            }
            throw e;
        }
    }

    @Nonnull
    public static <T> T runWithValidBasicAuthForHost(
        @Nullable Project project,
        @Nonnull ProgressIndicator indicator,
        @Nonnull String host,
        @Nonnull ThrowableFunction<GithubAuthData, T, IOException> task
    ) throws IOException {
        GithubSettings settings = GithubSettings.getInstance();
        GithubAuthData auth = null;
        try {
            if (settings.getAuthType() != GithubAuthData.AuthType.BASIC
                || !StringUtil.equalsIgnoreCase(GithubUrlUtil.getApiUrl(host), GithubUrlUtil.getApiUrl(settings.getHost()))) {
                throw new GithubAuthenticationException("Bad authentication type");
            }
            auth = settings.getAuthData();
            return task.apply(auth);
        }
        catch (GithubAuthenticationException e) {
            auth = getValidBasicAuthDataForHost(project, indicator, host);
            return task.apply(auth);
        }
        catch (IOException e) {
            if (checkSSLCertificate(e, auth.getHost(), indicator)) {
                return runWithValidBasicAuthForHost(project, indicator, host, task);
            }
            throw e;
        }
    }

    private static boolean checkSSLCertificate(IOException e, final String host, ProgressIndicator indicator) {
        final GithubSslSupport sslSupport = GithubSslSupport.getInstance();
        if (GithubSslSupport.isCertificateException(e)) {
            final Ref<Boolean> result = new Ref<Boolean>();
            ApplicationManager.getApplication().invokeAndWait(
                new Runnable() {
                    @Override
                    public void run() {
                        result.set(sslSupport.askIfShouldProceed(host));
                    }
                },
                indicator.getModalityState()
            );
            return result.get();
        }
        return false;
    }

    /**
     * @return null if user canceled login dialog. Valid GithubAuthData otherwise.
     */
    @Nonnull
    public static GithubAuthData getValidAuthData(
        @Nullable Project project,
        @Nonnull ProgressIndicator indicator
    ) throws GithubAuthenticationCanceledException {
        final GithubLoginDialog dialog = new GithubLoginDialog(project);
        ApplicationManager.getApplication().invokeAndWait(
            new Runnable() {
                @Override
                public void run() {
                    dialog.show();
                }
            },
            indicator.getModalityState()
        );
        if (!dialog.isOK()) {
            throw new GithubAuthenticationCanceledException("Can't get valid credentials");
        }
        return dialog.getAuthData();
    }

    /**
     * @return null if user canceled login dialog. Valid GithubAuthData otherwise.
     */
    @Nonnull
    public static GithubAuthData getValidBasicAuthDataForHost(
        @Nullable Project project,
        @Nonnull ProgressIndicator indicator,
        @Nonnull String host
    ) throws GithubAuthenticationCanceledException {
        final GithubLoginDialog dialog = new GithubBasicLoginDialog(project);
        dialog.lockHost(host);
        ApplicationManager.getApplication().invokeAndWait(
            new Runnable() {
                @Override
                public void run() {
                    dialog.show();
                }
            },
            indicator.getModalityState()
        );
        if (!dialog.isOK()) {
            throw new GithubAuthenticationCanceledException("Can't get valid credentials");
        }
        return dialog.getAuthData();
    }

    @Nonnull
    public static GithubAuthData getValidAuthDataFromConfig(
        @Nullable Project project,
        @Nonnull ProgressIndicator indicator
    ) throws IOException {
        GithubAuthData auth = GithubSettings.getInstance().getAuthData();
        try {
            checkAuthData(auth);
            return auth;
        }
        catch (GithubAuthenticationException e) {
            return getValidAuthData(project, indicator);
        }
    }

    @Nonnull
    public static GithubUserDetailed checkAuthData(@Nonnull GithubAuthData auth) throws IOException {
        if (StringUtil.isEmptyOrSpaces(auth.getHost())) {
            throw new GithubAuthenticationException("Target host not defined");
        }

        switch (auth.getAuthType()) {
            case BASIC:
                GithubAuthData.BasicAuth basicAuth = auth.getBasicAuth();
                assert basicAuth != null;
                if (StringUtil.isEmptyOrSpaces(basicAuth.getLogin())
                    || StringUtil.isEmptyOrSpaces(basicAuth.getPassword())) {
                    throw new GithubAuthenticationException("Empty login or password");
                }
                break;
            case TOKEN:
                GithubAuthData.TokenAuth tokenAuth = auth.getTokenAuth();
                assert tokenAuth != null;
                if (StringUtil.isEmptyOrSpaces(tokenAuth.getToken())) {
                    throw new GithubAuthenticationException("Empty token");
                }
                break;
            case ANONYMOUS:
                throw new GithubAuthenticationException("Anonymous connection not allowed");
        }

        return testConnection(auth);
    }

    @Nonnull
    private static GithubUserDetailed testConnection(@Nonnull GithubAuthData auth) throws IOException {
        return GithubApiUtil.getCurrentUserDetailed(auth);
    }

    public static <T, E extends Throwable> T computeValueInModal(
        @Nonnull Project project,
        @Nonnull String caption,
        @Nonnull final ThrowableFunction<ProgressIndicator, T, E> task
    ) throws E {
        final Ref<T> dataRef = new Ref<T>();
        final Ref<E> exceptionRef = new Ref<E>();
        ProgressManager.getInstance().run(new Task.Modal(project, caption, true) {
            public void run(@Nonnull ProgressIndicator indicator) {
                try {
                    dataRef.set(task.apply(indicator));
                }
                catch (Error e) {
                    throw e;
                }
                catch (RuntimeException e) {
                    throw e;
                }
                catch (Throwable e) {
                    //noinspection unchecked
                    exceptionRef.set((E)e);
                }
            }
        });
        if (!exceptionRef.isNull()) {
            throw exceptionRef.get();
        }
        return dataRef.get();
    }

    /*
     * Git utils
     */

    @Nullable
    public static String findGithubRemoteUrl(@Nonnull GitRepository repository) {
        Pair<GitRemote, String> remote = findGithubRemote(repository);
        if (remote == null) {
            return null;
        }
        return remote.getSecond();
    }

    @Nullable
    public static Pair<GitRemote, String> findGithubRemote(@Nonnull GitRepository repository) {
        Pair<GitRemote, String> githubRemote = null;
        for (GitRemote gitRemote : repository.getRemotes()) {
            for (String remoteUrl : gitRemote.getUrls()) {
                if (GithubUrlUtil.isGithubUrl(remoteUrl)) {
                    final String remoteName = gitRemote.getName();
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

    @Nullable
    public static String findUpstreamRemote(@Nonnull GitRepository repository) {
        for (GitRemote gitRemote : repository.getRemotes()) {
            final String remoteName = gitRemote.getName();
            if ("upstream".equals(remoteName)) {
                for (String remoteUrl : gitRemote.getUrls()) {
                    if (GithubUrlUtil.isGithubUrl(remoteUrl)) {
                        return remoteUrl;
                    }
                }
                return gitRemote.getFirstUrl();
            }
        }
        return null;
    }

    public static boolean testGitExecutable(final Project project) {
        final GitVcsApplicationSettings settings = GitVcsApplicationSettings.getInstance();
        final String executable = settings.getPathToGit();
        final GitVersion version;
        try {
            version = GitVersion.identifyVersion(executable);
        }
        catch (Exception e) {
            GithubNotifications.showErrorDialog(project, GitBundle.message("find.git.error.title"), e);
            return false;
        }

        if (!version.isSupported()) {
            GithubNotifications.showWarningDialog(
                project,
                GitBundle.message("find.git.unsupported.message", version.toString(), GitVersion.MIN),
                GitBundle.message("find.git.success.title")
            );
            return false;
        }
        return true;
    }

    public static boolean isRepositoryOnGitHub(@Nonnull GitRepository repository) {
        return findGithubRemoteUrl(repository) != null;
    }

    public static void setVisibleEnabled(AnActionEvent e, boolean visible, boolean enabled) {
        e.getPresentation().setVisible(visible);
        e.getPresentation().setEnabled(enabled);
    }

    @Nonnull
    public static String getErrorTextFromException(@Nonnull Exception e) {
        return e.getMessage();
    }

    @Nullable
    public static GitRepository getGitRepository(@Nonnull Project project, @Nullable VirtualFile file) {
        GitRepositoryManager manager = GitUtil.getRepositoryManager(project);
        List<GitRepository> repositories = manager.getRepositories();
        if (repositories.size() == 0) {
            return null;
        }
        if (repositories.size() == 1) {
            return repositories.get(0);
        }
        if (file != null) {
            GitRepository repository = manager.getRepositoryForFile(file);
            if (repository != null) {
                return repository;
            }
        }
        return manager.getRepositoryForFile(project.getBaseDir());
    }
}
