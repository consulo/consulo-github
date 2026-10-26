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
package org.jetbrains.plugins.github.pullrequest.ui.details.model;

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHReaction;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.List;

public record GHPRDetailsFull(GHPRIdentifier id,
                              String url,
                              GHActor author,
                              Date createdAt,
                              GHPullRequestState state,
                              String titleHtml,
                              @Nullable String description,
                              @Nullable String descriptionHtml,
                              @Nullable String headRefId,
                              @Nullable String headRefName,
                              boolean canEditDescription,
                              boolean canReactDescription,
                              boolean canDeleteHeadRef,
                              List<GHReaction> reactions) {
}
