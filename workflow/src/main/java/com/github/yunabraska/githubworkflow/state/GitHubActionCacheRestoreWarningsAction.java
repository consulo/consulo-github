/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.state;

import consulo.annotation.component.ActionImpl;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;

import static com.github.yunabraska.githubworkflow.state.GitHubActionCache.getActionCache;

@ActionImpl(id = "GitHubWorkflow.RestoreActionWarnings")
public class GitHubActionCacheRestoreWarningsAction extends GitHubActionCacheAction {
    public GitHubActionCacheRestoreWarningsAction() {
        super(GitHubWorkflowLocalize.actionGithubworkflowRestoreactionwarningsText(), GitHubWorkflowLocalize.actionGithubworkflowRestoreactionwarningsDescription());
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent event) {
        long restored = getActionCache().restoreWarnings();
        notify(event, GitHubWorkflowLocalize.notificationWarningsRestored(restored));
    }

    @Override
    protected boolean enabled(GitHubActionCache.CacheSummary summary) {
        return summary.suppressed() > 0;
    }
}
