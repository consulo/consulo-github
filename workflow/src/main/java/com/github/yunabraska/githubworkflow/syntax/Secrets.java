/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import consulo.document.util.TextRange;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_SECRET_WORKFLOW;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.RELOAD;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SUPPRESS_ON;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.model.SyntaxAnnotation.createAnnotation;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.deleteElementAction;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.replaceAction;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.simpleTextRange;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_IF;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ON;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getAllElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChildren;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;

public class Secrets {
    private static final String GITHUB_TOKEN = "GITHUB_TOKEN";

    public static void highLightSecrets(
        AnnotationHolder holder,
        PsiElement psiElement,
        LeafPsiElement element,
        SimpleElement simpleElement,
        SimpleElement[] parts,
        @Nullable YAMLKeyValue parentIf
    ) {
        ifEnoughItems(holder, element, parts, 2, 2, secretId -> {
            if (parentIf != null) {
                TextRange range = psiElement.getTextRange();
                TextRange textRange = new TextRange(range.getStartOffset() + parts[0].startIndexOffset(), range.getStartOffset() + parts[parts.length - 1].endIndexOffset());
                new SyntaxAnnotation(
                    GitHubWorkflowLocalize.inspectionSecretInvalidIf(simpleElement.text()),
                    SUPPRESS_ON,
                    deleteElementAction(textRange)
                ).createAnnotation(psiElement, textRange, holder);
            }
            List<String> secrets = listSecrets(element).stream().map(SimpleElement::key).toList();
            if (!secrets.contains(secretId.text())) {
                TextRange textRange = simpleTextRange(element, secretId);
                createAnnotation(element, textRange, holder, secrets.stream().map(secret -> new SyntaxAnnotation(
                    GitHubWorkflowLocalize.inspectionSecretReplaceRuntime(secretId.text(), secret),
                    RELOAD,
                    HighlightSeverity.WEAK_WARNING,
                    ProblemHighlightType.WEAK_WARNING,
                    replaceAction(textRange, secret),
                    true
                )).toList());
            }
        });
    }

    public static List<SimpleElement> listSecrets(PsiElement psiElement) {
        if (getParent(psiElement, FIELD_IF).isPresent()) {
            return Collections.emptyList();
        }
        Map<String, String> result = getChild(psiElement.getContainingFile(), FIELD_ON)
            .map(on -> getAllElements(on, FIELD_SECRETS))
            .map(secrets -> secrets.stream().flatMap(secret -> getChildren(secret).stream()).collect(Collectors.toMap(
                YAMLKeyValue::getKeyText,
                keyValue -> getText(keyValue, "description").orElse(""),
                (existing, replacement) -> existing,
                LinkedHashMap::new
            )))
            .orElseGet(LinkedHashMap::new);
        result.putIfAbsent(GITHUB_TOKEN, GitHubWorkflowLocalize.completionSecretGithubtoken().get());
        return completionItemsOf(result, ICON_SECRET_WORKFLOW);
    }

    private Secrets() {
    }
}
