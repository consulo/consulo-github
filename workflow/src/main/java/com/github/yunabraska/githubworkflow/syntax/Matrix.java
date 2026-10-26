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
import org.jetbrains.yaml.psi.YAMLSequenceItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_NODE;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_MATRIX;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_STRATEGY;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentJob;

public class Matrix {
    public static void highlightMatrix(AnnotationHolder holder, LeafPsiElement element, SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 2, -1, field -> isDefinedItem0(element, holder, field, listMatrix(element).stream().map(SimpleElement::key).toList()));
    }

    public static List<SimpleElement> listMatrix(PsiElement psiElement) {
        return completionItemsOf(listMatrixRaw(psiElement), ICON_NODE);
    }

    private static Map<String, String> listMatrixRaw(PsiElement psiElement) {
        Map<String, String> result = new LinkedHashMap<>();
        getParentJob(psiElement)
            .flatMap(job -> getChild(job, FIELD_STRATEGY))
            .flatMap(strategy -> getChild(strategy, FIELD_MATRIX))
            .ifPresent(matrix -> {
                WorkflowPsi.getChildren(matrix).stream()
                    .filter(Matrix::isMatrixProperty)
                    .forEach(property -> result.putIfAbsent(property.getKeyText(), WorkflowPsi.getText(property).orElse("")));
                getChild(matrix, "include")
                    .map(include -> WorkflowPsi.getChildren(include, YAMLSequenceItem.class))
                    .stream()
                    .flatMap(List::stream)
                    .flatMap(item -> WorkflowPsi.getChildren(item).stream())
                    .forEach(property -> result.putIfAbsent(property.getKeyText(), WorkflowPsi.getText(property).orElse("")));
            });
        return result;
    }

    private static boolean isMatrixProperty(YAMLKeyValue keyValue) {
        String key = keyValue.getKeyText();
        return !"include".equals(key) && !"exclude".equals(key);
    }

    private Matrix() {
    }
}
