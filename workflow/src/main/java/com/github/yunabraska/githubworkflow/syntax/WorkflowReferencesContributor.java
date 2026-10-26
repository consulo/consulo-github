/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceContributor;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.psi.PsiReferenceRegistrar;
import consulo.language.util.ProcessingContext;
import org.jetbrains.yaml.YAMLLanguage;

import static com.github.yunabraska.githubworkflow.syntax.Action.referenceGithubAction;
import static com.github.yunabraska.githubworkflow.syntax.Needs.referenceNeeds;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.removeQuotes;

@ExtensionImpl
public class WorkflowReferencesContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(PsiElement.class),
            new PsiReferenceProvider() {
                @Override
                public PsiReference[] getReferencesByElement(PsiElement psiElement, ProcessingContext context) {
                    if (!WorkflowYaml.isWorkflowElement(psiElement)) {
                        return PsiReference.EMPTY_ARRAY;
                    }
                    return WorkflowReferences.textElement(psiElement)
                        .flatMap(element -> {
                            String text = removeQuotes(element.getText().replace("IntellijIdeaRulezzz ", "").replace("IntellijIdeaRulezzz", ""));
                            return referenceGithubAction(element)
                                .or(() -> referenceNeeds(element, text))
                                .or(() -> WorkflowReferences.referenceVariables(element));
                        })
                        .orElse(PsiReference.EMPTY_ARRAY);
                }
            }
        );
    }

    @Override
    public Language getLanguage() {
        return YAMLLanguage.INSTANCE;
    }
}
