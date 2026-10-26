/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.model.NodeIcon;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemOf;
import static com.github.yunabraska.githubworkflow.syntax.Action.listActionsOutputs;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isField2Valid;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isValidItem3;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ID;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ON;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RESULT;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_USES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getAllElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChildren;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;
import static java.util.Optional.ofNullable;

public class Jobs {
    public static void highLightJobs(AnnotationHolder holder, LeafPsiElement element, SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 3, 4, jobId -> {
            List<YAMLKeyValue> jobs = listJobs(element);
            if (isDefinedItem0(element, holder, jobId, jobs.stream().map(YAMLKeyValue::getKeyText).toList()) && isField2Valid(element, holder, parts[2], List.of(FIELD_OUTPUTS, FIELD_RESULT))) {
                if (FIELD_RESULT.equals(parts[2].text())) {
                    return;
                }
                List<String> outputs = listJobOutputs(jobs.stream().filter(job -> job.getKeyText().equals(jobId.text())).findFirst().orElse(null)).stream().map(SimpleElement::key).toList();
                if (parts.length > 3) {
                    isValidItem3(element, holder, parts[3], outputs);
                }
            }
        });
    }

    public static List<SimpleElement> codeCompletionJobs(PsiElement psiElement) {
        return listJobs(psiElement).stream().map(Jobs::jobToCompletionItem).filter(Objects::nonNull).toList();
    }

    public static List<SimpleElement> codeCompletionJobs(String jobId, PsiElement position) {
        return listJobOutputs(listJobs(position).stream().filter(job -> job.getKeyText().equals(jobId)).findFirst().orElse(null));
    }

    public static List<YAMLKeyValue> listJobs(PsiElement psiElement) {
        return getParent(psiElement, FIELD_OUTPUTS)
            .flatMap(outputs -> getParent(psiElement, FIELD_ON))
            .map(Jobs::listAllJobs)
            .orElseGet(Collections::emptyList);
    }

    public static List<YAMLKeyValue> listAllJobs(@Nullable PsiElement psiElement) {
        return ofNullable(psiElement).map(element -> getAllElements(element.getContainingFile(), FIELD_JOBS).stream().flatMap(jobs -> getChildren(jobs, YAMLKeyValue.class).stream()).toList()).orElseGet(Collections::emptyList);
    }

    public static List<SimpleElement> listJobOutputs(@Nullable YAMLKeyValue job) {
        List<SimpleElement> jobOutputs = ofNullable(job)
            .flatMap(j -> getChild(j, FIELD_OUTPUTS)
                .map(WorkflowPsi::getChildren)
                .map(children -> children.stream().map(child -> getText(child).map(value -> completionItemOf(child.getKeyText(), value, ICON_OUTPUT)).orElse(null)).filter(Objects::nonNull).toList())
            ).orElseGet(Collections::emptyList);

        return Stream.concat(jobOutputs.stream(), listActionsOutputs(job).stream()).toList();
    }

    public static @Nullable SimpleElement jobToCompletionItem(YAMLKeyValue item) {
        List<YAMLKeyValue> children = WorkflowPsi.getChildren(item);
        YAMLKeyValue usesOrName = children.stream().filter(child -> FIELD_USES.equals(child.getKeyText())).findFirst().orElseGet(() -> children.stream().filter(child -> "name".equals(child.getKeyText())).findFirst().orElse(null));
        return completionItemOf(
            children.stream().filter(child -> FIELD_ID.equals(child.getKeyText())).findFirst().flatMap(WorkflowPsi::getText).orElse(item.getKeyText()),
            ofNullable(usesOrName).flatMap(WorkflowPsi::getText).orElse(""),
            NodeIcon.ICON_NEEDS
        );
    }

    private Jobs() {
    }
}
