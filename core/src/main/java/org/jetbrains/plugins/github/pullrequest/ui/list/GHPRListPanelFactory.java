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
package org.jetbrains.plugins.github.pullrequest.ui.list;

import consulo.dataContext.UiDataProvider;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.review.list.ReviewListComponentFactory;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.layout.DockLayout;
import org.jetbrains.plugins.github.pullrequest.GHPRListViewModel;
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRSearchPanelFactory;

public final class GHPRListPanelFactory {
    private GHPRListPanelFactory() {
    }

    @RequiredUIAccess
    public static Component create(Project project,
                                   Disposable cs,
                                   GHPRDataContext dataContext,
                                   GHPRListViewModel listVm,
                                   LocalizeValue projectName) {
        ReviewListComponentFactory listComponentFactory = project.getApplication().getInstance(ReviewListComponentFactory.class);
        ActionGroup popupGroup = (ActionGroup) ActionManager.getInstance().getAction("Github.PullRequest.ToolWindow.List.Popup");
        Component list = listComponentFactory.create(cs, projectName, listVm, listVm::open, popupGroup, (sink, pr) -> {
            sink.set(GHPRActionKeys.PULL_REQUEST_ID, pr == null ? null : pr.getPrId());
            sink.set(GHPRActionKeys.PULL_REQUEST_URL, pr == null ? null : pr.getUrl());
        });

        Component searchPanel = new GHPRSearchPanelFactory(listVm.getSearchVm(), listVm.getAvatarIconsProvider()).create(cs);

        DockLayout root = DockLayout.create();
        root.top(searchPanel);
        root.center(list);

        GHPRListController listController = new GHPRListControllerImpl(listVm);
        root.putUserData(UiDataProvider.KEY, sink -> sink.set(GHPRActionKeys.PULL_REQUESTS_LIST_CONTROLLER, listController));
        return root;
    }
}
