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
package org.jetbrains.plugins.github.pullrequest.data;

import consulo.application.Application;
import consulo.diff.DiffEditorTabFilesManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.fileEditor.FileEditorManager;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.lazy.LazyValue;
import consulo.versionControlSystem.review.util.CodeReviewFilesUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.pullrequest.GHPRVirtualFile;
import org.jetbrains.plugins.github.pullrequest.GHPRVirtualFileSystem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class GHPRFilesManagerImpl implements GHPRFilesManager {
    private final Project myProject;
    private final GHRepositoryCoordinates myRepository;
    private final Disposable myCs;

    // current time should be enough to distinguish the manager between launches
    private final String myId = String.valueOf(System.currentTimeMillis());

    private final Supplier<GHPRVirtualFileSystem> myFs = LazyValue.notNull(GHPRVirtualFileSystem::getInstance);

    public GHPRFilesManagerImpl(Project project, Disposable parentCs, GHRepositoryCoordinates repository) {
        myProject = project;
        myRepository = repository;
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
    }

    @Override
    public String getId() {
        return myId;
    }

    @RequiredUIAccess
    @Override
    public void createAndOpenTimelineFile(GHPRIdentifier prId, boolean requestFocus) {
        GHPRVirtualFileSystem fs = myFs.get();
        String path = fs.getPath(myId, myProject, myRepository, prId, false);
        VirtualFile file = fs.refreshAndFindFileByPath(path);
        if (file != null) {
            FileEditorManager.getInstance(myProject).openFile(file, requestFocus);
        }
    }

    @RequiredUIAccess
    @Override
    public void createAndOpenDiffFile(@Nullable GHPRIdentifier prId, boolean requestFocus) {
        GHPRVirtualFileSystem fs = myFs.get();
        String path = fs.getPath(myId, myProject, myRepository, prId, true);
        VirtualFile file = fs.refreshAndFindFileByPath(path);
        if (file != null) {
            DiffEditorTabFilesManager.getInstance(myProject).showDiffFile(file, requestFocus);
        }
    }

    @RequiredUIAccess
    @Override
    public void updateTimelineFilePresentation(GHPRIdentifier prId) {
        GHPRVirtualFileSystem fs = myFs.get();
        String path = fs.getPath(myId, myProject, myRepository, prId, false);
        VirtualFile file = fs.refreshAndFindFileByPath(path);
        if (file != null) {
            FileEditorManager.getInstance(myProject).updateFilePresentation(file);
        }
    }

    @RequiredUIAccess
    @Override
    public void closeNewPrFile() {
        GHPRVirtualFileSystem fs = myFs.get();
        String path = fs.getPath(myId, myProject, myRepository, null, true);
        VirtualFile file = fs.refreshAndFindFileByPath(path);
        if (file == null) {
            return;
        }
        CodeReviewFilesUtil.closeFilesSafely(FileEditorManager.getInstance(myProject), List.of(file));
    }

    @RequiredUIAccess
    @Override
    public void closeAllFiles() {
        if (!canCloseFiles()) {
            return;
        }
        FileEditorManager fileManager = FileEditorManager.getInstance(myProject);
        List<VirtualFile> files = new ArrayList<>();
        for (VirtualFile file : fileManager.getOpenFiles()) {
            if (file instanceof GHPRVirtualFile ghprFile && myId.equals(ghprFile.getFileManagerId())) {
                files.add(file);
            }
        }
        CodeReviewFilesUtil.closeFilesSafely(fileManager, files);
    }

    private boolean canCloseFiles() {
        return !myProject.isDisposed() && !Application.get().isDisposeInProgress();
    }
}
