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
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.review.toolwindow.ReviewTabViewModel;
import consulo.review.util.ComputedResult;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public sealed interface GHPRToolWindowTabViewModel extends ReviewTabViewModel permits GHPRToolWindowTabViewModel.PullRequest, GHPRToolWindowTabViewModel.NewPullRequest {
    // Disposable bc ReviewToolwindowTabsStateHolder requires it for cleanup
    final class PullRequest implements GHPRToolWindowTabViewModel, Disposable {
        private final Disposable myCs;
        private final GHPRIdentifier myId;
        private final LocalizeValue myDisplayName;
        private final GHPRInfoViewModel myInfoVm;
        private final List<Runnable> myFocusRequestListeners = new CopyOnWriteArrayList<>();

        PullRequest(Disposable parentCs, GHPRToolWindowProjectViewModel projectVm, GHPRIdentifier id) {
            myCs = Disposable.newDisposable(getClass().getName());
            Disposer.register(parentCs, myCs);
            myId = id;
            myDisplayName = LocalizeValue.of("#" + id.number());
            myInfoVm = projectVm.acquireInfoViewModel(id, myCs);
        }

        public GHPRInfoViewModel getInfoVm() {
            return myInfoVm;
        }

        public GHPRIdentifier getId() {
            return myId;
        }

        public Disposable getCs() {
            return myCs;
        }

        @Override
        public LocalizeValue getDisplayName() {
            return myDisplayName;
        }

        public void addFocusRequestListener(Disposable parentDisposable, Runnable listener) {
            myFocusRequestListeners.add(listener);
            Disposer.register(parentDisposable, () -> myFocusRequestListeners.remove(listener));
        }

        public void requestFocus() {
            myFocusRequestListeners.forEach(Runnable::run);
        }

        public void selectCommit(String oid) {
            ComputedResult<GHPRDetailsViewModel> result = myInfoVm.getDetailsVm().get();
            GHPRDetailsViewModel detailsVm = result.getOrNull();
            if (detailsVm != null) {
                detailsVm.getChangesVm().selectCommit(oid);
            }
        }

        @Override
        public void dispose() {
            Disposer.dispose(myCs);
        }
    }

    final class NewPullRequest implements GHPRToolWindowTabViewModel {
        private final GHPRCreateViewModel myCreateVm;
        private final LocalizeValue myDisplayName;
        private final List<Runnable> myFocusRequestListeners = new CopyOnWriteArrayList<>();

        NewPullRequest(GHPRCreateViewModel createVm) {
            myCreateVm = createVm;
            myDisplayName = GithubLocalize.tabTitlePullRequestsNew(createVm.getRepositoryName());
        }

        public GHPRCreateViewModel getCreateVm() {
            return myCreateVm;
        }

        @Override
        public LocalizeValue getDisplayName() {
            return myDisplayName;
        }

        public void addFocusRequestListener(Disposable parentDisposable, Runnable listener) {
            myFocusRequestListeners.add(listener);
            Disposer.register(parentDisposable, () -> myFocusRequestListeners.remove(listener));
        }

        public void requestFocus() {
            myFocusRequestListeners.forEach(Runnable::run);
        }
    }
}
