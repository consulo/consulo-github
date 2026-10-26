/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.run;

import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.github.yunabraska.githubworkflow.syntax.WorkflowYaml;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.RunConfigurationProducer;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;

@ExtensionImpl
public class WorkflowRunConfigurationProducer extends RunConfigurationProducer<WorkflowRunConfiguration> {
    private static final WorkflowRun.DispatchInputs DISPATCH_INPUTS = new WorkflowRun.DispatchInputs();

    public WorkflowRunConfigurationProducer() {
        super(WorkflowRunConfigurationType.getInstance().factory());
    }

    @Override
    protected boolean setupConfigurationFromContext(
        WorkflowRunConfiguration configuration,
        ConfigurationContext context,
        SimpleReference<PsiElement> sourceElement
    ) {
        PsiFile file = workflowFile(context.getPsiLocation()).orElse(null);
        if (file == null) {
            return false;
        }
        String text = file.getText();
        if (!DISPATCH_INPUTS.hasWorkflowDispatch(text)) {
            return false;
        }
        Project project = context.getProject();
        WorkflowRunRepositories.Target target = WorkflowRunRepositories.resolve(project, file.getVirtualFile()).orElse(null);
        if (target == null) {
            return false;
        }
        String path = workflowPath(project, file.getVirtualFile()).orElse(file.getName());
        configuration.setName(GitHubWorkflowLocalize.workflowRunConfigurationName(file.getName()).get());
        WorkflowRunConfigurationOptions options = configuration.getOptions();
        options.setServerUrl(target.server().toUrl());
        options.setOwner(target.owner());
        options.setRepo(target.repo());
        options.setWorkflowPath(path);
        options.setRef(Optional.ofNullable(target.branch()).orElse("main"));
        options.setInputs(DISPATCH_INPUTS.defaults(text));
        sourceElement.set(file);
        return true;
    }

    @Override
    public boolean isConfigurationFromContext(WorkflowRunConfiguration configuration, ConfigurationContext context) {
        Project project = context.getProject();
        return workflowFile(context.getPsiLocation())
            .filter(file -> workflowPath(project, file.getVirtualFile())
                .filter(path -> path.equals(configuration.getOptions().getWorkflowPath()))
                .isPresent())
            .filter(file -> WorkflowRunRepositories.resolve(project, file.getVirtualFile())
                .map(WorkflowRunRepositories.Target::branch)
                .map(branch -> branch.equals(configuration.getOptions().getRef()))
                .orElse(true))
            .isPresent();
    }

    static Optional<PsiFile> workflowFile(@Nullable PsiElement element) {
        return Optional.ofNullable(element)
            .map(PsiElement::getContainingFile)
            .filter(file -> Optional.ofNullable(file.getVirtualFile())
                .flatMap(WorkflowPsi::toPath)
                .filter(WorkflowYaml::isWorkflowPath)
                .isPresent());
    }

    static Optional<String> workflowPath(@Nullable Project project, @Nullable VirtualFile file) {
        return Optional.ofNullable(project)
            .map(Project::getBasePath)
            .map(Path::of)
            .flatMap(root -> Optional.ofNullable(file)
                .map(VirtualFile::getPath)
                .map(Path::of)
                .map(root::relativize)
                .map(Path::toString)
                .map(path -> path.replace('\\', '/')));
    }
}
