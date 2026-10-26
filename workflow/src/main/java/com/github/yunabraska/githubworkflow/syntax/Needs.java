/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.model.LocalReferenceResolver;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import consulo.codeEditor.DefaultLanguageHighlighterColors;
import consulo.document.util.TextRange;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.github.yunabraska.githubworkflow.model.NodeIcon.SUPPRESS_ON;
import static com.github.yunabraska.githubworkflow.syntax.Jobs.listAllJobs;
import static com.github.yunabraska.githubworkflow.syntax.Jobs.listJobOutputs;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.addAnnotation;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.deleteElementAction;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isField2Valid;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isValidItem3;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RESULT;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentJob;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getTextElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.goToDeclarationString;
import static java.util.Optional.ofNullable;

public class Needs {
    public static void highlightNeeds(AnnotationHolder holder, LeafPsiElement element, SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 3, 4, jobId -> {
            List<String> jobIds = listJobNeeds(element);
            if (isDefinedItem0(element, holder, jobId, jobIds) && isField2Valid(element, holder, parts[2], List.of("outputs", FIELD_RESULT))) {
                if (FIELD_RESULT.equals(parts[2].text())) {
                    return;
                }
                List<String> outputs = listJobOutputs(listAllJobs(element).stream().filter(job -> job.getKeyText().equals(jobId.text())).findFirst().orElse(null)).stream().map(SimpleElement::key).toList();
                if (parts.length > 3) {
                    isValidItem3(element, holder, parts[3], outputs);
                }
            }
        });
    }

    public static void highlightNeeds(AnnotationHolder holder, @Nullable PsiElement psiElement) {
        ofNullable(psiElement)
            .filter(WorkflowPsi::isTextElement)
            .filter(element -> getParent(element, FIELD_NEEDS).isPresent())
            .ifPresent(element -> {
                List<String> jobsNames = listJobs(element).stream().map(YAMLKeyValue::getKeyText).toList();
                if (!jobsNames.contains(element.getText())) {
                    addAnnotation(holder, element, new SyntaxAnnotation(
                        GitHubWorkflowLocalize.inspectionNeedsInvalidJob(element.getText()),
                        SUPPRESS_ON,
                        deleteElementAction(element.getTextRange())
                    ));
                }
                else {
                    String tooltip = goToDeclarationString();
                    holder.newAnnotation(HighlightSeverity.INFORMATION, tooltip)
                        .range(element)
                        .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                        .tooltip(tooltip)
                        .create();
                }
            });
    }

    public static List<SimpleElement> codeCompletionPreviousJobs(PsiElement psiElement) {
        return listJobs(psiElement).stream().map(Jobs::jobToCompletionItem).filter(Objects::nonNull).toList();
    }

    public static List<SimpleElement> codeCompletionNeeds(PsiElement psiElement) {
        List<YAMLKeyValue> jobs = listJobs(psiElement);
        return listJobNeeds(psiElement).stream()
            .map(need -> need.replace("IntellijIdeaRulezzz ", ""))
            .map(need -> need.replace("IntellijIdeaRulezzz", ""))
            .map(need -> jobs.stream().filter(job -> job.getKeyText().equals(need)).findFirst().orElse(null))
            .filter(Objects::nonNull)
            .map(Jobs::jobToCompletionItem)
            .filter(Objects::nonNull)
            .toList();
    }

    public static List<SimpleElement> codeCompletionNeeds(String jobId, PsiElement position) {
        return listJobOutputs(listAllJobs(position).stream().filter(job -> job.getKeyText().equals(jobId)).findFirst().orElse(null));
    }

    public static Optional<PsiReference[]> referenceNeeds(PsiElement psiElement, String need) {
        return getParent(psiElement, FIELD_NEEDS)
            .flatMap(needs -> listJobs(needs).stream().filter(keyValue -> keyValue.getKeyText().equals(need)).findFirst())
            .map(job -> new PsiReference[]{new LocalReferenceResolver(psiElement, job)});
    }

    private static List<YAMLKeyValue> listJobs(@Nullable PsiElement psiElement) {
        return currentJob(psiElement).map(job -> listAllJobs(psiElement).stream().takeWhile(j -> !j.getKeyText().equals(job.getKeyText())).toList()).orElseGet(Collections::emptyList);
    }

    private static Optional<YAMLKeyValue> currentJob(@Nullable PsiElement psiElement) {
        int offset = ofNullable(psiElement).map(PsiElement::getTextRange).map(TextRange::getStartOffset).orElse(-1);
        return getParentJob(psiElement).or(() -> ofNullable(psiElement)
            .flatMap(element -> listAllJobs(element).stream()
                .filter(job -> job.getTextRange().contains(element.getTextRange().getStartOffset()) || job.getTextRange().getStartOffset() <= offset)
                .reduce((previous, current) -> previous.getTextRange().contains(offset) ? previous : current)));
    }

    public static List<String> listJobNeeds(@Nullable PsiElement psiElement) {
        return getJobNeed(psiElement)
            .map(needs -> getTextElements(needs)
                .stream().map(PsiElement::getText)
                .map(WorkflowPsi::removeQuotes)
                .filter(WorkflowPsi::hasText)
                .toList()
            ).orElseGet(Collections::emptyList);
    }

    public static Optional<YAMLKeyValue> getJobNeed(@Nullable PsiElement psiElement) {
        return ofNullable(psiElement)
            .flatMap(WorkflowPsi::getParentJob)
            .flatMap(job -> getChild(job, FIELD_NEEDS));
    }

    private Needs() {
    }
}
