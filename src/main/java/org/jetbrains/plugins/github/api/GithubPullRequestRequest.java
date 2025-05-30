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

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
class GithubPullRequestRequest {
    @Nonnull
    private final String title;
    @Nonnull
    private final String body;
    @Nonnull
    private final String head; // branch with changes
    @Nonnull
    private final String base; // branch requested to

    public GithubPullRequestRequest(@Nonnull String title, @Nonnull String description, @Nonnull String head, @Nonnull String base) {
        this.title = title;
        this.body = description;
        this.head = head;
        this.base = base;
    }
}
