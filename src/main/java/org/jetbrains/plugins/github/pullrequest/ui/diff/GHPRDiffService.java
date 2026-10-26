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
package org.jetbrains.plugins.github.pullrequest.ui.diff;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.dataContext.UiDataProvider;
import consulo.diff.DiffEditorViewer;
import consulo.diff.DiffUserDataKeys;
import consulo.diff.combined.CombinedDiffKeys;
import consulo.diff.combined.CombinedDiffToggle;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.util.KeyValuePair;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.change.PresentableChange;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.diff.AsyncDiffRequestProcessorFactory;
import consulo.versionControlSystem.review.util.RefComparisonChangeUtil;
import consulo.virtualFileSystem.status.FileStatus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.pullrequest.comment.action.GHPRDiffReviewThreadsReloadAction;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateDiffChangeViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateDiffViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GHPRDiffService implements Disposable {
    private final Project myProject;

    @Inject
    public GHPRDiffService(Project project) {
        myProject = project;
    }

    public static GHPRDiffService getInstance(Project project) {
        return project.getInstance(GHPRDiffService.class);
    }

    @RequiredUIAccess
    public DiffEditorViewer createGHPRDiffProcessor(GHRepositoryCoordinates repository, GHPRIdentifier pullRequest) {
        DiffEditorViewer processor;
        if (CombinedDiffToggle.DEFAULT.isCombinedDiffEnabled()) {
            processor = createCombinedDiffProcessor(myProject, this, repository, pullRequest);
        }
        else {
            processor = createDiffRequestProcessor(myProject, this, repository, pullRequest);
        }
        processor.getContext().putUserData(CombinedDiffKeys.COMBINED_DIFF_TOGGLE, CombinedDiffToggle.DEFAULT);
        return processor;
    }

    @RequiredUIAccess
    public DiffEditorViewer createGHNewPRDiffProcessor(GHRepositoryCoordinates repository) {
        DiffEditorViewer processor;
        if (CombinedDiffToggle.DEFAULT.isCombinedDiffEnabled()) {
            processor = createCombinedDiffProcessor(myProject, this, repository);
        }
        else {
            processor = createDiffRequestProcessor(myProject, this, repository);
        }
        processor.getContext().putUserData(CombinedDiffKeys.COMBINED_DIFF_TOGGLE, CombinedDiffToggle.DEFAULT);
        return processor;
    }

    @RequiredUIAccess
    public static DiffEditorViewer createDiffRequestProcessor(Project project,
                                                              Disposable cs,
                                                              GHRepositoryCoordinates repository,
                                                              GHPRIdentifier pullRequest) {
        Disposable vmScope = createVmScope(cs);
        ObservableValue<@Nullable GHPRDiffViewModel> vmFlow = findDiffVm(project, vmScope, repository, pullRequest);
        DiffEditorViewer processor = getFactory(project).createIn(cs, project, vmFlow,
            GHPRDiffService::createDiffContext,
            (GHPRDiffChangeViewModel changeVm) -> getChangeDiffVmPresentation(changeVm));
        return bindVmScope(processor, vmScope);
    }

    @RequiredUIAccess
    private static DiffEditorViewer createCombinedDiffProcessor(Project project,
                                                                Disposable cs,
                                                                GHRepositoryCoordinates repository,
                                                                GHPRIdentifier pullRequest) {
        Disposable vmScope = createVmScope(cs);
        ObservableValue<@Nullable GHPRDiffViewModel> vmFlow = findDiffVm(project, vmScope, repository, pullRequest);
        DiffEditorViewer processor = getFactory(project).createCombinedIn(cs, project, vmFlow,
            GHPRDiffService::createDiffContext,
            (GHPRDiffChangeViewModel changeVm) -> getChangeDiffVmPresentation(changeVm));
        return bindVmScope(processor, vmScope);
    }

    private static List<KeyValuePair<?>> createDiffContext(GHPRDiffViewModel vm) {
        ActionManager actionManager = ActionManager.getInstance();
        List<KeyValuePair<?>> result = new ArrayList<>();
        result.add(new KeyValuePair<>(GHPRDiffViewModel.KEY, vm));
        UiDataProvider dataProvider = sink -> {
            sink.set(GHPRDiffViewModel.DATA_KEY, vm);
            sink.set(GHPRReviewViewModel.DATA_KEY, vm.getReviewVm());
        };
        result.add(new KeyValuePair<>(DiffUserDataKeys.DATA_PROVIDER, dataProvider));
        List<AnAction> contextActions = new ArrayList<>();
        contextActions.add(new GHPRDiffReviewThreadsReloadAction());
        for (String actionId : List.of("Github.PullRequest.Review.Submit", "CodeReview.PreviousComment", "CodeReview.NextComment")) {
            AnAction action = actionManager.getAction(actionId);
            if (action != null) {
                contextActions.add(action);
            }
        }
        result.add(new KeyValuePair<>(DiffUserDataKeys.CONTEXT_ACTIONS, List.copyOf(contextActions)));
        return result;
    }

    private static PresentableChange getChangeDiffVmPresentation(GHPRDiffChangeViewModel changeVm) {
        return createPresentation(changeVm.getChange());
    }

    @RequiredUIAccess
    private static DiffEditorViewer createDiffRequestProcessor(Project project, Disposable cs, GHRepositoryCoordinates repository) {
        Disposable vmScope = createVmScope(cs);
        ObservableValue<@Nullable GHPRCreateDiffViewModel> vmFlow = findDiffVm(project, vmScope, repository);
        DiffEditorViewer processor = getFactory(project).createIn(cs, project, vmFlow,
            vm -> List.of(),
            (GHPRCreateDiffChangeViewModel changeVm) -> getChangeDiffVmPresentation(changeVm));
        return bindVmScope(processor, vmScope);
    }

    @RequiredUIAccess
    private static DiffEditorViewer createCombinedDiffProcessor(Project project, Disposable cs, GHRepositoryCoordinates repository) {
        Disposable vmScope = createVmScope(cs);
        ObservableValue<@Nullable GHPRCreateDiffViewModel> vmFlow = findDiffVm(project, vmScope, repository);
        DiffEditorViewer processor = getFactory(project).createCombinedIn(cs, project, vmFlow,
            vm -> List.of(),
            (GHPRCreateDiffChangeViewModel changeVm) -> getChangeDiffVmPresentation(changeVm));
        return bindVmScope(processor, vmScope);
    }

    private static PresentableChange getChangeDiffVmPresentation(GHPRCreateDiffChangeViewModel changeVm) {
        return createPresentation(changeVm.getChange());
    }

    private static PresentableChange createPresentation(RefComparisonChange change) {
        return new PresentableChange() {
            @Override
            public FilePath getFilePath() {
                return RefComparisonChangeUtil.getFilePath(change);
            }

            @Override
            public FileStatus getFileStatus() {
                return RefComparisonChangeUtil.getFileStatus(change);
            }
        };
    }

    private static AsyncDiffRequestProcessorFactory getFactory(Project project) {
        return project.getApplication().getInstance(AsyncDiffRequestProcessorFactory.class);
    }

    private static Disposable createVmScope(Disposable cs) {
        Disposable vmScope = Disposable.newDisposable("GHPRDiffService.vm");
        Disposer.register(cs, vmScope);
        return vmScope;
    }

    private static DiffEditorViewer bindVmScope(DiffEditorViewer processor, Disposable vmScope) {
        Disposer.register(processor, () -> Disposer.dispose(vmScope));
        return processor;
    }

    private static ObservableValue<@Nullable GHPRDiffViewModel> findDiffVm(Project project,
                                                                           Disposable cs,
                                                                           GHRepositoryCoordinates repository,
                                                                           GHPRIdentifier pullRequest) {
        GHPRProjectViewModel projectVm = project.getInstanceIfCreated(GHPRProjectViewModel.class);
        if (projectVm == null) {
            return ObservableValue.of(null);
        }
        ObservableValue<@Nullable GHPRConnectedProjectViewModel> connectedProjectVm = projectVm.getConnectedProjectVm();
        MutableObservableValue<@Nullable GHPRDiffViewModel> result = MutableObservableValue.of(null);
        Disposable[] hostCs = new Disposable[1];
        Consumer<@Nullable GHPRConnectedProjectViewModel> handler = it -> {
            synchronized (hostCs) {
                Disposable previous = hostCs[0];
                hostCs[0] = null;
                if (it != null && it.getRepository().equals(repository)) {
                    Disposable host = Disposable.newDisposable("GHPRDiffViewModel host");
                    if (Disposer.tryRegister(cs, host)) {
                        hostCs[0] = host;
                        result.set(it.acquireDiffViewModel(pullRequest, host));
                    }
                    else {
                        result.set(null);
                    }
                }
                else {
                    result.set(null);
                }
                if (previous != null) {
                    Disposer.dispose(previous);
                }
            }
        };
        Disposer.register(cs, connectedProjectVm.addListener(handler)::run);
        handler.accept(connectedProjectVm.get());
        return result;
    }

    private static ObservableValue<@Nullable GHPRCreateDiffViewModel> findDiffVm(Project project,
                                                                                 Disposable cs,
                                                                                 GHRepositoryCoordinates repository) {
        GHPRProjectViewModel projectVm = project.getInstanceIfCreated(GHPRProjectViewModel.class);
        if (projectVm == null) {
            return ObservableValue.of(null);
        }
        ObservableValue<@Nullable GHPRConnectedProjectViewModel> connectedProjectVm = projectVm.getConnectedProjectVm();
        Function<@Nullable GHPRConnectedProjectViewModel, @Nullable GHPRCreateDiffViewModel> mapper = it -> {
            if (it == null || !it.getRepository().equals(repository)) {
                return null;
            }
            GHPRCreateViewModel createVm = it.getCreateVmOrNull();
            return createVm == null ? null : createVm.getDiffVm();
        };
        MutableObservableValue<@Nullable GHPRCreateDiffViewModel> result = MutableObservableValue.of(mapper.apply(connectedProjectVm.get()));
        Disposer.register(cs, connectedProjectVm.addListener(it -> result.set(mapper.apply(it)))::run);
        return result;
    }

    @Override
    public void dispose() {
    }
}
