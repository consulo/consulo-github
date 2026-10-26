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
package org.jetbrains.plugins.github;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.plugins.github.api.data.request.GithubGistRequest;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.NoSuchElementException;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface GithubGistContentsCollector {
    record GistEventData(
        Project project,
        @Nullable Editor editor,
        @Nullable VirtualFile file,
        VirtualFile @Nullable [] files
    ) {
    }

    /**
     * Collects the contents of a GitHub Gist based on the provided event data.
     *
     * @param gistEventData Data related to the Gist event, including project reference, editor instance, and target files.
     * @return A list of file contents for the Gist request.
     * Null indicates that this collector isn't responsible for this type of data, and the content should be passed to the next collector.
     * Empty list means that the collector is aware of this type of data but wasn't able to collect any content.
     */
    @Nullable
    List<GithubGistRequest.FileContent> collectContents(GistEventData gistEventData);

    /**
     * Retrieves the displayed file name for a gist from the given {@code editor} and {@code files} array.
     *
     * @param editor The editor object which might be null.
     * @param files  An array of VirtualFile objects which might be null.
     * @return The file name as a String if found, otherwise returns null.
     */
    @Nullable
    String getGistFileName(@Nullable Editor editor, VirtualFile @Nullable [] files);

    static List<GithubGistRequest.FileContent> collectContents(
        Project project,
        @Nullable Editor editor,
        @Nullable VirtualFile file,
        VirtualFile @Nullable [] files
    ) {
        GistEventData eventData = new GistEventData(project, editor, file, files);
        List<GithubGistRequest.FileContent> contents = Application.get()
            .getExtensionPoint(GithubGistContentsCollector.class)
            .computeSafeIfAny(it -> it.collectContents(eventData));
        if (contents == null) {
            throw new NoSuchElementException("No element of the collection was transformed to a non-null value.");
        }
        return contents;
    }

    static @Nullable String findGistFileName(@Nullable Editor editor, VirtualFile @Nullable [] files) {
        return Application.get()
            .getExtensionPoint(GithubGistContentsCollector.class)
            .computeSafeIfAny(it -> it.getGistFileName(editor, files));
    }
}
