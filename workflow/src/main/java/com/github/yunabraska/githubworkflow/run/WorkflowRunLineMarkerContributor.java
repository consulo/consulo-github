/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.run;

import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.github.yunabraska.githubworkflow.syntax.WorkflowYaml;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.icon.ExecutionIconGroup;
import consulo.execution.lineMarker.ExecutorAction;
import consulo.execution.lineMarker.RunLineMarkerContributor;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.language.Language;
import consulo.language.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.ex.action.AnAction;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@ExtensionImpl
public class WorkflowRunLineMarkerContributor extends RunLineMarkerContributor {
    private static final String WORKFLOW_DISPATCH = "workflow_dispatch";

    @RequiredReadAction
    @Override
    public @Nullable Info getInfo(PsiElement element) {
        if (!(element instanceof LeafPsiElement) || !WORKFLOW_DISPATCH.equals(element.getText())) {
            return null;
        }
        if (!(element.getParent() instanceof YAMLKeyValue keyValue) || !WORKFLOW_DISPATCH.equals(keyValue.getKeyText())) {
            return null;
        }
        VirtualFile file = Optional.ofNullable(element.getContainingFile()).map(PsiFile::getVirtualFile).orElse(null);
        boolean workflowFile = Optional.ofNullable(file)
            .flatMap(WorkflowPsi::toPath)
            .filter(WorkflowYaml::isWorkflowPath)
            .isPresent();
        Optional<String> workflowPath = WorkflowRunConfigurationProducer.workflowPath(element.getProject(), file)
            .or(() -> WorkflowPsi.toPath(file).map(path -> path.getFileName().toString()));
        if (!workflowFile || workflowPath.isEmpty()) {
            return null;
        }
        if (WorkflowRunTracker.getInstance(element.getProject()).isRunning(workflowPath.get())) {
            return new Info(
                PlatformIconGroup.actionsSuspend(),
                item -> GitHubWorkflowLocalize.workflowRunGutterStop().get(),
                new AnAction[]{new StopWorkflowRunAction(workflowPath.get())}
            );
        }
        boolean repositoryAvailable = WorkflowRunRepositories.resolve(element.getProject(), file).isPresent();
        if (!repositoryAvailable) {
            return null;
        }
        return new Info(ExecutionIconGroup.gutterRun(), item -> GitHubWorkflowLocalize.workflowRunGutterRun().get(), ExecutorAction.getActions(0));
    }

    @Override
    public Language getLanguage() {
        return YAMLLanguage.INSTANCE;
    }
}
