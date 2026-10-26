/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.run;

import consulo.execution.RuntimeConfigurationException;
import consulo.execution.configuration.CommandLineState;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.RunConfigurationBase;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.configuration.RuntimeConfigurationError;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Run configuration that dispatches a workflow_dispatch event and follows the resulting run.
 */
public class WorkflowRunConfiguration extends RunConfigurationBase {
    private static final int MAX_INPUTS = 25;

    WorkflowRunConfiguration(Project project, ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @Override
    protected Class<WorkflowRunConfigurationOptions> getOptionsClass() {
        return WorkflowRunConfigurationOptions.class;
    }

    @Override
    public WorkflowRunConfigurationOptions getOptions() {
        return (WorkflowRunConfigurationOptions) super.getOptions();
    }

    @Override
    public List<RunConfigurationFragment<?>> createFragments() {
        return List.of(new WorkflowRunConfigurationFragment());
    }

    @Override
    public @Nullable RunProfileState getState(Executor executor, ExecutionEnvironment environment) throws ExecutionException {
        WorkflowRun.Request request = toRequest();
        return new CommandLineState(environment) {
            @Override
            protected ProcessHandler startProcess() throws ExecutionException {
                return new WorkflowRunProcessHandler(getProject(), request, environment.getExecutor());
            }
        };
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        WorkflowRunConfigurationOptions options = getOptions();
        if (isBlank(options.getServerUrl()) || WorkflowRunRepositories.server(options.getServerUrl()).isEmpty()) {
            throw new RuntimeConfigurationError(GitHubWorkflowLocalize.workflowRunErrorServer());
        }
        if (isBlank(options.getOwner()) || isBlank(options.getRepo())) {
            throw new RuntimeConfigurationError(GitHubWorkflowLocalize.workflowRunErrorRepository());
        }
        if (isBlank(options.getWorkflowPath())) {
            throw new RuntimeConfigurationError(GitHubWorkflowLocalize.workflowRunErrorWorkflow());
        }
        if (isBlank(options.getRef())) {
            throw new RuntimeConfigurationError(GitHubWorkflowLocalize.workflowRunErrorRef());
        }
        if (options.getInputs().size() > MAX_INPUTS) {
            throw new RuntimeConfigurationError(GitHubWorkflowLocalize.workflowRunErrorInputs());
        }
    }

    WorkflowRun.Request toRequest() {
        WorkflowRunConfigurationOptions options = getOptions();
        return new WorkflowRun.Request(
            options.getServerUrl(),
            options.getOwner(),
            options.getRepo(),
            options.getWorkflowPath(),
            options.getRef(),
            options.getInputs()
        );
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
