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
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.application.dumb.DumbAware;
import consulo.github.icon.GitHubIconGroup;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.AnSeparator;
import consulo.ui.ex.action.DefaultActionGroup;
import org.jetbrains.plugins.github.GHOpenInBrowserActionGroup;
import org.jetbrains.plugins.github.GithubCreateGistAction;
import org.jetbrains.plugins.github.GithubSyncForkGroup;
import org.jetbrains.plugins.github.GithubViewPullRequestsAction;
import org.jetbrains.plugins.github.pullrequest.action.GHPRCreatePullRequestAction;
import org.jetbrains.plugins.github.ui.GHOpenSettingsAction;
import org.jetbrains.plugins.github.ui.cloneDialog.GHCloneRepositoryAction;

@ActionImpl(
    id = "GitHub.MainMenu",
    children = {
        @ActionRef(type = GHPRCreatePullRequestAction.class),
        @ActionRef(type = GithubViewPullRequestsAction.class),
        @ActionRef(type = GithubSyncForkGroup.class),
        @ActionRef(type = GithubCreateGistAction.class),
        @ActionRef(type = GHOpenInBrowserActionGroup.class),
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = GHCloneRepositoryAction.class),
        @ActionRef(type = AnSeparator.class),
        @ActionRef(type = GHOpenSettingsAction.class)
    },
    parents = {
        @ActionParentRef(
            value = @ActionRef(id = "Git.MainMenu"),
            anchor = ActionRefAnchor.BEFORE,
            relatedToAction = @ActionRef(id = "Vcs.QuickListPopupAction")
        ),
        @ActionParentRef(@ActionRef(id = "GitRepositoryActions"))
    }
)
public class MainMenuGroup extends DefaultActionGroup implements DumbAware, AnActionWithSyncUpdate {
    public MainMenuGroup() {
        super(GithubLocalize.groupGithubMainmenuText(), LocalizeValue.empty(), GitHubIconGroup.github());
        setPopup(true);
    }

    @Override
    public void update(AnActionEvent e) {
        if (ActionPlaces.ACTION_SEARCH.equals(e.getPlace())) {
            e.getPresentation().setText(GithubLocalize.groupGithubMainmenuGotoactionText());
        }
    }
}
