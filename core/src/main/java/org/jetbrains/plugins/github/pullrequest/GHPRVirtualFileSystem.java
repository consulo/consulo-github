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

import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.versionControlSystem.editor.ComplexPath;
import consulo.versionControlSystem.editor.ComplexPathVirtualFileSystem;
import consulo.versionControlSystem.editor.GsonComplexPathSerializer;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@ExtensionImpl
public class GHPRVirtualFileSystem extends ComplexPathVirtualFileSystem<GHPRVirtualFileSystem.GHPRFilePath> {
    private static final String PROTOCOL = "ghpr";

    public static GHPRVirtualFileSystem getInstance() {
        return (GHPRVirtualFileSystem) Objects.requireNonNull(VirtualFileManager.getInstance().getFileSystem(PROTOCOL));
    }

    public GHPRVirtualFileSystem() {
        super(new GsonComplexPathSerializer<>(GHPRFilePath.class));
    }

    @Override
    public String getProtocol() {
        return PROTOCOL;
    }

    @Override
    protected @Nullable VirtualFile findOrCreateFile(Project project, GHPRFilePath path) {
        GHPRIdentifier prId = path.prId();
        if (prId != null) {
            if (path.isDiff()) {
                return new GHPRDiffVirtualFile(path.sessionId(), project, path.repository(), prId);
            }
            return null;
        }
        if (path.isDiff()) {
            return new GHNewPRDiffVirtualFile(path.sessionId(), project, path.repository());
        }
        return null;
    }

    public String getPath(String fileManagerId, Project project, GHRepositoryCoordinates repository, @Nullable GHPRIdentifier id) {
        return getPath(fileManagerId, project, repository, id, false);
    }

    public String getPath(String fileManagerId,
                          Project project,
                          GHRepositoryCoordinates repository,
                          @Nullable GHPRIdentifier id,
                          boolean isDiff) {
        return getPath(new GHPRFilePath(fileManagerId, project.getLocationHash(), repository, id, isDiff));
    }

    public record GHPRFilePath(String sessionId,
                               String projectHash,
                               GHRepositoryCoordinates repository,
                               @Nullable GHPRIdentifier prId,
                               boolean isDiff) implements ComplexPath {
        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public String getProjectHash() {
            return projectHash;
        }
    }
}
