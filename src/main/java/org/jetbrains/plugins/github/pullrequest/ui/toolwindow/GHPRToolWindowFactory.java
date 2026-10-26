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

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.disposer.Disposer;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ui.wm.ToolWindowFactory;
import consulo.review.icon.ReviewIconGroup;
import consulo.review.toolwindow.ReviewToolwindowTabsManager;
import consulo.review.toolwindow.ToolwindowUtils;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.ui.image.Image;
import jakarta.inject.Inject;
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys;
import org.jetbrains.plugins.github.pullrequest.action.GHPRCreatePullRequestAction;
import org.jetbrains.plugins.github.pullrequest.action.GHPRSwitchRemoteAction;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowViewModel;

@ExtensionImpl
public class GHPRToolWindowFactory implements ToolWindowFactory, DumbAware {
    public static final String ID = "Pull Requests";

    private final ReviewToolwindowTabsManager myTabsManager;

    @Inject
    public GHPRToolWindowFactory(ReviewToolwindowTabsManager tabsManager) {
        myTabsManager = tabsManager;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void init(Project project, ToolWindow toolWindow) {
        GHPRProjectViewModel vm = GHPRProjectViewModel.getInstance(project);
        toolWindow.setAvailable(vm.isAvailable().get());
        Runnable unsubscribe = vm.isAvailable().addListener(available -> project.getUIAccess().give(() -> toolWindow.setAvailable(available)));
        Disposer.register(toolWindow.getContentManager(), unsubscribe::run);
        vm.addActivationRequestListener(toolWindow.getContentManager(), () -> project.getUIAccess().give(() -> toolWindow.activate(null)));
    }

    @RequiredUIAccess
    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        toolWindow.setTitleActions(ActionManager.getInstance().getAction(GHPRCreatePullRequestAction.class));
        toolWindow.setAdditionalGearActions(new DefaultActionGroup(new GHPRSwitchRemoteAction()));

        GHPRProjectViewModel vm = GHPRProjectViewModel.getInstance(project);
        GHPRToolWindowViewModel reviewTwVm = new GHPRToolWindowViewModel(vm);

        toolWindow.getContentManager().addUiDataProvider(sink -> {
            sink.set(GHPRActionKeys.PULL_REQUESTS_PROJECT_VM, vm);
            sink.set(GHPRActionKeys.PULL_REQUESTS_CONNECTED_PROJECT_VM, vm.getConnectedProjectVm().get());
        });

        // so it's not closed when all content is removed
        ToolwindowUtils.dontHideOnEmptyContent(toolWindow);
        GHPRToolWindowTabComponentFactory componentFactory = new GHPRToolWindowTabComponentFactory(project, vm);
        myTabsManager.manageReviewToolwindowTabs(toolWindow.getContentManager(), project, toolWindow, reviewTwVm, componentFactory,
            GithubLocalize.toolwindowStripePull_requests());
    }

    @Override
    public boolean shouldBeAvailable(Project project) {
        return false;
    }

    @Override
    public ToolWindowAnchor getAnchor() {
        return ToolWindowAnchor.LEFT;
    }

    @Override
    public Image getIcon() {
        return ReviewIconGroup.pullrequestopen();
    }

    @Override
    public LocalizeValue getDisplayName() {
        return GithubLocalize.toolwindowStripePull_requests();
    }
}
