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
package org.jetbrains.plugins.github.pullrequest;

import consulo.diff.DiffEditorViewer;
import consulo.github.localize.GithubLocalize;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.versionControlSystem.editor.ComplexPathVirtualFileSystem;
import consulo.versionControlSystem.review.diff.CodeReviewDiffVirtualFile;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffService;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class GHNewPRDiffVirtualFile extends CodeReviewDiffVirtualFile implements GHPRVirtualFile {
    private static final Logger LOG = Logger.getInstance(GHNewPRDiffVirtualFile.class);

    private final String myFileManagerId;
    private final Project myProject;
    private final GHRepositoryCoordinates myRepository;

    public GHNewPRDiffVirtualFile(String fileManagerId, Project project, GHRepositoryCoordinates repository) {
        super(getFileName());
        myFileManagerId = fileManagerId;
        myProject = project;
        myRepository = repository;
    }

    @Override
    public String getFileManagerId() {
        return myFileManagerId;
    }

    @Override
    public ComplexPathVirtualFileSystem<?> getFileSystem() {
        return GHPRVirtualFileSystem.getInstance();
    }

    @Override
    public String getPath() {
        return ((GHPRVirtualFileSystem) getFileSystem()).getPath(myFileManagerId, myProject, myRepository, null, true);
    }

    @Override
    public String getPresentablePath() {
        return getPresentablePath(myRepository);
    }

    @Override
    public String getPresentableName() {
        return GithubLocalize.pullRequestNewDiffEditorTitle().get();
    }

    @Override
    public boolean isValid() {
        return findProjectVm() != null;
    }

    @RequiredUIAccess
    @Override
    public DiffEditorViewer createViewer(Project project) {
        return GHPRDiffService.getInstance(project).createGHNewPRDiffProcessor(myRepository);
    }

    private @Nullable GHPRConnectedProjectViewModel findProjectVm() {
        try {
            if (myProject.isDisposed()) {
                return null;
            }
            GHPRConnectedProjectViewModel vm = GHPRProjectViewModel.getInstance(myProject).getConnectedProjectVm().get();
            return vm != null && vm.getRepository().equals(myRepository) ? vm : null;
        }
        catch (Exception e) {
            LOG.error(e);
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GHNewPRDiffVirtualFile that)) {
            return false;
        }
        return myFileManagerId.equals(that.myFileManagerId)
            && myProject.equals(that.myProject)
            && myRepository.equals(that.myRepository);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myFileManagerId, myProject, myRepository);
    }

    private static String getPresentablePath(GHRepositoryCoordinates repository) {
        return repository.toUrl() + "/pulls/newPR.diff";
    }

    private static String getFileName() {
        return "newPR.diff";
    }
}
