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
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.util.ComputedResult;
import consulo.review.util.FutureUtil;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsLoadingViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModelImpl;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

public class GHPRInfoViewModel implements GHPRDetailsLoadingViewModel {
    private final Disposable myCs;
    private final Project myProject;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;
    private final BiConsumer<@Nullable GHPRIdentifier, Boolean> myOpenPullRequestDiff;

    private final GHPRIdentifier myPullRequest;
    private volatile @Nullable String myPullRequestUrl;

    //TODO: rework to detailsComputationFlow
    private final MutableObservableValue<ComputedResult<GHPRDetailsViewModel>> myDetailsVm = MutableObservableValue.of(ComputedResult.loading());
    private @Nullable GHPRDetailsViewModelImpl myVm;
    private boolean myStarted;

    private final GHApiLoadingErrorHandler myDetailsLoadingErrorHandler;

    public GHPRInfoViewModel(Project project,
                             Disposable parentCs,
                             GHPRDataContext dataContext,
                             GHPRDataProvider dataProvider,
                             BiConsumer<@Nullable GHPRIdentifier, Boolean> openPullRequestDiff) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myProject = project;
        myDataContext = dataContext;
        myDataProvider = dataProvider;
        myOpenPullRequestDiff = openPullRequestDiff;
        myPullRequest = dataProvider.getId();

        myDetailsLoadingErrorHandler = new GHApiLoadingErrorHandler(project, dataContext.getSecurityService().getAccount(), () -> {
            dataProvider.getDetailsData().signalDetailsNeedReload();
            dataProvider.getDetailsData().signalMergeabilityNeedsReload();
        });
    }

    public GHPRIdentifier getPullRequest() {
        return myPullRequest;
    }

    public @Nullable String getPullRequestUrl() {
        return myPullRequestUrl;
    }

    public GHApiLoadingErrorHandler getDetailsLoadingErrorHandler() {
        return myDetailsLoadingErrorHandler;
    }

    @Override
    public ObservableValue<ComputedResult<GHPRDetailsViewModel>> getDetailsVm() {
        synchronized (this) {
            if (!myStarted) {
                myStarted = true;
                myDataProvider.getDetailsData().addDetailsNeedReloadListener(myCs, this::load);
                load();
            }
        }
        return myDetailsVm;
    }

    private void load() {
        GHPRDetailsViewModelImpl current;
        synchronized (this) {
            current = myVm;
        }
        if (current == null) {
            myDetailsVm.set(ComputedResult.loading());
        }
        else {
            current.isUpdating().set(true);
        }
        myDataProvider.getDetailsData().loadDetails().whenComplete((details, error) -> {
            GHPRDetailsViewModelImpl vm;
            synchronized (this) {
                vm = myVm;
            }
            if (error != null) {
                if (FutureUtil.isCancellation(error)) {
                    if (vm != null) {
                        vm.isUpdating().set(false);
                    }
                    return;
                }
                if (vm != null) {
                    vm.destroy();
                }
                synchronized (this) {
                    myVm = null;
                }
                myDetailsVm.set(ComputedResult.failure(FutureUtil.unwrap(error)));
                return;
            }
            myPullRequestUrl = details.getUrl();
            if (vm == null) {
                GHPRDetailsViewModelImpl newVm = new GHPRDetailsViewModelImpl(myProject, myCs, myDataContext, myDataProvider, details, myOpenPullRequestDiff);
                synchronized (this) {
                    myVm = newVm;
                }
                myDetailsVm.set(ComputedResult.success(newVm));
            }
            else {
                vm.update(details);
                vm.isUpdating().set(false);
            }
        });
    }

    @Override
    public void requestReload() {
        myDataProvider.getDetailsData().signalDetailsNeedReload();
        myDataProvider.getDetailsData().signalMergeabilityNeedsReload();
        myDataProvider.getReviewData().signalPendingReviewNeedsReload();
        myDataProvider.getReviewData().signalThreadsNeedReload();
        myDataProvider.getChangesData().signalChangesNeedReload();
        myDataProvider.getViewedStateData().signalViewedStateNeedsReload();
    }
}
