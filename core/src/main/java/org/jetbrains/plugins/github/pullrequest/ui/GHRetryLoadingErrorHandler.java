// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.pullrequest.ui;

import consulo.github.localize.GithubLocalize;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DumbAwareAction;

public class GHRetryLoadingErrorHandler implements GHLoadingErrorHandler {
    protected final Runnable myResetRunnable;

    public GHRetryLoadingErrorHandler(Runnable resetRunnable) {
        myResetRunnable = resetRunnable;
    }

    @Override
    public AnAction getActionForError(Throwable error) {
        return createRetryAction();
    }

    protected AnAction createRetryAction() {
        return DumbAwareAction.create(GithubLocalize.retryAction(), e -> myResetRunnable.run());
    }
}
