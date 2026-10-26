/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.model;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementResolveResult;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.ResolveResult;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getProject;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowReferences.ACTION_KEY;
import static java.util.Optional.ofNullable;

public class LocalActionReferenceResolver extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {
    public LocalActionReferenceResolver(PsiElement element) {
        super(element);
    }

    @RequiredReadAction
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        GitHubAction cachedAction = myElement.getUserData(ACTION_KEY);
        Project project = getProject(myElement);
        if (cachedAction == null || project == null) {
            return ResolveResult.EMPTY_ARRAY;
        }
        return cachedAction.getLocalVirtualFile(project)
            .filter(VirtualFile::isValid)
            .flatMap(virtualFile -> Optional.<PsiFile>ofNullable(PsiManager.getInstance(project).findFile(virtualFile)))
            .filter(PsiElement::isValid)
            .map(target -> target.getChildren().length > 0 ? target.getChildren()[0] : target)
            .map(target -> new ResolveResult[]{new PsiElementResolveResult(target)})
            .orElse(ResolveResult.EMPTY_ARRAY);
    }

    @RequiredReadAction
    @Override
    public @Nullable PsiElement resolve() {
        ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }

    @RequiredReadAction
    @Override
    public String getCanonicalText() {
        return ofNullable(myElement.getText()).orElse("");
    }
}
