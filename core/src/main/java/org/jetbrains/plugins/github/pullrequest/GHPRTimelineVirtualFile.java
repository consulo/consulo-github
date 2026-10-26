// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import consulo.fileEditor.FileEditorManager;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.versionControlSystem.editor.ComplexPathVirtualFileSystem;
import consulo.versionControlSystem.review.file.ComplexPathVirtualFileWithoutContent;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class GHPRTimelineVirtualFile extends ComplexPathVirtualFileWithoutContent implements GHPRVirtualFile {
    private final String myFileManagerId;
    private final Project myProject;
    private final GHRepositoryCoordinates myRepository;
    private final GHPRIdentifier myPullRequest;

    public GHPRTimelineVirtualFile(String fileManagerId, Project project, GHRepositoryCoordinates repository, GHPRIdentifier pullRequest) {
        super(fileManagerId);
        myFileManagerId = fileManagerId;
        myProject = project;
        myRepository = repository;
        myPullRequest = pullRequest;
        putUserData(FileEditorManager.FORBID_TAB_SPLIT, true);
        setWritable(false);
    }

    @Override
    public String getFileManagerId() {
        return myFileManagerId;
    }

    public Project getProject() {
        return myProject;
    }

    public GHRepositoryCoordinates getRepository() {
        return myRepository;
    }

    public GHPRIdentifier getPullRequest() {
        return myPullRequest;
    }

    @Override
    public ComplexPathVirtualFileSystem<?> getFileSystem() {
        return GHPRVirtualFileSystem.getInstance();
    }

    @Override
    public String getName() {
        return GHPRTimelineUIUtil.getName(myPullRequest);
    }

    @Override
    public String getPresentableName() {
        return GHPRTimelineUIUtil.getPresentableName(myProject, myRepository, myPullRequest);
    }

    @Override
    public String getPath() {
        return ((GHPRVirtualFileSystem) getFileSystem()).getPath(getSessionId(), myProject, myRepository, myPullRequest, false);
    }

    @Override
    public String getPresentablePath() {
        return GHPRTimelineUIUtil.getPresentablePath(myProject, myRepository, myPullRequest);
    }

    public @Nullable Image getIcon() {
        return GHPRTimelineUIUtil.getIcon(myProject, myRepository, myPullRequest);
    }

    @Override
    public boolean isValid() {
        return !myProject.isDisposed();
    }

    @Override
    public void setValid(boolean valid) {
    }

    public @Nullable GHPRConnectedProjectViewModel findProjectVm() {
        if (myProject.isDisposed()) {
            return null;
        }
        GHPRConnectedProjectViewModel vm = GHPRProjectViewModel.getInstance(myProject).getConnectedProjectVm().get();
        return vm != null && vm.getRepository().equals(myRepository) ? vm : null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRTimelineVirtualFile that)) {
            return false;
        }
        if (!super.equals(other)) {
            return false;
        }
        return myProject.equals(that.myProject)
            && myRepository.equals(that.myRepository)
            && myPullRequest.equals(that.myPullRequest);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + myProject.hashCode();
        result = 31 * result + myRepository.hashCode();
        result = 31 * result + myPullRequest.hashCode();
        return result;
    }
}
