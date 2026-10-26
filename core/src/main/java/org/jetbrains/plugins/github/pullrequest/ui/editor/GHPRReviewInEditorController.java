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
package org.jetbrains.plugins.github.pullrequest.ui.editor;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorKind;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.project.Project;
import consulo.review.diff.DiscussionsViewOption;
import consulo.review.editor.CodeReviewCommentableEditorModel;
import consulo.review.editor.CodeReviewEditorRenderer;
import consulo.review.editor.CodeReviewNavigableEditorViewModel;
import consulo.review.editor.ReviewInEditorUtil;
import consulo.review.util.ComputedResult;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.AnAction;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.dataholder.Key;
import consulo.versionControlSystem.FilePath;
import consulo.versionControlSystem.review.editor.CodeReviewEditorDocumentTracker;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GHPRReviewInEditorController implements Disposable {
    private static final Key<Disposable> REVIEW_DISPOSABLE = Key.create("GitHub.PullRequest.Editor.Review.Disposable");

    private static final Object NO_VALUE = new Object();

    private final Project myProject;
    private final GithubPullRequestsProjectUISettings mySettings;
    private final CodeReviewEditorRenderer myRenderer;
    private final CodeReviewEditorDocumentTracker myDocumentTracker;
    private final ActionManager myActionManager;

    @Inject
    public GHPRReviewInEditorController(Project project,
                                        GithubPullRequestsProjectUISettings settings,
                                        CodeReviewEditorRenderer renderer,
                                        CodeReviewEditorDocumentTracker documentTracker,
                                        ActionManager actionManager) {
        myProject = project;
        mySettings = settings;
        myRenderer = renderer;
        myDocumentTracker = documentTracker;
        myActionManager = actionManager;
    }

    public static GHPRReviewInEditorController getInstance(Project project) {
        return project.getInstance(GHPRReviewInEditorController.class);
    }

    void setupReview(Editor editor) {
        if (!isPotentialEditor(editor)) {
            return;
        }
        VirtualFile file = editor.getVirtualFile();
        if (file == null) {
            return;
        }

        Disposable editorDisposable = Disposable.newDisposable("GHPRReviewInEditorController.editor");
        if (!Disposer.tryRegister(this, editorDisposable)) {
            return;
        }
        editor.putUserData(REVIEW_DISPOSABLE, editorDisposable);

        UIAccess uiAccess = myProject.getUIAccess();
        ObservableValue<@Nullable GHPRConnectedProjectViewModel> connectedProjectVm =
            GHPRProjectViewModel.getInstance(myProject).getConnectedProjectVm();
        collectLatest(editorDisposable, connectedProjectVm, uiAccess, (projectVm, projectVmCs) -> {
            if (projectVm == null) {
                return;
            }
            MutableObservableValue<@Nullable GHPRIdentifier> prOnCurrentBranch = MutableObservableValue.of(getId(projectVm));
            Disposer.register(projectVmCs, projectVm.getPrOnCurrentBranch().addListener(it -> prOnCurrentBranch.set(getId(projectVm)))::run);
            collectLatest(projectVmCs, prOnCurrentBranch, uiAccess, (id, reviewCs) -> {
                if (id == null) {
                    return;
                }
                GHPRReviewInEditorViewModel reviewVm = projectVm.acquireEditorReviewViewModel(id, reviewCs);
                collectLatest(reviewCs, reviewVm.getViewModelFor(file, reviewCs), uiAccess, (fileVm, fileCs) -> {
                    if (fileVm != null) {
                        setupFileReview(fileCs, uiAccess, reviewVm, fileVm, editor);
                    }
                });
            });
        });
    }

    void releaseReview(Editor editor) {
        Disposable disposable = editor.getUserData(REVIEW_DISPOSABLE);
        if (disposable != null) {
            editor.putUserData(REVIEW_DISPOSABLE, null);
            Disposer.dispose(disposable);
        }
    }

    private static @Nullable GHPRIdentifier getId(GHPRConnectedProjectViewModel projectVm) {
        ComputedResult<@Nullable GHPRIdentifier> result = projectVm.getPrOnCurrentBranch().get();
        return result == null ? null : result.getOrNull();
    }

    @RequiredUIAccess
    private void setupFileReview(Disposable cs,
                                 UIAccess uiAccess,
                                 GHPRReviewInEditorViewModel reviewVm,
                                 GHPRReviewFileEditorViewModel fileVm,
                                 Editor editor) {
        List<AnAction> actions = new ArrayList<>();
        AnAction previousComment = myActionManager.getAction("CodeReview.PreviousComment");
        if (previousComment != null) {
            actions.add(previousComment);
        }
        AnAction nextComment = myActionManager.getAction("CodeReview.NextComment");
        if (nextComment != null) {
            actions.add(nextComment);
        }
        ReviewInEditorUtil.showReviewToolbarWithActions(cs, reviewVm, editor, actions.toArray(AnAction.EMPTY_ARRAY));

        editor.putUserData(GHPRReviewFileEditorViewModel.KEY, fileVm);
        Disposer.register(cs, () -> {
            if (editor.getUserData(GHPRReviewFileEditorViewModel.KEY) == fileVm) {
                editor.putUserData(GHPRReviewFileEditorViewModel.KEY, null);
            }
        });

        MutableObservableValue<Boolean> enabledState = MutableObservableValue.of(isReviewEnabled(reviewVm));
        Disposer.register(cs, reviewVm.getDiscussionsViewOption().addListener(it -> enabledState.set(isReviewEnabled(reviewVm)))::run);
        Disposer.register(cs, reviewVm.getUpdateRequired().addListener(it -> enabledState.set(isReviewEnabled(reviewVm)))::run);

        collectLatest(cs, enabledState, uiAccess, (enabled, reviewCs) -> {
            if (enabled) {
                showReview(reviewCs, uiAccess, fileVm, editor);
            }
        });
    }

    private static boolean isReviewEnabled(GHPRReviewInEditorViewModel reviewVm) {
        boolean enabled = reviewVm.getDiscussionsViewOption().get() != DiscussionsViewOption.DONT_SHOW;
        boolean synced = !reviewVm.getUpdateRequired().get();
        return enabled && synced;
    }

    private void showReview(Disposable cs, UIAccess uiAccess, GHPRReviewFileEditorViewModel fileVm, Editor editor) {
        ObservableValue<@Nullable ComputedResult<CharSequence>> originalContent = fileVm.getOriginalContent();
        AtomicBoolean shown = new AtomicBoolean();
        Consumer<@Nullable ComputedResult<CharSequence>> onContent = result -> {
            CharSequence reviewHeadContent = result == null || !result.isSuccess() ? null : result.getOrNull();
            if (reviewHeadContent == null || !shown.compareAndSet(false, true)) {
                return;
            }
            uiAccess.give(() -> {
                if (!Disposer.isDisposed(cs)) {
                    doShowReview(cs, fileVm, editor, reviewHeadContent);
                }
            });
        };
        Disposer.register(cs, originalContent.addListener(onContent)::run);
        onContent.accept(originalContent.get());
    }

    @RequiredUIAccess
    private void doShowReview(Disposable cs, GHPRReviewFileEditorViewModel fileVm, Editor editor, CharSequence reviewHeadContent) {
        GHPRReviewFileEditorModel model = new GHPRReviewFileEditorModel(cs, myProject, mySettings, fileVm, (changeToShow, lineIdx) -> {
            FilePath filePath = changeToShow.filePathAfter();
            VirtualFile file = filePath == null ? null : filePath.getVirtualFile();
            if (file == null) {
                return;
            }
            OpenFileDescriptorFactory.getInstance(myProject).newBuilder(file).line(lineIdx).build().navigate(true);
        });

        myDocumentTracker.trackDocumentDiff(cs, reviewHeadContent, editor.getDocument(), model::setPostReviewChanges);

        myRenderer.setupIn(cs, editor, model);

        editor.putUserData(CodeReviewCommentableEditorModel.KEY, model);
        editor.putUserData(CodeReviewNavigableEditorViewModel.KEY, model);
        Disposer.register(cs, () -> {
            if (editor.getUserData(CodeReviewCommentableEditorModel.KEY) == model) {
                editor.putUserData(CodeReviewCommentableEditorModel.KEY, null);
            }
            if (editor.getUserData(CodeReviewNavigableEditorViewModel.KEY) == model) {
                editor.putUserData(CodeReviewNavigableEditorViewModel.KEY, null);
            }
        });
    }

    private static <T extends @Nullable Object> void collectLatest(Disposable parentCs,
                                                                   ObservableValue<T> state,
                                                                   UIAccess uiAccess,
                                                                   BiConsumer<T, Disposable> consumer) {
        Disposable[] current = new Disposable[1];
        Object[] last = {NO_VALUE};
        Consumer<T> onValue = value -> uiAccess.give(() -> {
            if (Disposer.isDisposed(parentCs)) {
                return;
            }
            T latest = state.get();
            if (last[0] != NO_VALUE && Objects.equals(last[0], latest)) {
                return;
            }
            last[0] = latest;
            Disposable previous = current[0];
            current[0] = null;
            if (previous != null) {
                Disposer.dispose(previous);
            }
            Disposable child = Disposable.newDisposable("GHPRReviewInEditorController.scope");
            if (!Disposer.tryRegister(parentCs, child)) {
                return;
            }
            current[0] = child;
            consumer.accept(latest, child);
        });
        Disposer.register(parentCs, state.addListener(onValue)::run);
        onValue.accept(state.get());
    }

    private static boolean isPotentialEditor(Editor editor) {
        return editor.getEditorKind() == EditorKind.MAIN_EDITOR && editor.getVirtualFile() != null;
    }

    @Override
    public void dispose() {
    }
}
