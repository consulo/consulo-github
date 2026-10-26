/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.run;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.ConfigurationTypeBase;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.platform.base.icon.PlatformIconGroup;

@ExtensionImpl
public class WorkflowRunConfigurationType extends ConfigurationTypeBase {
    public static final String ID = "GitHubWorkflow.RunConfiguration";

    private final ConfigurationFactory myFactory;

    public WorkflowRunConfigurationType() {
        super(
            ID,
            GitHubWorkflowLocalize.workflowRunConfigurationDisplay(),
            GitHubWorkflowLocalize.workflowRunConfigurationDescription(),
            PlatformIconGroup.actionsExecute()
        );
        myFactory = new WorkflowRunConfigurationFactory(this);
        addFactory(myFactory);
    }

    public static WorkflowRunConfigurationType getInstance() {
        return Application.get().getExtensionPoint(ConfigurationType.class).findExtensionOrFail(WorkflowRunConfigurationType.class);
    }

    public ConfigurationFactory factory() {
        return myFactory;
    }
}
