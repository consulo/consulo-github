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
package org.jetbrains.plugins.github.extensions;

import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.Task;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.function.ThrowableFunction;
import git4idea.checkout.GitCheckoutPage;
import git4idea.commands.Git;
import git4idea.remote.GitRememberedInputs;
import jakarta.annotation.Nonnull;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubRepo;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationCanceledException;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author VISTALL
 * @since 2026-08-25
 */
public class GithubCheckoutPage extends GitCheckoutPage {
    private static final String SEPARATOR = "-----------------------------------------------";

    public GithubCheckoutPage(Project project, Disposable uiDisposable, Git git) {
        super(project, uiDisposable, git);
    }

    @Override
    @RequiredUIAccess
    public Component createComponent(Context context) {
        Component component = super.createComponent(context);

        loadAvailableRepos();

        return component;
    }

    @RequiredUIAccess
    private void loadAvailableRepos() {
        Project project = myProject;

        new Task.Backgroundable(project, LocalizeValue.localizeTODO("Access to GitHub"), true) {
            private final List<String> myCloneUrls = new ArrayList<>();

            @Override
            public void run(@Nonnull ProgressIndicator indicator) {
                List<GithubRepo> availableRepos;
                try {
                    availableRepos = GithubUtil.runWithValidAuth(
                        project,
                        indicator,
                        (ThrowableFunction<GithubAuthData, List<GithubRepo>, IOException>)GithubApiUtil::getAvailableRepos
                    );
                }
                catch (GithubAuthenticationCanceledException e) {
                    return;
                }
                catch (IOException e) {
                    GithubNotifications.showError(
                        project,
                        LocalizeValue.localizeTODO("Couldn't get the list of GitHub repositories"),
                        e
                    );
                    return;
                }

                availableRepos.sort(Comparator.comparing(GithubRepo::getUserName).thenComparing(GithubRepo::getName));

                for (GithubRepo repo : availableRepos) {
                    myCloneUrls.add(repo.getCloneUrl());
                }
            }

            @Override
            @RequiredUIAccess
            public void onSuccess() {
                if (myCloneUrls.isEmpty()) {
                    return;
                }

                List<String> history = new ArrayList<>(myCloneUrls);
                history.add(SEPARATOR);
                history.addAll(GitRememberedInputs.getInstance().getVisitedUrls());

                setUrlHistory(history);
            }
        }.queue();
    }
}
