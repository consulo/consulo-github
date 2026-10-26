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
package org.jetbrains.plugins.github;

import consulo.dataContext.DataContext;
import consulo.github.icon.GitHubIconGroup;
import consulo.github.localize.GithubLocalize;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.versionControlSystem.action.UpToDateLineNumberListener;
import consulo.versionControlSystem.annotate.FileAnnotation;
import consulo.versionControlSystem.history.VcsRevisionNumber;
import consulo.virtualFileSystem.VirtualFile;
import git4idea.GitRevisionNumber;
import git4idea.annotate.GitFileAnnotation;
import git4idea.remote.hosting.action.HostedGitRepositoryReference;
import git4idea.remote.hosting.action.HostedGitRepositoryReferenceActionGroup;
import git4idea.remote.hosting.action.HostedGitRepositoryReferenceUtil;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;

import java.net.URI;
import java.util.List;

public class GHOpenInBrowserFromAnnotationActionGroup extends HostedGitRepositoryReferenceActionGroup implements UpToDateLineNumberListener {
    private final FileAnnotation myAnnotation;
    private int myLineNumber = -1;

    public GHOpenInBrowserFromAnnotationActionGroup(FileAnnotation annotation) {
        super(GithubLocalize.openOnGithubAction(), GithubLocalize.openOnGithubActionDescription(), GitHubIconGroup.github());
        myAnnotation = annotation;
    }

    public FileAnnotation getAnnotation() {
        return myAnnotation;
    }

    @Override
    public void accept(int lineNumber) {
        myLineNumber = lineNumber;
    }

    @Override
    protected List<HostedGitRepositoryReference> findReferences(DataContext dataContext) {
        if (!(myAnnotation instanceof GitFileAnnotation gitAnnotation)) {
            return List.of();
        }
        Project project = gitAnnotation.getProject();
        VirtualFile virtualFile = gitAnnotation.getFile();

        if (myLineNumber < 0) {
            return List.of();
        }
        VcsRevisionNumber revisionNumber = gitAnnotation.getLineRevisionNumber(myLineNumber);
        if (!(revisionNumber instanceof GitRevisionNumber revision)) {
            return List.of();
        }

        return HostedGitRepositoryReferenceUtil
            .findReferences(project, GHHostedRepositoriesManager.getInstance(project), virtualFile, revision, GHPathUtil::getWebURI);
    }

    @Override
    protected void handleReference(HostedGitRepositoryReference reference) {
        URI uri = reference.buildWebURI();
        if (uri == null) {
            return;
        }
        Platform.current().openInBrowser(uri.toString());
    }
}
