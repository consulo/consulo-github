// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import consulo.dataContext.UiDataProvider;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.github.localize.GithubLocalize;
import consulo.project.Project;
import consulo.review.list.error.ErrorStatusPanelFactory;
import consulo.review.list.error.ErrorStatusPresenter;
import consulo.review.util.ComputedResult;
import consulo.review.util.ReviewUIUtil;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys;
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsComponentFactory;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsLoadingViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRInfoViewModel;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public class GHPRViewComponentFactory {
    private final Project myProject;
    private final GHPRInfoViewModel myVm;

    public GHPRViewComponentFactory(Project project, GHPRInfoViewModel vm) {
        myProject = project;
        myVm = vm;
    }

    @RequiredUIAccess
    public Component create(Disposable cs) {
        Component component = createInfoLoadingComponent(cs);
        registerDataProvider(component, myVm);
        return component;
    }

    @RequiredUIAccess
    private Component createInfoLoadingComponent(Disposable cs) {
        DockLayout wrapper = DockLayout.create();
        AtomicReference<@Nullable Disposable> contentScope = new AtomicReference<>();
        Disposer.register(cs, () -> disposeContent(contentScope));
        ReviewUIUtil.bind(cs, myVm.getDetailsVm(), (ComputedResult<GHPRDetailsViewModel> result) -> {
            disposeContent(contentScope);
            Disposable contentCs = Disposable.newDisposable("GHPRViewComponentFactory.info");
            Disposer.register(cs, contentCs);
            contentScope.set(contentCs);
            wrapper.center(result.fold(
                () -> GHPRDetailsComponentFactory.createLoadingLabel(),
                detailsVm -> createInfoComponent(contentCs, detailsVm),
                error -> createInfoErrorComponent(myProject, myVm, error)
            ));
        });
        return wrapper;
    }

    private static void disposeContent(AtomicReference<@Nullable Disposable> contentScope) {
        Disposable previous = contentScope.getAndSet(null);
        if (previous != null) {
            Disposer.dispose(previous);
        }
    }

    @RequiredUIAccess
    private Component createInfoComponent(Disposable cs, GHPRDetailsViewModel detailsVm) {
        return GHPRDetailsComponentFactory.create(cs, myProject, detailsVm);
    }

    public static void registerDataProvider(Component component, GHPRInfoViewModel vm) {
        component.putUserData(UiDataProvider.KEY, sink -> {
            sink.set(GHPRActionKeys.PULL_REQUEST_ID, vm.getPullRequest());
            sink.set(GHPRActionKeys.PULL_REQUEST_URL, vm.getPullRequestUrl());
            sink.set(GHPRDetailsLoadingViewModel.DATA_KEY, vm);
        });
    }

    @RequiredUIAccess
    public static Component createInfoErrorComponent(Project project, GHPRInfoViewModel vm, Throwable error) {
        ErrorStatusPresenter<Throwable> errorPresenter = ErrorStatusPresenter.simple(
            GithubLocalize.cannotLoadDetails(),
            null,
            vm.getDetailsLoadingErrorHandler()::getActionForError
        );
        return project.getApplication().getInstance(ErrorStatusPanelFactory.class).create(error, errorPresenter);
    }
}
