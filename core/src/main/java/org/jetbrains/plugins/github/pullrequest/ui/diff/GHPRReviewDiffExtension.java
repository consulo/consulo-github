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
package org.jetbrains.plugins.github.pullrequest.ui.diff;

import consulo.annotation.component.ExtensionImpl;
import consulo.diff.DiffContext;
import consulo.diff.DiffExtension;
import consulo.diff.FrameDiffTool;
import consulo.diff.request.DiffRequest;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.diff.CodeReviewDiffViewerRenderer;
import consulo.versionControlSystem.review.diff.UnifiedCodeReviewItemPosition;
import jakarta.inject.Inject;
import org.jetbrains.plugins.github.util.GithubSettings;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

@ExtensionImpl
public class GHPRReviewDiffExtension extends DiffExtension {
    private final CodeReviewDiffViewerRenderer myRenderer;

    @Inject
    public GHPRReviewDiffExtension(CodeReviewDiffViewerRenderer renderer) {
        myRenderer = renderer;
    }

    @RequiredUIAccess
    @Override
    public void onViewerCreated(FrameDiffTool.DiffViewer viewer, DiffContext context, DiffRequest request) {
        Project project = context.getProject();
        if (project == null) {
            return;
        }

        GHPRDiffViewModel diffVm = context.getUserData(GHPRDiffViewModel.KEY);
        if (diffVm == null) {
            return;
        }
        RefComparisonChange change = request.getUserData(RefComparisonChange.KEY);
        if (change == null) {
            return;
        }

        installInlays(project, diffVm, change, viewer, context);
    }

    @RequiredUIAccess
    private void installInlays(Project project,
                               GHPRDiffViewModel reviewVm,
                               RefComparisonChange change,
                               FrameDiffTool.DiffViewer viewer,
                               DiffContext context) {
        GithubSettings settings = GithubSettings.getInstance();
        Disposable cs = Disposable.newDisposable(getClass().getName());
        Disposer.register(viewer, cs);

        UIAccess uiAccess = project.getUIAccess();
        ObservableValue<@Nullable GHPRDiffReviewViewModel> changeVmState = reviewVm.getViewModelFor(change);
        Disposable[] changeScope = new Disposable[1];
        Object[] shown = new Object[1];
        Consumer<@Nullable GHPRDiffReviewViewModel> collector = ignored -> uiAccess.give(() -> {
            if (Disposer.isDisposed(cs)) {
                return;
            }
            GHPRDiffReviewViewModel changeVm = changeVmState.get();
            if (changeScope[0] != null && shown[0] == changeVm) {
                return;
            }
            if (changeScope[0] != null) {
                Disposer.dispose(changeScope[0]);
                changeScope[0] = null;
            }
            shown[0] = changeVm;
            context.putUserData(GHPRDiffReviewViewModel.KEY, changeVm);
            if (changeVm == null) {
                return;
            }
            Disposable scope = Disposable.newDisposable("GHPRDiffReviewViewModel.inlays");
            if (!Disposer.tryRegister(cs, scope)) {
                return;
            }
            changeScope[0] = scope;

            if (settings.isAutomaticallyMarkAsViewed()) {
                changeVm.setViewedState(true);
            }
            myRenderer.showCodeReview(scope, viewer, (editorScope, editor, side, mapper) ->
                new DiffEditorModel(editorScope, project, reviewVm, changeVm, mapper::locationToLine, mapper::lineToLocation, line -> {
                    int[] leftAndRight = mapper.lineToUnified(line);
                    return new UnifiedCodeReviewItemPosition(change, leftAndRight[0], leftAndRight[1]);
                }));
        });
        Disposer.register(cs, changeVmState.addListener(collector)::run);
        collector.accept(changeVmState.get());
    }
}
