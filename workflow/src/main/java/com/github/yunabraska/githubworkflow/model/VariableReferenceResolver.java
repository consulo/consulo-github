/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.model;

import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementResolveResult;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.ResolveResult;
import org.jspecify.annotations.Nullable;

public class VariableReferenceResolver extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {
    private final @Nullable PsiElement myTargetElement;

    public VariableReferenceResolver(PsiElement element, TextRange rangeInElement, @Nullable PsiElement targetElement) {
        super(element, rangeInElement);
        myTargetElement = targetElement;
    }

    @RequiredReadAction
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        if (myTargetElement == null) {
            return ResolveResult.EMPTY_ARRAY;
        }
        return new ResolveResult[]{new PsiElementResolveResult(myTargetElement)};
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
        return myElement.getText();
    }
}
