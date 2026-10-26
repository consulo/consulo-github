// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create;

import consulo.application.util.ListSelection;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.diff.model.CodeReviewDiffProcessorState;
import consulo.versionControlSystem.review.diff.model.CodeReviewDiffProcessorViewModel;
import consulo.versionControlSystem.review.diff.model.DiffViewerScrollRequest;
import consulo.versionControlSystem.review.diff.model.PreLoadingCodeReviewAsyncDiffViewModelDelegate;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class GHPRCreateDiffViewModel implements CodeReviewDiffProcessorViewModel<GHPRCreateDiffChangeViewModel> {
    private final Disposable myCs;

    private final PreLoadingCodeReviewAsyncDiffViewModelDelegate<RefComparisonChange, GHPRCreateDiffChangeViewModel> myDelegate;

    public GHPRCreateDiffViewModel(Project project, Disposable parentCs) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);

        myDelegate = PreLoadingCodeReviewAsyncDiffViewModelDelegate.create(myCs,
            ObservableValue.<@Nullable ComputedResult<Boolean>>of(ComputedResult.success(Boolean.TRUE)),
            (scope, unit, change) -> new GHPRCreateDiffChangeViewModel(project, scope, change));
    }

    public void handleSelection(Disposable parentDisposable, Consumer<ListSelection<RefComparisonChange>> listener) {
        myDelegate.handleSelection(parentDisposable, listener);
    }

    public void showChanges(ListSelection<RefComparisonChange> changes) {
        showChanges(changes, null);
    }

    public void showChanges(ListSelection<RefComparisonChange> changes, @Nullable DiffViewerScrollRequest scrollRequest) {
        myDelegate.showChanges(changes, scrollRequest);
    }

    @Override
    public ObservableValue<@Nullable ComputedResult<CodeReviewDiffProcessorState<GHPRCreateDiffChangeViewModel>>> getChanges() {
        return myDelegate.getChanges();
    }

    @Override
    public void showChange(GHPRCreateDiffChangeViewModel change, @Nullable DiffViewerScrollRequest scrollRequest) {
        myDelegate.showChange(change.getChange(), scrollRequest);
    }

    @Override
    public void showChange(int changeIdx, @Nullable DiffViewerScrollRequest scrollRequest) {
        ComputedResult<CodeReviewDiffProcessorState<GHPRCreateDiffChangeViewModel>> result = getChanges().get();
        CodeReviewDiffProcessorState<GHPRCreateDiffChangeViewModel> state = result == null ? null : result.getOrNull();
        if (state == null) {
            return;
        }
        List<GHPRCreateDiffChangeViewModel> list = state.getSelectedChanges().getList();
        if (changeIdx < 0 || changeIdx >= list.size()) {
            return;
        }
        showChange(list.get(changeIdx), scrollRequest);
    }
}
