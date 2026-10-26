// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow;

import consulo.accountManagement.AccountManager;
import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.review.toolwindow.ReviewTabsComponentFactory;
import consulo.ui.Component;
import consulo.ui.Space;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.list.GHPRListPanelFactory;
import org.jetbrains.plugins.github.pullrequest.ui.selector.GHRepositoryAndAccountSelectorComponentFactory;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateComponentFactory;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowTabViewModel;

public class GHPRToolWindowTabComponentFactory implements ReviewTabsComponentFactory<GHPRToolWindowTabViewModel, GHPRToolWindowProjectViewModel> {
    private final Project myProject;
    private final GHPRProjectViewModel myVm;

    public GHPRToolWindowTabComponentFactory(Project project, GHPRProjectViewModel vm) {
        myProject = project;
        myVm = vm;
    }

    @RequiredUIAccess
    @Override
    public Component createEmptyTabContent(Disposable cs) {
        Component selector = new GHRepositoryAndAccountSelectorComponentFactory(myProject, myVm.getSelectorVm(),
            myProject.getApplication().getInstance(AccountManager.class)).create(cs);
        VerticalLayout root = VerticalLayout.create();
        root.add(selector);
        root.paddingBuilder().allSet(Space.LARGE).apply();
        return root;
    }

    @RequiredUIAccess
    @Override
    public Component createReviewListComponent(Disposable cs, GHPRToolWindowProjectViewModel projectVm) {
        return GHPRListPanelFactory.create(myProject, cs, projectVm.getDataContext(), projectVm.getListVm(), projectVm.getProjectName());
    }

    @RequiredUIAccess
    @Override
    public Component createTabComponent(Disposable cs, GHPRToolWindowProjectViewModel projectVm, GHPRToolWindowTabViewModel tabVm) {
        return switch (tabVm) {
            case GHPRToolWindowTabViewModel.PullRequest pullRequest -> createPullRequestComponent(cs, pullRequest);
            case GHPRToolWindowTabViewModel.NewPullRequest newPullRequest -> createNewPullRequestComponent(cs, newPullRequest);
        };
    }

    @RequiredUIAccess
    private Component createPullRequestComponent(Disposable cs, GHPRToolWindowTabViewModel.PullRequest tabVm) {
        return new GHPRViewComponentFactory(myProject, tabVm.getInfoVm()).create(cs);
    }

    @RequiredUIAccess
    private Component createNewPullRequestComponent(Disposable cs, GHPRToolWindowTabViewModel.NewPullRequest tabVm) {
        return GHPRCreateComponentFactory.createIn(cs, tabVm.getCreateVm());
    }
}
