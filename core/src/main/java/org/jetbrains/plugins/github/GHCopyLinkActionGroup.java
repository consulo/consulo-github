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
package org.jetbrains.plugins.github;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.github.icon.GitHubIconGroup;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ex.CopyPasteManager;
import consulo.util.lang.Range;
import git4idea.remote.hosting.HostedGitRepositoriesManager;
import git4idea.remote.hosting.action.GlobalHostedGitRepositoryReferenceActionGroup;
import git4idea.remote.hosting.action.HostedGitRepositoryReference;
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager;
import org.jspecify.annotations.Nullable;

import java.net.URI;

@ActionImpl(
    id = "GitHub.Copy.Link",
    parents = @ActionParentRef(@ActionRef(id = "Git.Hosting.Copy.Link.Group"))
)
public class GHCopyLinkActionGroup extends GlobalHostedGitRepositoryReferenceActionGroup {
    public GHCopyLinkActionGroup() {
        super(GithubLocalize.groupGithubCopyLinkText(), LocalizeValue.empty(), GitHubIconGroup.github());
    }

    @Override
    protected HostedGitRepositoriesManager<?> repositoriesManager(Project project) {
        return GHHostedRepositoriesManager.getInstance(project);
    }

    @Override
    protected URI getUri(URI repository, String revisionHash) {
        return GHPathUtil.getWebURI(repository, revisionHash);
    }

    @Override
    protected URI getUri(URI repository, String revisionHash, String relativePath, @Nullable Range<Integer> lineRange) {
        return GHPathUtil.getWebURI(repository, revisionHash, relativePath, lineRange);
    }

    @Override
    protected void handleReference(HostedGitRepositoryReference reference) {
        URI uri = reference.buildWebURI();
        if (uri == null) {
            return;
        }
        CopyPasteManager.getInstance().setText(uri.toString());
    }
}
