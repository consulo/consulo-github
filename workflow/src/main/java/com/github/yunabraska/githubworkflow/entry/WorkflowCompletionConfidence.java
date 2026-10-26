/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.entry;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionConfidence;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.util.lang.ThreeState;
import org.jetbrains.yaml.YAMLLanguage;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.getWorkflowFile;

/**
 * Keeps workflow auto-popup completion available in sparse YAML positions, such as the line after {@code on:}.
 */
@ExtensionImpl(id = "githubWorkflowCompletionConfidence")
public class WorkflowCompletionConfidence extends CompletionConfidence {
    @Override
    public ThreeState shouldSkipAutopopup(PsiElement contextElement, PsiFile psiFile, int offset) {
        return getWorkflowFile(psiFile).isPresent() || getWorkflowFile(contextElement).isPresent()
            ? ThreeState.NO
            : ThreeState.UNSURE;
    }

    @Override
    public Language getLanguage() {
        return YAMLLanguage.INSTANCE;
    }
}
