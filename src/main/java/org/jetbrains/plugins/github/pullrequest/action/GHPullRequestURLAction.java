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
package org.jetbrains.plugins.github.pullrequest.action;

import consulo.dataContext.DataContext;
import consulo.localize.LocalizeValue;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

public abstract class GHPullRequestURLAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    protected GHPullRequestURLAction(LocalizeValue text, LocalizeValue description, @Nullable Image icon) {
        super(text, description, icon);
    }

    @Override
    public final void update(AnActionEvent e) {
        String url = findPullRequestURL(e.getDataContext());
        e.getPresentation().setEnabledAndVisible(url != null);
    }

    @RequiredUIAccess
    @Override
    public final void actionPerformed(AnActionEvent e) {
        String url = findPullRequestURL(e.getDataContext());
        if (url == null) {
            return;
        }
        handleURL(url);
    }

    @RequiredUIAccess
    public abstract void handleURL(String pullRequestUrl);

    private @Nullable String findPullRequestURL(DataContext ctx) {
        return ctx.getData(GHPRActionKeys.PULL_REQUEST_URL);
    }
}
