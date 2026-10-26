/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.entry;

import com.github.yunabraska.githubworkflow.git.WorkflowLocation;
import com.github.yunabraska.githubworkflow.model.IconRenderer;
import com.github.yunabraska.githubworkflow.model.NodeIcon;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.SyntaxAnnotation;
import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.github.yunabraska.githubworkflow.syntax.WorkflowReferences;
import com.github.yunabraska.githubworkflow.syntax.WorkflowSyntax;
import com.github.yunabraska.githubworkflow.syntax.WorkflowYaml;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.codeEditor.DefaultLanguageHighlighterColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.document.util.TextRange;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.isChildOf;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.pathEndsWith;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.pathMatches;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_TEXT_VARIABLE;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.RELOAD;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.SUPPRESS_ON;
import static com.github.yunabraska.githubworkflow.syntax.Action.highLightAction;
import static com.github.yunabraska.githubworkflow.syntax.Action.highlightActionInput;
import static com.github.yunabraska.githubworkflow.syntax.Envs.highLightEnvs;
import static com.github.yunabraska.githubworkflow.syntax.Inputs.highLightInputs;
import static com.github.yunabraska.githubworkflow.syntax.JobContext.highlightJob;
import static com.github.yunabraska.githubworkflow.syntax.Jobs.highLightJobs;
import static com.github.yunabraska.githubworkflow.syntax.Matrix.highlightMatrix;
import static com.github.yunabraska.githubworkflow.syntax.Needs.highlightNeeds;
import static com.github.yunabraska.githubworkflow.syntax.Secrets.highLightSecrets;
import static com.github.yunabraska.githubworkflow.syntax.Steps.highlightSteps;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.deleteElementAction;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.getFirstChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.ifEnoughItems;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.isDefinedItem0;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.replaceAction;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowAnnotations.simpleTextRange;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.*;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getAllElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentJob;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStep;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getTextElement;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.goToDeclarationString;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.parseEnvVariables;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.parseOutputVariables;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.toYAMLKeyValue;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowReferences.splitToElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowReferences.toSimpleElements;
import static consulo.language.editor.annotation.HighlightSeverity.INFORMATION;
import static java.util.Optional.ofNullable;

public class WorkflowAnnotator implements Annotator {

    public static final TextAttributesKey VARIABLE_REFERENCE = TextAttributesKey.createTextAttributesKey(
            "GITHUB_WORKFLOW_VARIABLE_REFERENCE",
            DefaultLanguageHighlighterColors.CONSTANT
    );
    public static final TextAttributesKey DECLARATION = TextAttributesKey.createTextAttributesKey(
            "GITHUB_WORKFLOW_DECLARATION",
            DefaultLanguageHighlighterColors.STATIC_FIELD
    );
    public static final TextAttributesKey RUNNER_VARIABLE = TextAttributesKey.createTextAttributesKey(
            "GITHUB_WORKFLOW_RUNNER_VARIABLE",
            DefaultLanguageHighlighterColors.GLOBAL_VARIABLE
    );
    public static final TextAttributesKey SCALAR_LITERAL = TextAttributesKey.createTextAttributesKey(
            "GITHUB_WORKFLOW_SCALAR_LITERAL",
            DefaultLanguageHighlighterColors.NUMBER
    );

    @RequiredReadAction
    @Override
    public void annotate(PsiElement psiElement, AnnotationHolder holder) {
        annotationTrigger(holder, psiElement).ifPresent(trigger -> trigger
                .then(WorkflowAnnotator::processPsiElement)
                .then(WorkflowAnnotator::variableElementHandler)
                .then(WorkflowAnnotator::highlightVariableReferences)
                .then(WorkflowAnnotator::highlightDeclarations)
                .then(WorkflowAnnotator::highlightRunOutputs)
                .then(WorkflowAnnotator::highlightRunnerVariables)
                .then(WorkflowAnnotator::highlightScalarLiterals)
                .then(WorkflowAnnotator::validateWorkflowSyntax)
                .then((currentHolder, currentElement) -> highlightActionInput(currentHolder, currentElement))
                .then((currentHolder, currentElement) -> highlightNeeds(currentHolder, currentElement)));
    }

