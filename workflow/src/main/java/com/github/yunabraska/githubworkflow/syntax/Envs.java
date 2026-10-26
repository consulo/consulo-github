/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import consulo.document.util.TextRange;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV_JOB;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV_ROOT;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV_STEP;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_TEXT_VARIABLE;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.defaultEnvs;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getAllElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentJob;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStep;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;

public class Envs {
    public static void highLightEnvs(AnnotationHolder holder, LeafPsiElement element, SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 2, -1, envId -> isDefinedItem0(element, holder, envId, listEnvs(element).stream().map(SimpleElement::key).toList()));
    }

    public static List<SimpleElement> listEnvs(PsiElement psiElement) {
        List<SimpleElement> result = new ArrayList<>();
        addRunEnvs(psiElement, result);
        addStepEnvs(psiElement, result);
        addJobEnvs(psiElement, result);
        addWorkflowEnvs(psiElement, result);
        addDefaultEnvs(result);
        return result;
    }

    private static void addRunEnvs(PsiElement psiElement, List<SimpleElement> result) {
        TextRange currentRange = psiElement.getTextRange();
        result.addAll(completionItemsOf(
            getAllElements(psiElement.getContainingFile(), FIELD_RUN).stream()
                .filter(keyValue -> getParentStep(keyValue).map(PsiElement::getTextRange).map(TextRange::getStartOffset).orElse(currentRange.getEndOffset()) < currentRange.getStartOffset())
                .map(WorkflowPsi::parseEnvVariables)
                .flatMap(Collection::stream)
                .filter(element -> element.key() != null)
                .collect(Collectors.toMap(SimpleElement::key, SimpleElement::textNoQuotes, (existing, replacement) -> existing)),
            ICON_TEXT_VARIABLE
        ));
    }

    private static void addWorkflowEnvs(PsiElement psiElement, List<SimpleElement> result) {
        getChild(psiElement.getContainingFile(), FIELD_ENVS)
            .map(WorkflowPsi::getChildren)
            .map(toMapWithKeyAndText())
            .map(map -> completionItemsOf(map, ICON_ENV_ROOT))
            .ifPresent(result::addAll);
    }

    private static void addJobEnvs(PsiElement psiElement, List<SimpleElement> result) {
        getParentJob(psiElement)
            .flatMap(job -> getChild(job, FIELD_ENVS))
            .map(WorkflowPsi::getChildren)
            .map(toMapWithKeyAndText())
            .map(map -> completionItemsOf(map, ICON_ENV_JOB))
            .ifPresent(result::addAll);
    }

    private static void addStepEnvs(PsiElement psiElement, List<SimpleElement> result) {
        getParentStep(psiElement)
            .flatMap(step -> getChild(step, FIELD_ENVS))
            .map(WorkflowPsi::getChildren)
            .map(toMapWithKeyAndText())
            .map(map -> completionItemsOf(map, ICON_ENV_STEP))
            .ifPresent(result::addAll);
    }

    private static Function<List<YAMLKeyValue>, Map<String, String>> toMapWithKeyAndText() {
        return elements -> elements.stream()
            .collect(Collectors.toMap(YAMLKeyValue::getKeyText, keyValue -> getText(keyValue).orElse(""), (existing, replacement) -> existing));
    }

    private static void addDefaultEnvs(List<SimpleElement> result) {
        result.addAll(completionItemsOf(defaultEnvs(), ICON_ENV));
    }

    private Envs() {
    }
}
