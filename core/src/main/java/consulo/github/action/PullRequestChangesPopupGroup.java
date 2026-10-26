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
package consulo.github.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionRef;
import consulo.application.dumb.DumbAware;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.IdeActions;
import org.jetbrains.plugins.github.pullrequest.action.GHPRReloadDetailsAction;
import org.jetbrains.plugins.github.pullrequest.action.GHPRViewedStateToggleAction;
import org.jetbrains.plugins.github.pullrequest.action.GHPullRequestCopyURLAction;
import org.jetbrains.plugins.github.pullrequest.action.GHPullRequestOpenURLAction;

@ActionImpl(
    id = PullRequestChangesPopupGroup.ID,
    children = {
        @ActionRef(id = "CodeReview.ShowDiff"),
        @ActionRef(id = "CodeReview.ShowStandaloneDiff"),
        @ActionRef(id = IdeActions.ACTION_EDIT_SOURCE),
        @ActionRef(type = GHPRViewedStateToggleAction.class),
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = PullRequestCommitTreeViewOptionsGroup.class),
        @ActionRef(id = "ExpandAll"),
        @ActionRef(id = "CollapseAll"),
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = GHPRReloadDetailsAction.class),
        @ActionRef(type = GHPullRequestOpenURLAction.class),
        @ActionRef(type = GHPullRequestCopyURLAction.class)
    }
)
public class PullRequestChangesPopupGroup extends DefaultActionGroup implements DumbAware {
    public static final String ID = "Github.PullRequest.Changes.Popup";
}