    private static Optional<AnnotationTrigger> annotationTrigger(AnnotationHolder holder, PsiElement psiElement) {
        return psiElement.isValid() && WorkflowYaml.isWorkflowElement(psiElement)
                ? Optional.of(new AnnotationTrigger(holder, psiElement))
                : Optional.empty();
    }

    public static void processPsiElement(AnnotationHolder holder, PsiElement psiElement) {
        toYAMLKeyValue(psiElement).ifPresent(element -> {
            switch (element.getKeyText()) {
                case FIELD_USES -> highLightAction(holder, element);
                case FIELD_OUTPUTS -> outputsHandler(holder, element);
                default -> {
                }
            }
        });
    }

    private static void highlightRunOutputs(AnnotationHolder holder, PsiElement psiElement) {
        Optional.of(psiElement)
                .filter(LeafPsiElement.class::isInstance)
                .map(LeafPsiElement.class::cast)
                .filter(element -> WorkflowPsi.getParent(element, FIELD_RUN).isPresent())
                .ifPresent(element -> Stream.of(
                        parseEnvVariables(element).stream().map(variable -> withIcon(variable, ICON_ENV)).toList(),
                        parseOutputVariables(element).stream().map(variable -> withIcon(variable, ICON_TEXT_VARIABLE)).toList()
                ).flatMap(Collection::stream).collect(Collectors.groupingBy(SimpleElement::startIndexOffset)).forEach((integer, elements) -> ofNullable(getFirstChild(elements)).ifPresent(lineElement -> holder
                        .newSilentAnnotation(INFORMATION)
                        .range(lineElement.range())
                        .textAttributes(DECLARATION)
                        .gutterIconRenderer(new IconRenderer(null, element, lineElement.icon()))
                        .create()
                )));
    }

    private static SimpleElement withIcon(SimpleElement element, NodeIcon icon) {
        return new SimpleElement(element.key(), element.text(), element.range(), icon);
    }

    private static void highlightRunnerVariables(AnnotationHolder holder, PsiElement psiElement) {
        Optional.of(psiElement)
                .filter(LeafPsiElement.class::isInstance)
                .map(LeafPsiElement.class::cast)
                .filter(element -> getParent(element, FIELD_RUN).isPresent())
                .ifPresent(element -> defaultEnvs().keySet().forEach(name -> highlightWord(holder, element, name, RUNNER_VARIABLE)));
    }

    private static void highlightWord(
            AnnotationHolder holder,
            PsiElement element,
            String word,
            TextAttributesKey attributes
    ) {
        String text = element.getText();
        int index = text.indexOf(word);
        while (index >= 0) {
            int end = index + word.length();
            boolean before = index == 0 || !isIdentifierChar(text.charAt(index - 1));
            boolean after = end >= text.length() || !isIdentifierChar(text.charAt(end));
            if (before && after) {
                holder.newSilentAnnotation(INFORMATION)
                        .range(new TextRange(element.getTextRange().getStartOffset() + index, element.getTextRange().getStartOffset() + end))
                        .textAttributes(attributes)
                        .create();
            }
            index = text.indexOf(word, end);
        }
    }

    private static void highlightScalarLiterals(AnnotationHolder holder, PsiElement psiElement) {
        toYAMLKeyValue(psiElement)
                .flatMap(WorkflowPsi::getTextElement)
                .filter(text -> text.getText().matches("true|false|-?\\d+(?:\\.\\d+)?"))
                .ifPresent(text -> holder.newSilentAnnotation(INFORMATION)
                        .range(text)
                        .textAttributes(SCALAR_LITERAL)
                        .create());
    }

    private static void validateWorkflowSyntax(AnnotationHolder holder, PsiElement psiElement) {
        if (!(psiElement instanceof YAMLKeyValue)) {
            return;
        }
        WorkflowLocation.from(psiElement)
                .filter(WorkflowAnnotator::shouldValidateWorkflowSyntax)
                .ifPresent(location -> validateWorkflowKeyValue(
                        holder,
                        location.keyValue(),
                        location.path()
                ));
    }

    private static boolean shouldValidateWorkflowSyntax(WorkflowLocation location) {
        return location.workflowFile() || isUnitTestWorkflowFile(location.keyValue());
    }

    private static boolean isUnitTestWorkflowFile(YAMLKeyValue element) {
        return Application.get().isUnitTestMode()
                && WorkflowPsi.getChild(element.getContainingFile(), "runs").isEmpty();
    }

