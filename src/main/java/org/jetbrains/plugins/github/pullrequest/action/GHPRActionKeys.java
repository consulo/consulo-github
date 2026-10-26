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
package org.jetbrains.plugins.github.pullrequest.action;

import consulo.util.dataholder.Key;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.list.GHPRListController;

public final class GHPRActionKeys {
    public static final Key<GHPRIdentifier> PULL_REQUEST_ID = Key.create("org.jetbrains.plugins.github.pullrequest.id");

    public static final Key<String> PULL_REQUEST_URL = Key.create("org.jetbrains.plugins.github.pullrequest.url");

    public static final Key<GHPRListController> PULL_REQUESTS_LIST_CONTROLLER =
        Key.create("org.jetbrains.plugins.github.pullrequest.list.controller");

    public static final Key<GHPRProjectViewModel> PULL_REQUESTS_PROJECT_VM =
        Key.create("org.jetbrains.plugins.github.pullrequest.project.vm");

    public static final Key<GHPRConnectedProjectViewModel> PULL_REQUESTS_CONNECTED_PROJECT_VM =
        Key.create("org.jetbrains.plugins.github.pullrequest.connected.project.vm");

    private GHPRActionKeys() {
    }
}
