// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.pullrequest;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.github.localize.GithubLocalize;
import consulo.project.Project;
import consulo.review.details.model.CodeReviewDetailsTabViewModel;
import consulo.review.timeline.CodeReviewTimelineComponentFactory;
import consulo.review.util.ComputedResult;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.dataholder.UserDataHolderBase;
import kava.beans.PropertyChangeListener;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineTabViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.timeline.GHPRTimelineViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRInfoViewModel;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class GHPRTimelineFileEditor extends UserDataHolderBase implements FileEditor {
    private final GHPRTimelineVirtualFile myFile;
    private final Disposable myCs = Disposable.newDisposable("GitHub Pull Request Timeline UI");
    private final MutableObservableValue<ComputedResult<CodeReviewDetailsTabViewModel>> myTabVm =
        MutableObservableValue.of(ComputedResult.loading());
    private volatile @Nullable GHPRTimelineViewModel myTimelineVm;
    private @Nullable Component myContent;

    @RequiredUIAccess
    public GHPRTimelineFileEditor(GHPRTimelineVirtualFile file) {
        myFile = file;
        Project project = file.getProject();
        ObservableValue<@Nullable GHPRConnectedProjectViewModel> connectedProjectVm =
            GHPRProjectViewModel.getInstance(project).getConnectedProjectVm();
        connect(connectedProjectVm.get());
        Runnable unsubscribe = connectedProjectVm.addListener(projectVm -> project.getUIAccess().give(() -> {
            if (!Disposer.isDisposed(myCs)) {
                connect(projectVm);
            }
        }));
        Disposer.register(myCs, unsubscribe::run);
    }

    @RequiredUIAccess
    private void connect(@Nullable GHPRConnectedProjectViewModel projectVm) {
        if (myTimelineVm != null || projectVm == null || !projectVm.getRepository().equals(myFile.getRepository())) {
            return;
        }
        GHPRIdentifier prId = myFile.getPullRequest();
        GHPRInfoViewModel infoVm = projectVm.acquireInfoViewModel(prId, myCs);
        GHPRTimelineViewModel timelineVm = projectVm.acquireTimelineViewModel(prId, myCs);
        myTimelineVm = timelineVm;
        ObservableValue<ComputedResult<CodeReviewDetailsTabViewModel>> tabVm = infoVm.getDetailsVm().map(result ->
            result.<CodeReviewDetailsTabViewModel>map(detailsVm ->
                new GHPRTimelineTabViewModel(myFile.getProject(), myFile.getRepository(), prId, detailsVm, timelineVm)));
        myTabVm.set(tabVm.get());
        updatePresentation(tabVm.get());
        Runnable unsubscribe = tabVm.addListener(result -> {
            myTabVm.set(result);
            myFile.getProject().getUIAccess().give(() -> updatePresentation(result));
        });
        Disposer.register(myCs, unsubscribe::run);
    }

    @RequiredUIAccess
    private void updatePresentation(ComputedResult<CodeReviewDetailsTabViewModel> result) {
        Project project = myFile.getProject();
        if (result.getOrNull() != null && !project.isDisposed() && !Disposer.isDisposed(myCs)) {
            FileEditorManager.getInstance(project).updateFilePresentation(myFile);
        }
    }

    @Override
    public String getName() {
        return GithubLocalize.pullRequestEditorTimeline().get();
    }

    @RequiredUIAccess
    @Override
    public Component getUIComponent() {
        Component content = myContent;
        if (content == null) {
            content = myFile.getProject().getApplication().getInstance(CodeReviewTimelineComponentFactory.class)
                .create(myCs, myTabVm, () -> Objects.requireNonNull(myTimelineVm));
            myContent = content;
        }
        return content;
    }

    @Override
    public @Nullable Component getPreferredFocusedUIComponent() {
        return null;
    }

    @Override
    public void selectNotify() {
        GHPRTimelineViewModel timelineVm = myTimelineVm;
        if (timelineVm != null) {
            timelineVm.update();
        }
    }

    @Override
    public GHPRTimelineVirtualFile getFile() {
        return myFile;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {
    }

    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {
    }

    @Override
    public void dispose() {
        Disposer.dispose(myCs);
        myContent = null;
    }
}