    private static void validateWorkflowKeyValue(
            AnnotationHolder holder,
            YAMLKeyValue element,
            List<String> path
    ) {
        WorkflowSyntax.validationKeysForPath(path).ifPresent(keys -> {
            validateKnownKey(holder, element, keys.values(), keys.message());
            validateWorkflowPropertyValue(holder, element, path);
        });
    }

    private static void validateWorkflowPropertyValue(
            AnnotationHolder holder,
            YAMLKeyValue element,
            List<String> path
    ) {
        String key = element.getKeyText();
        if (isChildOf(path, FIELD_ON, "workflow_dispatch", FIELD_INPUTS)
                || isChildOf(path, FIELD_ON, "workflow_call", FIELD_INPUTS)) {
            validateWorkflowInputPropertyValue(holder, element, path);
        }
        if (isChildOf(path, FIELD_ON, "workflow_call", FIELD_SECRETS) && "required".equals(key)) {
            validateKnownValue(holder, element, WorkflowSyntax.booleanValues(), GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntriggervalue);
        }
        if (pathMatches(path, FIELD_ON, "*") && "types".equals(key)) {
            validateKnownValue(holder, element, WorkflowSyntax.eventActivityTypesFor(path.get(1)), GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntriggervalue);
        }
        if (pathEndsWith(path, "permissions")) {
            validateKnownValue(holder, element, WorkflowSyntax.permissionValuesFor(key), GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknownpermissionvalue);
        }
    }

    private static void validateWorkflowInputPropertyValue(
            AnnotationHolder holder,
            YAMLKeyValue element,
            List<String> path
    ) {
        if ("type".equals(element.getKeyText())) {
            validateKnownValue(holder, element, WorkflowSyntax.workflowInputTypesFor(path.get(1)), GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntriggervalue);
        }
        if ("required".equals(element.getKeyText())) {
            validateKnownValue(holder, element, WorkflowSyntax.booleanValues(), GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntriggervalue);
        }
    }

    private static void validateKnownKey(
            AnnotationHolder holder,
            YAMLKeyValue element,
            Map<String, String> allowed,
            Function<Object, LocalizeValue> message
    ) {
        if (allowed.containsKey(element.getKeyText()) || element.getKeyText().isBlank()) {
            return;
        }
        TextRange range = Optional.ofNullable(element.getKey())
                .map(PsiElement::getTextRange)
                .orElseGet(element::getTextRange);
        createKnownAnnotation(holder, element, range, message.apply(element.getKeyText()), allowed);
    }

    private static void validateKnownValue(
            AnnotationHolder holder,
            YAMLKeyValue element,
            Map<String, String> allowed,
            Function<Object, LocalizeValue> message
    ) {
        String value = WorkflowPsi.getText(element).orElse("");
        if (allowed.isEmpty()
                || value.isBlank()
                || value.startsWith("${{")
                || !value.matches("[A-Za-z0-9_-]+")
                || allowed.containsKey(value)) {
            return;
        }
        WorkflowPsi.getTextElement(element).ifPresent(valueElement -> {
            TextRange range = valueElement.getTextRange();
            createKnownAnnotation(holder, element, range, message.apply(value), allowed);
        });
    }

    private static void createKnownAnnotation(
            AnnotationHolder holder,
            YAMLKeyValue element,
            TextRange range,
            LocalizeValue message,
            Map<String, String> allowed
    ) {
        List<SyntaxAnnotation> fixes = new ArrayList<>();
        fixes.add(new SyntaxAnnotation(message, null, HighlightSeverity.WEAK_WARNING, ProblemHighlightType.WEAK_WARNING, null));
        allowed.keySet().stream()
                .map(candidate -> new SyntaxAnnotation(
                        GitHubWorkflowLocalize.inspectionReplaceWith(candidate),
                        RELOAD,
                        HighlightSeverity.WEAK_WARNING,
                        ProblemHighlightType.WEAK_WARNING,
                        replaceAction(range, candidate)
                ))
                .forEach(fixes::add);
        SyntaxAnnotation.createAnnotation(element, range, holder, fixes);
    }

