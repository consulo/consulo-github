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
package org.jetbrains.plugins.github.pullrequest.data;

import consulo.disposer.Disposable;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public interface GHPRDataProviderRepository {
    @RequiredUIAccess
    GHPRDataProvider getDataProvider(GHPRIdentifier id, Disposable hostCs);

    @RequiredUIAccess
    @Nullable
    GHPRDataProvider findDataProvider(GHPRIdentifier id);

    @RequiredUIAccess
    void addDetailsLoadedListener(Disposable hostCs, Consumer<GHPullRequest> listener);
}
