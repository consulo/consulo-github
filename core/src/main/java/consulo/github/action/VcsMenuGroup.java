package consulo.github.action;

import consulo.annotation.component.ActionImpl;
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.application.dumb.DumbAware;
import consulo.localize.LocalizeValue;
import consulo.ui.ex.action.DefaultActionGroup;
import org.jetbrains.plugins.github.GithubSyncForkAction;
import org.jetbrains.plugins.github.GithubViewPullRequestsAction;
import org.jetbrains.plugins.github.pullrequest.action.GHPRCreatePullRequestAction;

/**
 * @author UNV
 * @since 2025-11-09
 */
@ActionImpl(
    id = "Git.Menu.Github",
    children = {
        @ActionRef(type = GithubSyncForkAction.class),
        @ActionRef(type = GHPRCreatePullRequestAction.class),
        @ActionRef(type = GithubViewPullRequestsAction.class)
    },
    parents = @ActionParentRef(@ActionRef(id = "Git.Menu"))
)
public class VcsMenuGroup extends DefaultActionGroup implements DumbAware {
    public VcsMenuGroup() {
        super(LocalizeValue.empty(), false);
    }
}