    private static void outputsHandler(AnnotationHolder holder, PsiElement psiElement) {
        getParentJob(psiElement).ifPresent(job -> {
            List<YAMLKeyValue> outputs = WorkflowPsi.getChildren(psiElement).stream().toList();
            String workflowText = WorkflowPsi.getChild(psiElement.getContainingFile(), FIELD_JOBS).map(PsiElement::getText).orElse("");
            List<String> workflowOutputs = WorkflowPsi.getChild(psiElement.getContainingFile(), FIELD_ON)
                    .map(on -> getAllElements(on, FIELD_OUTPUTS))
                    .map(list -> list.stream().flatMap(keyValue -> WorkflowPsi.getChildren(keyValue).stream().map(output -> getText(output, "value").orElse(""))).toList())
                    .orElseGet(Collections::emptyList);
            outputs.stream().filter(output -> {
                String outputKey = output.getKeyText();
                String reusableOutputReference = FIELD_JOBS + "." + job.getKeyText() + "." + FIELD_OUTPUTS + "." + outputKey;
                String needsOutputReference = FIELD_NEEDS + "." + job.getKeyText() + "." + FIELD_OUTPUTS + "." + outputKey;
                return workflowOutputs.stream().noneMatch(value -> containsOutputReference(value, reusableOutputReference))
                        && !containsOutputReference(workflowText, needsOutputReference);
            }).forEach(output -> new SyntaxAnnotation(
                    GitHubWorkflowLocalize.inspectionOutputUnused(output.getKeyText()),
                    SUPPRESS_ON,
                    HighlightSeverity.WEAK_WARNING,
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                    deleteElementAction(output.getTextRange()),
                    true
            ).createAnnotation(output, output.getTextRange(), holder));

        });
    }

    private static boolean containsOutputReference(String text, String reference) {
        int index = ofNullable(text).orElse("").indexOf(reference);
        while (index >= 0) {
            int end = index + reference.length();
            if (end >= text.length() || !isIdentifierChar(text.charAt(end))) {
                return true;
            }
            index = text.indexOf(reference, end);
        }
        return false;
    }

    public static Predicate<PsiElement> isElementWithVariables(@Nullable YAMLKeyValue parentIf) {
        return element -> ofNullable(parentIf)
                .or(() -> getParent(element, FIELD_RUN))
                .or(() -> getParent(element, FIELD_ID))
                .or(() -> getParent(element, "name"))
                .or(() -> getParent(element, "run-name"))
                .or(() -> getParent(element, "runs-on"))
                .or(() -> getParent(element, "concurrency"))
                .or(() -> getParent(element, "group").filter(group -> getParent(group, "concurrency").isPresent()))
                .or(() -> getParent(element, "default").filter(defaultValue -> getParent(defaultValue, FIELD_INPUTS).isPresent()))
                .or(() -> getParent(element, "credentials"))
                .or(() -> getParent(element, "environment"))
                .or(() -> getParent(element, "fail-fast").filter(failFast -> getParent(failFast, FIELD_STRATEGY).isPresent()))
                .or(() -> getParent(element, "max-parallel").filter(maxParallel -> getParent(maxParallel, FIELD_STRATEGY).isPresent()))
                .or(() -> getParent(element, "shell").filter(shell -> getParent(shell, "defaults").isPresent()))
                .or(() -> getParent(element, "container").filter(container -> getParent(container, "jobs").isPresent()))
                .or(() -> getParent(element, "url").filter(url -> getParent(url, "environment").isPresent()))
                .or(() -> getParent(element, "timeout-minutes"))
                .or(() -> getParent(element, "continue-on-error"))
                .or(() -> getParent(element, "working-directory"))
                .or(() -> getParent(element, "image").filter(image -> getParent(image, "container").isPresent() || getParent(image, "services").isPresent()))
                .or(() -> getParent(element, "value").isPresent() ? getParent(element, FIELD_OUTPUTS) : Optional.empty())
                .or(() -> getParent(element, FIELD_WITH))
                .or(() -> getParent(element, FIELD_ENVS))
                .or(() -> getParent(element, FIELD_OUTPUTS))
                .isPresent();
    }

