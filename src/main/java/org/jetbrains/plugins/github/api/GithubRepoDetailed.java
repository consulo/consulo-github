/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Aleksey Pivovarov
 */
public class GithubRepoDetailed extends GithubRepo {
    @Nullable
    private final GithubRepo myParent;
    @Nullable
    private final GithubRepo mySource;

    public GithubRepoDetailed(
        @Nonnull String name,
        @Nullable String description,
        boolean isPrivate,
        boolean isFork,
        @Nonnull String htmlUrl,
        @Nonnull String cloneUrl,
        @Nullable String defaultBranch,
        @Nonnull GithubUser owner,
        @Nullable GithubRepo parent,
        @Nullable GithubRepo source
    ) {
        super(name, description, isPrivate, isFork, htmlUrl, cloneUrl, defaultBranch, owner);
        myParent = parent;
        mySource = source;
    }

    @Nullable
    public GithubRepo getParent() {
        return myParent;
    }

    @Nullable
    public GithubRepo getSource() {
        return mySource;
    }
}
