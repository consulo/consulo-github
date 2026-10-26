/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_INPUT;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getAllElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;

public class Inputs {
    public static void highLightInputs(AnnotationHolder holder, LeafPsiElement element, SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 2, 2, inputId -> isDefinedItem0(element, holder, inputId, listInputs(element).stream().map(SimpleElement::key).toList()));
    }

    public static List<SimpleElement> listInputs(PsiElement psiElement) {
        Map<String, String> result = new HashMap<>();
        listInputsRaw(psiElement).forEach(input -> {
            String description = getText(psiElement, "description").orElse("");
            String previousDescription = result.computeIfAbsent(input.getKeyText(), value -> description);
            if (previousDescription.length() < description.length()) {
                result.put(input.getKeyText(), description);
            }
        });
        return completionItemsOf(result, ICON_INPUT);
    }

    public static List<YAMLKeyValue> listInputsRaw(PsiElement psiElement) {
        return getAllElements(psiElement.getContainingFile(), FIELD_INPUTS).stream()
            .map(WorkflowPsi::getChildren)
            .flatMap(Collection::stream)
            .toList();
    }

    private Inputs() {
    }
}
