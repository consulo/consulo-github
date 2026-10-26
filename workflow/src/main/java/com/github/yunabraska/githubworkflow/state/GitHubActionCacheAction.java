/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.state;

import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationService;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;

import static com.github.yunabraska.githubworkflow.state.GitHubActionCache.getActionCache;

public abstract class GitHubActionCacheAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    protected GitHubActionCacheAction(LocalizeValue text, LocalizeValue description) {
        super(text, description);
    }

    @Override
    public void update(AnActionEvent event) {
        event.getPresentation().setEnabled(enabled(getActionCache().summary()));
    }

    protected boolean enabled(GitHubActionCache.CacheSummary summary) {
        return true;
    }

    protected final void notify(AnActionEvent event, LocalizeValue content) {
        NotificationService.getInstance()
            .newInfo(GitHubWorkflowNotificationGroupContributor.GROUP)
            .content(content)
            .notify(event.getData(Project.KEY));
    }
}
