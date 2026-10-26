/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_JOB;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_NODE;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isField2Valid;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isValidItem3;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.DEFAULT_VALUE_MAP;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_JOB;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_SERVICES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentJob;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getTextElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.removeQuotes;

public class JobContext {
    private static final String FIELD_CONTAINER = "container";
    private static final String FIELD_ID = "id";
    private static final String FIELD_NETWORK = "network";
    private static final String FIELD_PORTS = "ports";
    private static final Pattern PORT_PATTERN = Pattern.compile("(\\d+)(?:/(?:tcp|udp))?(?::\\d+)?");
    private static final List<String> CONTAINER_FIELDS = List.of(FIELD_ID, FIELD_NETWORK);
    private static final List<String> SERVICE_FIELDS = List.of(FIELD_ID, FIELD_NETWORK, FIELD_PORTS);

    public static void highlightJob(AnnotationHolder holder, LeafPsiElement element, SimpleElement[] parts) {
        ifEnoughItems(holder, element, parts, 2, -1, field -> {
            if (!isDefinedItem0(element, holder, field, new ArrayList<>(DEFAULT_VALUE_MAP.get(FIELD_JOB).get().keySet()))) {
                return;
            }
            switch (field.text()) {
                case FIELD_CONTAINER -> highlightContainer(holder, element, parts);
                case FIELD_SERVICES -> highlightServices(holder, element, parts);
                default -> ifEnoughItems(holder, element, parts, 2, 2, ignored -> {
                });
            }
        });
    }

    public static List<SimpleElement> codeCompletionJob() {
        return completionItemsOf(DEFAULT_VALUE_MAP.get(FIELD_JOB).get(), ICON_JOB);
    }

    public static List<SimpleElement> codeCompletionJob(String parent, PsiElement position) {
        return switch (parent) {
            case FIELD_CONTAINER -> completionItemsOf(toMap(CONTAINER_FIELDS, GitHubWorkflowLocalize.completionJobContainerfield().get()), ICON_NODE);
            case FIELD_SERVICES -> completionItemsOf(toMap(listServiceIds(position), GitHubWorkflowLocalize.completionJobService().get()), ICON_NODE);
            default -> List.of();
        };
    }

    public static List<SimpleElement> codeCompletionJob(String parent, String child, PsiElement position) {
        if (FIELD_SERVICES.equals(parent) && listServiceIds(position).contains(child)) {
            return completionItemsOf(toMap(SERVICE_FIELDS, GitHubWorkflowLocalize.completionJobServicefield().get()), ICON_NODE);
        }
        return List.of();
    }

    public static List<SimpleElement> codeCompletionJob(String parent, String serviceId, String serviceField, PsiElement position) {
        if (FIELD_SERVICES.equals(parent) && FIELD_PORTS.equals(serviceField)) {
            return completionItemsOf(toMap(listServicePorts(position, serviceId), GitHubWorkflowLocalize.completionJobMappedserviceport().get()), ICON_NODE);
        }
        return List.of();
    }

    public static List<String> listServiceIds(PsiElement psiElement) {
        return getParentJob(psiElement)
            .flatMap(job -> getChild(job, FIELD_SERVICES))
            .map(services -> WorkflowPsi.getChildren(services).stream()
                .map(YAMLKeyValue::getKeyText)
                .toList())
            .orElseGet(List::of);
    }

    public static Optional<YAMLKeyValue> getService(PsiElement psiElement, String serviceId) {
        return getParentJob(psiElement)
            .flatMap(job -> getChild(job, FIELD_SERVICES))
            .flatMap(services -> WorkflowPsi.getChildren(services).stream()
                .filter(service -> serviceId.equals(service.getKeyText()))
                .findFirst());
    }

    public static List<String> listServicePorts(PsiElement psiElement, String serviceId) {
        return getService(psiElement, serviceId)
            .flatMap(service -> getChild(service, FIELD_PORTS))
            .map(ports -> getTextElements(ports).stream()
                .map(PsiElement::getText)
                .map(JobContext::toServicePort)
                .flatMap(Optional::stream)
                .distinct()
                .toList())
            .orElseGet(List::of);
    }

    private static void highlightContainer(AnnotationHolder holder, LeafPsiElement element, SimpleElement[] parts) {
        if (parts.length > 2 && isField2Valid(element, holder, parts[2], CONTAINER_FIELDS)) {
            ifEnoughItems(holder, element, parts, 2, 3, ignored -> {
            });
        }
    }

    private static void highlightServices(AnnotationHolder holder, LeafPsiElement element, SimpleElement[] parts) {
        if (parts.length < 3) {
            return;
        }
        if (!isDefinedItem0(element, holder, parts[2], listServiceIds(element))) {
            return;
        }
        if (parts.length == 3) {
            return;
        }
        if (!isField2Valid(element, holder, parts[3], SERVICE_FIELDS)) {
            return;
        }
        if (!FIELD_PORTS.equals(parts[3].text())) {
            ifEnoughItems(holder, element, parts, 2, 4, ignored -> {
            });
            return;
        }
        if (parts.length > 4) {
            isValidItem3(element, holder, parts[4], listServicePorts(element, parts[2].text()));
        }
        ifEnoughItems(holder, element, parts, 2, 5, ignored -> {
        });
    }

    private static Map<String, String> toMap(List<String> keys, String description) {
        return keys.stream().collect(Collectors.toMap(
            key -> key,
            key -> description,
            (existing, replacement) -> existing,
            LinkedHashMap::new
        ));
    }

    private static Optional<String> toServicePort(String text) {
        Matcher matcher = PORT_PATTERN.matcher(removeQuotes(text));
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private JobContext() {
    }
}
