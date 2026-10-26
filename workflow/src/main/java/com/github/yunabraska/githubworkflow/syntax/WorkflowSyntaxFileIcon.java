/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.model.GitHubSchemaProvider;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.github.icon.GitHubIconGroup;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.virtualFileSystem.VirtualFile;

@ExtensionImpl
public class WorkflowSyntaxFileIcon implements IconDescriptorUpdater {
    @RequiredReadAction
    @Override
    public void updateIcon(IconDescriptor iconDescriptor, PsiElement element, int flags) {
        if (element instanceof PsiFile file) {
            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile != null && isKnownFile(virtualFile)) {
                iconDescriptor.setMainIcon(GitHubIconGroup.github());
            }
        }
    }

    private static boolean isKnownFile(VirtualFile virtualFile) {
        if (WorkflowPsi.toPath(virtualFile).filter(WorkflowYaml::isWorkflowFile).isPresent()) {
            return true;
        }
        return WorkflowSyntax.schemaFileProviders().stream()
            .filter(GitHubSchemaProvider.class::isInstance)
            .map(GitHubSchemaProvider.class::cast)
            .anyMatch(schemaProvider -> schemaProvider.isAvailable(virtualFile));
    }
}
