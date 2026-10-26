/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.run;

import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.RunConfiguration;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.project.Project;

class WorkflowRunConfigurationFactory extends ConfigurationFactory {
    WorkflowRunConfigurationFactory(ConfigurationType type) {
        super(type);
    }

    @Override
    public String getId() {
        return WorkflowRunConfigurationType.ID + ".Factory";
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
        return new WorkflowRunConfiguration(project, this, GitHubWorkflowLocalize.workflowRunConfigurationDisplay().get());
    }
}