    private static void variableElementHandler(AnnotationHolder holder, PsiElement psiElement) {
        Optional<YAMLKeyValue> parentIf = getParent(psiElement, FIELD_IF);
        Optional.of(psiElement)
                .filter(LeafPsiElement.class::isInstance)
                .map(LeafPsiElement.class::cast)
                .filter(isElementWithVariables(parentIf.orElse(null)))
                .ifPresent(element -> toSimpleElements(element).forEach(simpleElement -> {
                            SimpleElement[] parts = splitToElements(simpleElement);
                            switch (parts.length > 0 ? parts[0].text() : "N/A") {
                                case FIELD_INPUTS -> highLightInputs(holder, element, parts);
                                case FIELD_SECRETS ->
                                        highLightSecrets(holder, psiElement, element, simpleElement, parts, parentIf.orElse(null));
                                case FIELD_ENVS -> highLightEnvs(holder, element, parts);
                                case FIELD_GITHUB -> highlightContext(holder, element, parts, FIELD_GITHUB, -1);
                                case FIELD_JOB -> highlightJob(holder, element, parts);
                                case FIELD_RUNNER -> highlightContext(holder, element, parts, FIELD_RUNNER, 2);
                                case FIELD_MATRIX -> highlightMatrix(holder, element, parts);
                                case FIELD_STRATEGY -> highlightContext(holder, element, parts, FIELD_STRATEGY, 2);
                                case FIELD_STEPS -> highlightSteps(holder, element, parts);
                                case FIELD_JOBS -> highLightJobs(holder, element, parts);
                                case FIELD_NEEDS -> highlightNeeds(holder, element, parts);
                                default -> {
                                }
                            }
                        })
                );
    }

    private static void highlightContext(
            AnnotationHolder holder,
            LeafPsiElement element,
            SimpleElement[] parts,
            String field,
            int maxParts
    ) {
        ifEnoughItems(holder, element, parts, 2, maxParts, item -> isDefinedItem0(element, holder, item, DEFAULT_VALUE_MAP.get(field).get().keySet()));
    }

    private static void highlightVariableReferences(AnnotationHolder holder, PsiElement psiElement) {
        Optional.of(psiElement)
                .filter(WorkflowPsi::isTextElement)
                .ifPresent(element -> {
                    toSimpleElements(element).stream()
                            .flatMap(source -> Stream.of(splitToElements(source)))
                            .forEach(segment -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                                    .range(simpleTextRange(element, segment))
                                    .textAttributes(VARIABLE_REFERENCE)
                                    .create());
                    WorkflowReferences.resolve(element).forEach(target -> {
                    String tooltip = goToDeclarationString();
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                            .range(simpleTextRange(element, target.segment()))
                            .textAttributes(VARIABLE_REFERENCE)
                            .create();
                    holder.newAnnotation(HighlightSeverity.INFORMATION, tooltip)
                            .range(simpleTextRange(element, target.segment()))
                            .textAttributes(DefaultLanguageHighlighterColors.HIGHLIGHTED_REFERENCE)
                            .tooltip(tooltip)
                            .create();
                    });
                });
    }

    private static void highlightDeclarations(AnnotationHolder holder, PsiElement psiElement) {
        toYAMLKeyValue(psiElement).ifPresent(element -> {
            highlightJobDeclaration(holder, element);
            highlightStepDeclaration(holder, element);
        });
    }

    private static void highlightJobDeclaration(AnnotationHolder holder, YAMLKeyValue element) {
        getParent(element, FIELD_JOBS)
                .filter(jobs -> isDirectChildOf(element, jobs))
                .flatMap(job -> ofNullable(element.getKey()))
                .ifPresent(key -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(key)
                        .textAttributes(DECLARATION)
                        .create());
    }

    private static boolean isDirectChildOf(YAMLKeyValue child, YAMLKeyValue parent) {
        PsiElement current = child.getParent();
        while (current != null && current != parent) {
            if (current instanceof YAMLKeyValue) {
                return false;
            }
            current = current.getParent();
        }
        return current == parent;
    }

    private static void highlightStepDeclaration(AnnotationHolder holder, YAMLKeyValue element) {
        if (FIELD_ID.equals(element.getKeyText()) && getParentStep(element).isPresent()) {
            getTextElement(element).ifPresent(text -> holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(text)
                    .textAttributes(DECLARATION)
                    .create());
        }
    }

    private static boolean isIdentifierChar(char character) {
        return WorkflowReferences.isIdentifierChar(character);
    }

    private record AnnotationTrigger(AnnotationHolder holder, PsiElement psiElement) {

        private AnnotationTrigger then(BiConsumer<AnnotationHolder, PsiElement> step) {
            step.accept(holder, psiElement);
            return this;
        }
    }

}
