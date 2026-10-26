/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.model.VariableReferenceResolver;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.util.dataholder.Key;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.syntax.Inputs.listInputsRaw;
import static com.github.yunabraska.githubworkflow.syntax.JobContext.getService;
import static com.github.yunabraska.githubworkflow.syntax.Jobs.listAllJobs;
import static com.github.yunabraska.githubworkflow.syntax.Needs.getJobNeed;
import static com.github.yunabraska.githubworkflow.syntax.Steps.listSteps;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ENVS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_GITHUB;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ID;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_JOB;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_MATRIX;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_NEEDS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ON;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_PORTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RUNNER;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_SERVICES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_STRATEGY;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_USES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_VARS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getTextElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.removeQuotes;
import static java.util.Optional.ofNullable;

public class WorkflowReferences {
    public static final Key<GitHubAction> ACTION_KEY = Key.create("GITHUB_WORKFLOW_ACTION_KEY");

    static Optional<PsiElement> textElement(PsiElement psiElement) {
        PsiElement current = psiElement;
        while (current != null && current.getParent() != current) {
            if (WorkflowPsi.isTextElement(current)) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    static Optional<PsiReference[]> referenceVariables(PsiElement psiElement) {
        PsiReference[] references = WorkflowReferences.resolve(psiElement).stream()
                .map(target -> new VariableReferenceResolver(
                        psiElement,
                        new TextRange(target.segment().startIndexOffset(), target.segment().endIndexOffset()),
                        target.target()
                ))
                .toArray(PsiReference[]::new);
        return references.length == 0 ? Optional.empty() : Optional.of(references);
    }

    public record Target(String kind, SimpleElement source, SimpleElement segment, PsiElement target) {
    }

    public static List<SimpleElement> toSimpleElements(PsiElement element) {
        if (getParent(element, FIELD_RUN).isPresent()) {
            return toSimpleElementsInExpressions(element);
        }
        List<SimpleElement> result = new ArrayList<>();
        String text = element.getText();
        int lineStart = 0;
        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            String line = text.substring(lineStart, lineEnd);
            if (!line.isBlank() && !line.trim().startsWith("#")) {
                int currentLineStart = lineStart;
                findDottedExpressions(line).stream()
                        .map(expression -> new SimpleElement(
                                expression.text(),
                                new TextRange(
                                        currentLineStart + expression.range().getStartOffset(),
                                        currentLineStart + expression.range().getEndOffset()
                                )
                        ))
                        .forEach(result::add);
            }
            if (lineEnd == text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        return result;
    }

    public static SimpleElement[] splitToElements(SimpleElement simpleElement) {
        List<SimpleElement> result = new ArrayList<>();
        AtomicInteger index = new AtomicInteger(0);
        while (index.get() < simpleElement.text().length()) {
            if (isIdentifierChar(simpleElement.text().charAt(index.get()))) {
                result.add(readIdentifier(simpleElement, index));
            }
            else {
                index.incrementAndGet();
            }
        }
        return result.toArray(SimpleElement[]::new);
    }

    public static List<Target> resolve(PsiElement psiElement) {
        return toSimpleElements(psiElement).stream()
                .flatMap(source -> resolveSource(psiElement, source).stream())
                .toList();
    }

    public static List<Target> resolveAt(PsiElement psiElement, int offsetInElement) {
        return resolve(psiElement).stream()
                .filter(target -> contains(target.segment(), offsetInElement))
                .toList();
    }

    public static Optional<SimpleElement> segmentAt(PsiElement psiElement, int offsetInElement) {
        return toSimpleElements(psiElement).stream()
                .filter(source -> contains(source, offsetInElement))
                .flatMap(source -> Stream.of(splitToElements(source)))
                .filter(segment -> contains(segment, offsetInElement))
                .findFirst();
    }

    private static boolean contains(SimpleElement segment, int offsetInElement) {
        return segment.startIndexOffset() - 1 <= offsetInElement && offsetInElement <= segment.endIndexOffset();
    }

    private static List<SimpleElement> findDottedExpressions(String text) {
        List<SimpleElement> elements = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            if (!isContextStart(text, index)) {
                index++;
                continue;
            }
            int start = index;
            boolean hasSeparator = false;
            index = readIdentifierEnd(text, index);
            while (index < text.length()) {
                char current = text.charAt(index);
                if (current == '.') {
                    hasSeparator = true;
                    index++;
                    index = readIdentifierEnd(text, index);
                }
                else if (current == '[') {
                    int closingBracket = findClosingBracket(text, index);
                    if (closingBracket < 0) {
                        break;
                    }
                    hasSeparator = true;
                    index = closingBracket + 1;
                }
                else {
                    break;
                }
            }
            if (hasSeparator && start < index) {
                elements.add(new SimpleElement(text.substring(start, index), new TextRange(start, index)));
            }
        }
        return elements;
    }

    private static List<SimpleElement> toSimpleElementsInExpressions(PsiElement element) {
        List<SimpleElement> result = new ArrayList<>();
        String text = element.getText();
        int index = 0;
        while (index < text.length()) {
            int expressionStart = text.indexOf("${{", index);
            if (expressionStart < 0) {
                break;
            }
            int bodyStart = expressionStart + 3;
            int expressionEnd = text.indexOf("}}", bodyStart);
            if (expressionEnd < 0) {
                break;
            }
            String body = text.substring(bodyStart, expressionEnd);
            findDottedExpressions(body).stream()
                    .map(expression -> new SimpleElement(
                            expression.text(),
                            new TextRange(
                                    bodyStart + expression.range().getStartOffset(),
                                    bodyStart + expression.range().getEndOffset()
                            )
                    ))
                    .forEach(result::add);
            index = expressionEnd + 2;
        }
        return result;
    }

    private static SimpleElement readIdentifier(SimpleElement simpleElement, AtomicInteger index) {
        int start = index.get();
        index.set(readIdentifierEnd(simpleElement.text(), start));
        return new SimpleElement(
                simpleElement.text().substring(start, index.get()),
                new TextRange(simpleElement.range().getStartOffset() + start, simpleElement.range().getStartOffset() + index.get())
        );
    }

    private static int readIdentifierEnd(String text, int start) {
        int index = start;
        while (index < text.length() && isIdentifierChar(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int findClosingBracket(String text, int start) {
        int index = start + 1;
        while (index < text.length()) {
            if (text.charAt(index) == ']') {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static boolean isContextStart(String text, int start) {
        return List.of(FIELD_INPUTS, FIELD_SECRETS, FIELD_ENVS, FIELD_GITHUB, FIELD_JOB, FIELD_RUNNER, FIELD_MATRIX, FIELD_STRATEGY, FIELD_STEPS, FIELD_JOBS, FIELD_NEEDS, FIELD_VARS)
                .stream()
                .anyMatch(context -> text.startsWith(context, start) && hasContextSeparator(text, start + context.length()));
    }

    private static boolean hasContextSeparator(String text, int index) {
        return index < text.length() && (text.charAt(index) == '.' || text.charAt(index) == '[');
    }

    public static boolean isIdentifierChar(char character) {
        return Character.isLetterOrDigit(character) || character == '_' || character == '-';
    }

    private static List<Target> resolveSource(PsiElement psiElement, SimpleElement source) {
        SimpleElement[] parts = splitToElements(source);
        if (parts.length < 2) {
            return List.of();
        }
        List<Target> result = new ArrayList<>();
        switch (parts[0].text()) {
            case FIELD_INPUTS -> resolveInput(psiElement, source, parts[1]).ifPresent(result::add);
            case FIELD_SECRETS -> resolveSecret(psiElement, source, parts[1]).ifPresent(result::add);
            case FIELD_ENVS -> resolveEnv(psiElement, source, parts[1]).ifPresent(result::add);
            case FIELD_MATRIX -> resolveMatrix(psiElement, source, parts[1]).ifPresent(result::add);
            case FIELD_JOB -> resolveJobContext(psiElement, source, parts).ifPresent(result::add);
            case FIELD_STEPS -> {
                resolveStep(psiElement, source, parts[1]).ifPresent(result::add);
                resolveStepOutput(psiElement, source, parts).ifPresent(result::add);
            }
            case FIELD_NEEDS -> {
                resolveNeed(psiElement, source, parts[1]).ifPresent(result::add);
                resolveNeedOutput(psiElement, source, parts).ifPresent(result::add);
            }
            case FIELD_JOBS -> {
                resolveJob(psiElement, source, parts[1]).ifPresent(result::add);
                resolveJobOutput(psiElement, source, parts).ifPresent(result::add);
            }
            default -> {
            }
        }
        return result;
    }

    private static Optional<Target> resolveInput(
            PsiElement psiElement,
            SimpleElement source,
            SimpleElement inputId
    ) {
        return listInputsRaw(psiElement).stream()
                .filter(input -> inputId.text().equals(input.getKeyText()))
                .findFirst()
                .map(input -> new Target("input", source, inputId, input));
    }

    private static Optional<Target> resolveSecret(
            PsiElement psiElement,
            SimpleElement source,
            SimpleElement secretId
    ) {
        return getChild(psiElement.getContainingFile(), FIELD_ON)
                .stream()
                .flatMap(on -> WorkflowPsi.getAllElements(on, FIELD_SECRETS).stream())
                .flatMap(secrets -> WorkflowPsi.getChildren(secrets).stream())
                .filter(secret -> secretId.text().equals(secret.getKeyText()))
                .findFirst()
                .map(secret -> new Target("secret", source, secretId, secret));
    }

    private static Optional<Target> resolveEnv(
            PsiElement psiElement,
            SimpleElement source,
            SimpleElement envId
    ) {
        return Stream.of(stepEnv(psiElement, envId), jobEnv(psiElement, envId), workflowEnv(psiElement, envId))
                .flatMap(Optional::stream)
                .findFirst()
                .map(env -> new Target("env", source, envId, env));
    }

    private static Optional<YAMLKeyValue> stepEnv(PsiElement psiElement, SimpleElement envId) {
        return WorkflowPsi.getParentStep(psiElement)
                .flatMap(step -> getChild(step, FIELD_ENVS))
                .flatMap(env -> childByKey(env, envId.text()));
    }

    private static Optional<YAMLKeyValue> jobEnv(PsiElement psiElement, SimpleElement envId) {
        return WorkflowPsi.getParentJob(psiElement)
                .flatMap(job -> getChild(job, FIELD_ENVS))
                .flatMap(env -> childByKey(env, envId.text()));
    }

    private static Optional<YAMLKeyValue> workflowEnv(PsiElement psiElement, SimpleElement envId) {
        return getChild(psiElement.getContainingFile(), FIELD_ENVS)
                .flatMap(env -> childByKey(env, envId.text()));
    }

    private static Optional<Target> resolveMatrix(
            PsiElement psiElement,
            SimpleElement source,
            SimpleElement matrixId
    ) {
        return WorkflowPsi.getParentJob(psiElement)
                .flatMap(job -> getChild(job, FIELD_STRATEGY))
                .flatMap(strategy -> getChild(strategy, FIELD_MATRIX))
                .flatMap(matrix -> matrixProperty(matrix, matrixId.text()))
                .map(matrix -> new Target("matrix", source, matrixId, matrix));
    }

    private static Optional<Target> resolveJobContext(
            PsiElement psiElement,
            SimpleElement source,
            SimpleElement[] parts
    ) {
        if (parts.length >= 3 && FIELD_SERVICES.equals(parts[1].text())) {
            if (parts.length >= 5 && FIELD_PORTS.equals(parts[3].text())) {
                return getService(psiElement, parts[2].text())
                        .flatMap(service -> getChild(service, FIELD_PORTS))
                        .map(ports -> new Target("service-port", source, parts[4], ports));
            }
            return getService(psiElement, parts[2].text())
                    .map(service -> new Target("service", source, parts[2], service));
        }
        if (parts.length >= 3 && "container".equals(parts[1].text())) {
            return WorkflowPsi.getParentJob(psiElement)
                    .flatMap(job -> getChild(job, "container"))
                    .map(container -> new Target("container", source, parts[2], container));
        }
        return Optional.empty();
    }

    private static Optional<YAMLKeyValue> matrixProperty(YAMLKeyValue matrix, String key) {
        return Stream.concat(
                        WorkflowPsi.getChildren(matrix).stream()
                                .filter(WorkflowReferences::isDirectMatrixProperty),
                        getChild(matrix, "include")
                                .stream()
                                .flatMap(include -> WorkflowPsi.getChildren(include, YAMLSequenceItem.class).stream())
                                .flatMap(item -> WorkflowPsi.getChildren(item).stream())
                )
                .filter(property -> key.equals(property.getKeyText()))
                .findFirst();
    }

    private static boolean isDirectMatrixProperty(YAMLKeyValue keyValue) {
        String key = keyValue.getKeyText();
        return !"include".equals(key) && !"exclude".equals(key);
    }

    private static Optional<Target> resolveStep(
            PsiElement psiElement,
            SimpleElement source,
            SimpleElement stepId
    ) {
        return listSteps(psiElement).stream()
                .map(step -> getChild(step, FIELD_ID).orElse(null))
                .filter(Objects::nonNull)
                .filter(id -> getText(id).filter(stepId.text()::equals).isPresent())
                .findFirst()
                .map(step -> new Target("step", source, stepId, step));
    }

    private static Optional<Target> resolveStepOutput(
            PsiElement psiElement,
            SimpleElement source,
            SimpleElement[] parts
    ) {
        if (parts.length < 4 || !FIELD_OUTPUTS.equals(parts[2].text())) {
            return Optional.empty();
        }
        return listSteps(psiElement).stream()
                .filter(step -> getText(step, FIELD_ID).filter(parts[1].text()::equals).isPresent())
                .findFirst()
                .flatMap(step -> stepOutputTarget(step, parts[3].text()))
                .map(output -> new Target("step-output", source, parts[3], output));
    }

    private static Optional<PsiElement> stepOutputTarget(YAMLSequenceItem step, String outputId) {
        return getChild(step, FIELD_RUN)
                .filter(run -> WorkflowPsi.parseOutputVariables(run).stream().anyMatch(output -> outputId.equals(output.key())))
                .map(PsiElement.class::cast)
                .or(() -> getChild(step, FIELD_USES)
                        .filter(uses -> Action.listActionsOutputs(step).stream()
                                .anyMatch(output -> outputId.equals(output.key())))
                        .map(PsiElement.class::cast));
    }

    private static Optional<Target> resolveNeed(
            PsiElement psiElement,
            SimpleElement source,
            SimpleElement needId
    ) {
        return getJobNeed(psiElement).stream()
                .flatMap(need -> getTextElements(need).stream())
                .filter(need -> needId.text().equals(removeQuotes(need.getText())))
                .findFirst()
                .map(need -> new Target("need", source, needId, need));
    }

    private static Optional<Target> resolveNeedOutput(
            PsiElement psiElement,
            SimpleElement source,
            SimpleElement[] parts
    ) {
        if (parts.length < 4 || !FIELD_OUTPUTS.equals(parts[2].text())) {
            return Optional.empty();
        }
        return jobById(psiElement, parts[1].text())
                .flatMap(job -> jobOutput(job, parts[3].text()))
                .map(output -> new Target("need-output", source, parts[3], output));
    }

    private static Optional<Target> resolveJob(
            PsiElement psiElement,
            SimpleElement source,
            SimpleElement jobId
    ) {
        return jobById(psiElement, jobId.text())
                .map(job -> new Target("job", source, jobId, job));
    }

    private static Optional<Target> resolveJobOutput(
            PsiElement psiElement,
            SimpleElement source,
            SimpleElement[] parts
    ) {
        if (parts.length < 4 || !FIELD_OUTPUTS.equals(parts[2].text())) {
            return Optional.empty();
        }
        return jobById(psiElement, parts[1].text())
                .flatMap(job -> jobOutput(job, parts[3].text()))
                .map(output -> new Target("job-output", source, parts[3], output));
    }

    private static Optional<YAMLKeyValue> jobById(PsiElement psiElement, String jobId) {
        return listAllJobs(psiElement).stream()
                .filter(job -> jobId.equals(job.getKeyText()))
                .findFirst();
    }

    private static Optional<YAMLKeyValue> jobOutput(YAMLKeyValue job, String outputId) {
        return getChild(job, FIELD_OUTPUTS)
                .flatMap(outputs -> childByKey(outputs, outputId));
    }

    private static Optional<YAMLKeyValue> childByKey(@Nullable PsiElement parent, String key) {
        return ofNullable(parent)
                .stream()
                .flatMap(element -> WorkflowPsi.getChildren(element).stream())
                .filter(child -> key.equals(child.getKeyText()))
                .findFirst();
    }

    private WorkflowReferences() {
    }
}
