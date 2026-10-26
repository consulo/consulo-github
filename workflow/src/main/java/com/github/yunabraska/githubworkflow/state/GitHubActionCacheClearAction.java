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

@ActionImpl(id = "GitHubWorkflow.ClearActionCache")
public class GitHubActionCacheClearAction extends GitHubActionCacheAction {
    public GitHubActionCacheClearAction() {
        super(GitHubWorkflowLocalize.actionGithubworkflowClearactioncacheText(), GitHubWorkflowLocalize.actionGithubworkflowClearactioncacheDescription());
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent event) {
        GitHubActionCache.CacheSummary before = getActionCache().summary();
        getActionCache().clear();
        notify(event, GitHubWorkflowLocalize.notificationCacheCleared(before.total()));
    }
}
