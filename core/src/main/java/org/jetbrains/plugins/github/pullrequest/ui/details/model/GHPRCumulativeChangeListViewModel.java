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

import consulo.versionControlSystem.review.details.model.CodeReviewViewedStateChangeListViewModel;

/**
 * A change list of the cumulative pull request diff.
 * <p>
 * The viewed state describes a file as a whole and is only reported for the cumulative diff, so a commit-scoped change
 * list does not expose it at all - only this view model implements {@link CodeReviewViewedStateChangeListViewModel}.
 */
public interface GHPRCumulativeChangeListViewModel extends GHPRChangeListViewModel, CodeReviewViewedStateChangeListViewModel {
}
