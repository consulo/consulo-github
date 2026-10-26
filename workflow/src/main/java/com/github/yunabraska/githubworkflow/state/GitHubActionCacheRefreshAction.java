/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.state;

import consulo.annotation.component.ActionImpl;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;

import static com.github.yunabraska.githubworkflow.state.GitHubActionCache.getActionCache;

@ActionImpl(id = "GitHubWorkflow.RefreshActionCache")
public class GitHubActionCacheRefreshAction extends GitHubActionCacheAction {
    public GitHubActionCacheRefreshAction() {
        super(GitHubWorkflowLocalize.actionGithubworkflowRefreshactioncacheText(), GitHubWorkflowLocalize.actionGithubworkflowRefreshactioncacheDescription());
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent event) {
        GitHubActionCache.CacheSummary before = getActionCache().summary();
        getActionCache().refreshResolvedRemoteActions(event.getData(Project.KEY));
        notify(event, GitHubWorkflowLocalize.notificationCacheRefreshStarted(before.remote()));
    }

    @Override
    protected boolean enabled(GitHubActionCache.CacheSummary summary) {
        return summary.remote() > 0;
    }
}
