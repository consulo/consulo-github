/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.model;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementResolveResult;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.ResolveResult;
import org.jspecify.annotations.Nullable;

public class LocalReferenceResolver extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {
    private final @Nullable PsiElement myTargetElement;

    public LocalReferenceResolver(PsiElement souceElement, @Nullable PsiElement targetElement) {
        super(souceElement);
        myTargetElement = targetElement;
    }

    @RequiredReadAction
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        if (myTargetElement != null) {
            return new ResolveResult[]{new PsiElementResolveResult(myTargetElement)};
        }
        return ResolveResult.EMPTY_ARRAY;
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
        return myTargetElement == null ? myElement.getText() : myTargetElement.getText();
    }
}
