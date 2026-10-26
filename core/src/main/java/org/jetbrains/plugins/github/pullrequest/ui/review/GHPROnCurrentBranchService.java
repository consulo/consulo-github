// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.review;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.github.localize.GithubLocalize;
import consulo.project.Project;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.lazy.LazyValue;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GHPROnCurrentBranchService implements Disposable {
    private static final Object NO_VALUE = new Object();

    private final Project myProject;
    private final Disposable myCs;

    private final LazyValue<ObservableValue<@Nullable GHPRBranchWidgetViewModel>> myVmState;

    @Inject
    public GHPROnCurrentBranchService(Project project) {
        myProject = project;
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(this, myCs);
        myVmState = LazyValue.atomicNotNull(this::createVmState);
    }

    public static GHPROnCurrentBranchService getInstance(Project project) {
        return project.getInstance(GHPROnCurrentBranchService.class);
    }

    public ObservableValue<@Nullable GHPRBranchWidgetViewModel> getVmState() {
        return myVmState.get();
    }

    private ObservableValue<@Nullable GHPRBranchWidgetViewModel> createVmState() {
        GHPRProjectViewModel vm = GHPRProjectViewModel.getInstance(myProject);
        MutableObservableValue<@Nullable GHPRBranchWidgetViewModel> result = MutableObservableValue.of(null);
        collectLatest(myCs, vm.getConnectedProjectVm(), (projectVm, projectVmCs) -> {
            result.set(null);
            if (projectVm == null) {
                return;
            }
            // we're not interested in intermittent loading/canceled state
            MutableObservableValue<@Nullable ComputedResult<@Nullable GHPRIdentifier>> prOnCurrentBranch = MutableObservableValue.of(null);
            Runnable updatePrOnCurrentBranch = () -> {
                ComputedResult<@Nullable GHPRIdentifier> it = projectVm.getPrOnCurrentBranch().get();
                if (it != null && !it.isInProgress()) {
                    prOnCurrentBranch.set(it);
                }
            };
            Disposer.register(projectVmCs, projectVm.getPrOnCurrentBranch().addListener(it -> updatePrOnCurrentBranch.run())::run);
            updatePrOnCurrentBranch.run();
            collectLatest(projectVmCs, prOnCurrentBranch, (prResult, prCs) -> {
                if (prResult == null) {
                    return;
                }
                GHPRIdentifier id = prResult.getOrNull();
                if (id == null) {
                    result.set(null);
                    return;
                }
                GHPRBranchWidgetViewModel widgetVm = projectVm.acquireBranchWidgetModel(id, prCs);
                showUpdateErrorsIn(widgetVm, prCs);
                result.set(widgetVm);
                Disposer.register(prCs, () -> result.compareAndSet(widgetVm, null));
            });
        });
        vm.loginIfPossible();
        return result;
    }

    private void showUpdateErrorsIn(GHPRBranchWidgetViewModel vm, Disposable cs) {
        vm.addUpdateErrorsListener(cs, error -> myProject.getUIAccess().give(() -> {
            if (!myProject.isDisposed()) {
                GithubNotifications.showError(myProject, GithubLocalize.pullRequestOnBranchUpdateFailedTitle(), error);
            }
        }));
    }

    private static <T extends @Nullable Object> void collectLatest(Disposable parentCs,
                                                                   ObservableValue<T> state,
                                                                   BiConsumer<T, Disposable> consumer) {
        Object lock = new Object();
        Disposable[] current = new Disposable[1];
        Object[] last = {NO_VALUE};
        Consumer<T> onValue = value -> {
            synchronized (lock) {
                if (Disposer.isDisposed(parentCs)) {
                    return;
                }
                if (last[0] != NO_VALUE && Objects.equals(last[0], value)) {
                    return;
                }
                last[0] = value;
                Disposable previous = current[0];
                current[0] = null;
                if (previous != null) {
                    Disposer.dispose(previous);
                }
                Disposable child = Disposable.newDisposable("GHPROnCurrentBranchService.scope");
                if (!Disposer.tryRegister(parentCs, child)) {
                    return;
                }
                current[0] = child;
                consumer.accept(value, child);
            }
        };
        Disposer.register(parentCs, state.addListener(onValue)::run);
        onValue.accept(state.get());
    }

    @Override
    public void dispose() {
    }
}
