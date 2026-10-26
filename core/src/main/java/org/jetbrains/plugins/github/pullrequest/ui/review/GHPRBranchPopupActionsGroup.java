// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.review;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;
import org.jetbrains.plugins.github.pullrequest.action.GHPRReviewSubmitAction;

@ActionImpl(
    id = "Github.Pull.Request.Branch.Popup.Actions",
    children = {
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = GHPROnCurrentBranchShowAction.class),
        @ActionRef(type = GHPRReviewSubmitAction.class),
        @ActionRef(type = GHPROnCurrentBranchUpdateAction.class),
        @ActionRef(type = GHPROnCurrentBranchToggleReviewAction.class)
    },
    parents = @ActionParentRef(@ActionRef(id = "Git.Experimental.Branch.Popup.Actions"))
)
public class GHPRBranchPopupActionsGroup extends DefaultActionGroup implements DumbAware {
    public GHPRBranchPopupActionsGroup() {
        super(LocalizeValue.empty(), false);
    }
}
