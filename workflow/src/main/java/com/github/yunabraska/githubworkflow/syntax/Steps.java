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
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_STEP;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_TEXT_VARIABLE;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemOf;
import static com.github.yunabraska.githubworkflow.syntax.Action.highlightActionOutputs;
import static com.github.yunabraska.githubworkflow.syntax.Action.listActionsOutputs;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.VALID_OUTPUT_FIELDS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.VALID_STEP_FIELDS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isField2Valid;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isValidItem3;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_CONCLUSION;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ID;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTCOME;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RUNS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_USES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChildSteps;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentJob;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStep;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;
import static java.util.Optional.ofNullable;

public class Steps {
    public static void highlightSteps(AnnotationHolder holder, LeafPsiElement psiElement, SimpleElement[] parts) {
        if (parts.length > 2 && List.of(FIELD_CONCLUSION, FIELD_OUTCOME).contains(parts[2].text())) {
            ifEnoughStepItems(holder, psiElement, parts, 3, VALID_STEP_FIELDS);
        }
        else {
            ifEnoughStepItems(holder, psiElement, parts, 4, VALID_OUTPUT_FIELDS);
        }
    }

    private static void ifEnoughStepItems(AnnotationHolder holder, PsiElement psiElement, SimpleElement[] parts, int numberOfItems, List<String> validFields) {
        ifEnoughItems(holder, psiElement, parts, numberOfItems, numberOfItems, stepId -> {
            List<YAMLSequenceItem> steps = listSteps(psiElement);
            if (isDefinedItem0(psiElement, holder, stepId, steps.stream().map(step -> getText(step, FIELD_ID).orElse(null)).filter(Objects::nonNull).toList()) && isField2Valid(psiElement, holder, parts[2], validFields)) {
                YAMLSequenceItem stepItem = steps.stream().filter(step -> getText(step, FIELD_ID).filter(id -> id.equals(stepId.text())).isPresent()).findFirst().orElse(null);
                List<String> runOutputs = listRunOutputs(stepItem).stream().map(SimpleElement::key).toList();
                List<String> actionOutputs = listActionsOutputs(stepItem).stream().map(SimpleElement::key).toList();
                if (parts.length > 3) {
                    highlightActionOutputs(holder, psiElement, stepItem, parts[3]);
                    isValidItem3(psiElement, holder, parts[3], Stream.concat(runOutputs.stream(), actionOutputs.stream()).toList());
                }
            }
        });
    }

    public static List<SimpleElement> codeCompletionSteps(PsiElement psiElement) {
        return listSteps(psiElement).stream().map(item -> {
            List<YAMLKeyValue> children = WorkflowPsi.getChildren(item);
            return children.stream().filter(child -> FIELD_ID.equals(child.getKeyText())).findFirst().flatMap(WorkflowPsi::getText).map(stepId -> completionItemOf(
                stepId,
                children.stream().filter(child -> FIELD_USES.equals(child.getKeyText())).findFirst().flatMap(WorkflowPsi::getText).orElseGet(() -> children.stream().filter(child -> "name".equals(child.getKeyText())).findFirst().flatMap(WorkflowPsi::getText).orElse(null)),
                ICON_STEP
            )).orElse(null);
        }).filter(Objects::nonNull).toList();
    }

    public static List<SimpleElement> codeCompletionSteps(String stepId, PsiElement position) {
        return listStepOutputs(listSteps(position).stream().filter(step -> getText(step, FIELD_ID).filter(id -> id.equals(stepId)).isPresent()).findFirst().orElse(null));
    }

    public static List<YAMLSequenceItem> listSteps(PsiElement psiElement) {
        return getParentJob(psiElement).map(job -> {
            YAMLSequenceItem currentStep = getParentStep(psiElement).orElse(null);
            boolean isOutput = getParent(psiElement, FIELD_OUTPUTS).isPresent();
            return getChildSteps(job).stream().takeWhile(step -> isOutput || step != currentStep).toList();
        }).orElseGet(() -> getParent(psiElement, FIELD_RUNS)
            .flatMap(runs -> getChild(runs, FIELD_STEPS))
            .map(steps -> {
                YAMLSequenceItem currentStep = getParentStep(psiElement).orElse(null);
                boolean isOutput = getParent(psiElement, FIELD_OUTPUTS).isPresent();
                return getChildSteps(steps).stream().takeWhile(step -> isOutput || step != currentStep).toList();
            })
            .orElseGet(() -> getParent(psiElement, FIELD_OUTPUTS)
                .map(outputs -> psiElement.getContainingFile())
                .flatMap(psiFile -> getChild(psiFile, FIELD_RUNS))
                .flatMap(runs -> getChild(runs, FIELD_STEPS))
                .map(WorkflowPsi::getChildSteps)
                .orElseGet(Collections::emptyList))
        );
    }

    public static List<SimpleElement> listStepOutputs(@Nullable YAMLSequenceItem step) {
        return Stream.concat(listRunOutputs(step).stream(), listActionsOutputs(step).stream()).toList();
    }

    private static List<SimpleElement> listRunOutputs(@Nullable YAMLSequenceItem step) {
        return ofNullable(step).flatMap(s -> getChild(s, FIELD_RUN)
            .map(WorkflowPsi::parseOutputVariables)
            .map(outputs -> outputs.stream().map(output -> completionItemOf(output.key(), output.text(), ICON_TEXT_VARIABLE)).filter(Objects::nonNull).toList())
        ).orElseGet(Collections::emptyList);
    }

    private Steps() {
    }
}
