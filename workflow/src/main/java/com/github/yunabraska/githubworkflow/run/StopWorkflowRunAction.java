/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.run;

import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;

class StopWorkflowRunAction extends DumbAwareAction {
    private final String myWorkflowPath;

    StopWorkflowRunAction(String workflowPath) {
        super(
            GitHubWorkflowLocalize.workflowRunGutterStopText(),
            GitHubWorkflowLocalize.workflowRunGutterStopDescription(),
            PlatformIconGroup.actionsSuspend()
        );
        myWorkflowPath = workflowPath;
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent event) {
        Project project = event.getData(Project.KEY);
        if (project != null) {
            WorkflowRunTracker.getInstance(project).stop(myWorkflowPath);
        }
    }
}
