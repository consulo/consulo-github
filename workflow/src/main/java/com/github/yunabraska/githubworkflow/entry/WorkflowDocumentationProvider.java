/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.entry;

import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.state.GitHubActionCache;
import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.github.yunabraska.githubworkflow.syntax.WorkflowReferences;
import com.github.yunabraska.githubworkflow.syntax.WorkflowSyntax;
import com.github.yunabraska.githubworkflow.syntax.WorkflowYaml;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.language.Language;
import consulo.language.editor.documentation.DocumentationProviderEx;
import consulo.language.editor.documentation.LanguageDocumentationProvider;
import consulo.language.impl.psi.FakePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.github.yunabraska.githubworkflow.syntax.Steps.listStepOutputs;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ON;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_USES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_WITH;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStepOrJob;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getText;
import static java.util.Optional.ofNullable;

@ExtensionImpl(id = "GithubWorkflowDocumentationProvider", order = "before YamlJsonSchemaDocumentationProvider")
public class WorkflowDocumentationProvider extends DocumentationProviderEx implements LanguageDocumentationProvider {
    @Override
    public Language getLanguage() {
        return YAMLLanguage.INSTANCE;
    }

    @RequiredReadAction
    @Override
    public @Nullable PsiElement getCustomDocumentationElement(Editor editor, PsiFile file, @Nullable PsiElement contextElement) {
        if (contextElement == null || !WorkflowYaml.isWorkflowElement(file)) {
            return null;
        }
        int targetOffset = targetOffset(editor, contextElement);
        if (targetOffset < 0 || targetOffset > file.getTextLength()) {
            return null;
        }
        return documentationCandidates(file, contextElement, targetOffset).stream()
                .flatMap(candidate -> documentationAt(candidate, targetOffset).stream())
                .findFirst()
                .map(payload -> new WorkflowDocumentationElement(contextElement, payload))
                .orElse(null);
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return element instanceof WorkflowDocumentationElement workflowElement
                ? workflowElement.payload().hint()
                : null;
    }

    @Override
    public @Nullable String generateHoverDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return generateDoc(element, originalElement);
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        return element instanceof WorkflowDocumentationElement workflowElement
                ? workflowElement.payload().html()
                : null;
    }

    private static int targetOffset(@Nullable Editor editor, PsiElement contextElement) {
        TextRange range = contextElement.getTextRange();
        if (range == null) {
            return -1;
        }
        if (editor != null) {
            int caret = editor.getCaretModel().getOffset();
            if (range.getStartOffset() <= caret && caret <= range.getEndOffset()) {
                return caret;
            }
        }
        return range.getStartOffset();
    }

    static Optional<DocPayload> documentationAt(PsiElement element, int absoluteOffset) {
        return declaredOutputDoc(element)
                .or(() -> actionParameterDoc(element))
                .or(() -> workflowSyntaxDoc(element, absoluteOffset))
                .or(() -> textElement(element).flatMap(WorkflowDocumentationProvider::actionUseDoc))
                .or(() -> textElement(element).flatMap(text -> variableDoc(text, absoluteOffset)));
    }

    private static List<PsiElement> documentationCandidates(PsiFile file, PsiElement contextElement, int targetOffset) {
        Set<PsiElement> result = new LinkedHashSet<>();
        addCandidate(result, contextElement);
        addCandidate(result, file.findElementAt(Math.min(targetOffset, Math.max(0, file.getTextLength() - 1))));
        if (targetOffset > 0) {
            addCandidate(result, file.findElementAt(targetOffset - 1));
        }
        if (targetOffset + 1 < file.getTextLength()) {
            addCandidate(result, file.findElementAt(targetOffset + 1));
        }
        return List.copyOf(result);
    }

    private static void addCandidate(Set<PsiElement> result, PsiElement element) {
        PsiElement current = element;
        while (current != null && current.getParent() != current) {
            result.add(current);
            if (current instanceof YAMLKeyValue) {
                return;
            }
            current = current.getParent();
        }
    }

    private static Optional<DocPayload> actionUseDoc(PsiElement textElement) {
        return getParent(textElement, FIELD_USES)
                .map(GitHubActionCache::getAction)
                .map(WorkflowDocumentationProvider::actionDoc);
    }

    private static Optional<DocPayload> actionParameterDoc(PsiElement element) {
        return keyValueAt(element)
                .filter(item -> getParent(item.getParent(), FIELD_WITH).isPresent()
                        || getParent(item.getParent(), FIELD_SECRETS).isPresent())
                .flatMap(item -> getParentStepOrJob(item)
                        .map(GitHubActionCache::getAction)
                        .flatMap(action -> parameterDoc(item, action)));
    }

    private static Optional<DocPayload> parameterDoc(YAMLKeyValue item, GitHubAction action) {
        if (action == null || !action.isResolved()) {
            return Optional.empty();
        }
        boolean secret = getParent(item.getParent(), FIELD_SECRETS).isPresent();
        String name = item.getKeyText();
        String details = secret ? action.freshSecrets().get(name) : action.freshInputs().get(name);
        String label = secret ? GitHubWorkflowLocalize.documentationSecretLabel().get() : GitHubWorkflowLocalize.documentationInputLabel().get();
        return ofNullable(details)
                .map(text -> new DocPayload(
                        label + " " + name,
                        renderParameter(label, name, text, action.githubUrl()),
                        plainParameter(label, name, text)
                ));
    }

    private static Optional<DocPayload> variableDoc(PsiElement textElement, int absoluteOffset) {
        int offsetInElement = absoluteOffset - textElement.getTextRange().getStartOffset();
        Optional<WorkflowReferences.Target> target = WorkflowReferences.resolveAt(textElement, offsetInElement).stream().findFirst();
        if (target.isPresent()) {
            return Optional.of(referenceDoc(target.get()));
        }
        return WorkflowReferences.segmentAt(textElement, offsetInElement)
                .flatMap(WorkflowDocumentationProvider::contextDoc);
    }

    private static DocPayload referenceDoc(WorkflowReferences.Target target) {
        return switch (target.kind()) {
            case "input" -> yamlParameterDoc(GitHubWorkflowLocalize.documentationInputLabel().get(), true, target.segment().text(), target.target());
            case "secret" -> yamlParameterDoc(GitHubWorkflowLocalize.documentationSecretLabel().get(), false, target.segment().text(), target.target());
            case "env" -> yamlValueDoc(GitHubWorkflowLocalize.documentationEnvLabel().get(), target.segment().text(), target.target());
            case "matrix" -> yamlValueDoc(GitHubWorkflowLocalize.documentationMatrixLabel().get(), target.segment().text(), target.target());
            case "step" -> stepDoc(target.target());
            case "step-output" -> stepOutputDoc(target.segment().text(), target.target());
            case "need" -> simpleDoc(GitHubWorkflowLocalize.documentationNeedLabel().get(), target.segment().text(), GitHubWorkflowLocalize.documentationNeedDescription().get());
            case "need-output" -> outputDoc(GitHubWorkflowLocalize.documentationNeedoutputLabel().get(), target.segment().text(), target.target());
            case "job" -> simpleDoc(GitHubWorkflowLocalize.documentationReusablejobLabel().get(), target.segment().text(), GitHubWorkflowLocalize.documentationReusablejobDescription().get());
            case "job-output" -> outputDoc(GitHubWorkflowLocalize.documentationReusablejoboutputLabel().get(), target.segment().text(), target.target());
            case "service" -> yamlValueDoc(GitHubWorkflowLocalize.documentationServiceLabel().get(), target.segment().text(), target.target());
            case "service-port" -> yamlValueDoc(GitHubWorkflowLocalize.documentationServiceportLabel().get(), target.segment().text(), target.target());
            case "container" -> yamlValueDoc(GitHubWorkflowLocalize.documentationContainerLabel().get(), target.segment().text(), target.target());
            default -> simpleDoc(GitHubWorkflowLocalize.documentationSymbolLabel().get(), target.segment().text(), GitHubWorkflowLocalize.documentationSymbolDescription().get());
        };
    }

    private static Optional<DocPayload> declaredOutputDoc(PsiElement element) {
        return keyValueAt(element)
                .filter(output -> getParent(output.getParent(), FIELD_OUTPUTS).isPresent())
                .filter(WorkflowDocumentationProvider::isDirectOutput)
                .filter(output -> getParent(output, FIELD_WITH).isEmpty())
                .map(output -> outputDoc(outputLabel(output), output.getKeyText(), output));
    }

    private static Optional<DocPayload> workflowSyntaxDoc(PsiElement element, int absoluteOffset) {
        return keyValueAt(element)
                .filter(keyValue -> isKeyOffset(keyValue, absoluteOffset))
                .flatMap(keyValue -> WorkflowSyntax.descriptionForKey(keyValue)
                        .map(description -> simpleDoc(GitHubWorkflowLocalize.documentationWorkflowsyntaxLabel().get(), keyValue.getKeyText(), description)));
    }

    private static boolean isKeyOffset(YAMLKeyValue keyValue, int absoluteOffset) {
        return ofNullable(keyValue.getKey())
                .map(PsiElement::getTextRange)
                .filter(range -> range.containsOffset(absoluteOffset))
                .isPresent();
    }

    private static boolean isDirectOutput(YAMLKeyValue output) {
        return output.getParent() != null
                && output.getParent().getParent() instanceof YAMLKeyValue parent
                && FIELD_OUTPUTS.equals(parent.getKeyText());
    }

    private static String outputLabel(YAMLKeyValue output) {
        return getParent(output, FIELD_ON).isPresent()
                ? GitHubWorkflowLocalize.documentationWorkflowoutputLabel().get()
                : GitHubWorkflowLocalize.documentationJoboutputLabel().get();
    }

    private static DocPayload actionDoc(GitHubAction action) {
        String title = action.displayName();
        StringBuilder html = new StringBuilder();
        html.append("<h3>").append(escape(title)).append("</h3>");
        html.append("<p><b>").append(action.isAction()
                ? GitHubWorkflowLocalize.documentationActionLabel().get()
                : GitHubWorkflowLocalize.documentationReusableworkflowLabel().get()).append("</b>");
        if (action.isResolved()) {
            html.append(" ").append(GitHubWorkflowLocalize.documentationResolvedfrom("<code>" + escape(action.usesValue()) + "</code>").get());
        }
        else {
            html.append(" ").append(GitHubWorkflowLocalize.documentationNotresolved().get());
        }
        html.append("</p>");
        appendParagraph(html, action.description());
        appendLink(html, action.githubUrl());
        appendMap(html, GitHubWorkflowLocalize.documentationInputsTitle().get(), action.freshInputs());
        appendMap(html, GitHubWorkflowLocalize.documentationOutputsTitle().get(), action.freshOutputs());
        appendMap(html, GitHubWorkflowLocalize.documentationSecretsTitle().get(), action.freshSecrets());
        return new DocPayload(title, html.toString(), title + "\n" + action.usesValue());
    }

    private static DocPayload yamlParameterDoc(String label, boolean input, String name, PsiElement target) {
        String details = target instanceof YAMLKeyValue keyValue
                ? WorkflowPsi.getDescription(keyValue, input)
                : "";
        return new DocPayload(
                label + " " + name,
                renderParameter(label, name, details, ""),
                plainParameter(label, name, details)
        );
    }

    private static DocPayload yamlValueDoc(String label, String name, PsiElement target) {
        String value = target instanceof YAMLKeyValue keyValue ? getText(keyValue).orElse("") : target.getText();
        String html = "<h3>" + escape(label) + " <code>" + escape(name) + "</code></h3>"
                + (value.isBlank() ? "" : "<p><b>" + escape(GitHubWorkflowLocalize.documentationValueLabel().get()) + ":</b> <code>" + escape(value) + "</code></p>");
        return new DocPayload(label + " " + name, html, label + " " + name);
    }

    private static DocPayload stepDoc(PsiElement target) {
        YAMLKeyValue id = target instanceof YAMLKeyValue keyValue ? keyValue : null;
        Optional<YAMLSequenceItem> stepItem = WorkflowPsi.getParentStep(target);
        String name = id == null ? target.getText() : getText(id).orElse(id.getKeyText());
        String title = GitHubWorkflowLocalize.documentationStepTitle(name).get();
        StringBuilder html = new StringBuilder("<h3>").append(escape(title)).append("</h3>");
        stepItem.flatMap(step -> getChild(step, "name")).flatMap(WorkflowPsi::getText).ifPresent(value -> appendDetail(html, GitHubWorkflowLocalize.documentationNameLabel().get(), value));
        stepItem.flatMap(step -> getChild(step, FIELD_USES)).flatMap(WorkflowPsi::getText).ifPresent(value -> appendDetail(html, GitHubWorkflowLocalize.documentationUsesLabel().get(), value));
        stepItem.flatMap(step -> getChild(step, FIELD_USES))
                .map(GitHubActionCache::getAction)
                .filter(action -> action != null && action.isResolved())
                .ifPresent(action -> {
                    appendDetail(html, action.isAction()
                            ? GitHubWorkflowLocalize.documentationActionLabel().get()
                            : GitHubWorkflowLocalize.documentationReusableworkflowLabel().get(), action.displayName());
                    appendParagraph(html, action.description());
                });
        stepItem.flatMap(step -> getChild(step, "run")).flatMap(WorkflowPsi::getText).ifPresent(value -> appendDetail(html, GitHubWorkflowLocalize.documentationRunLabel().get(), value));
        stepItem.ifPresent(step -> appendList(html, GitHubWorkflowLocalize.documentationOutputsTitle().get(), listStepOutputs(step).stream().map(output -> output.key()).toList()));
        return new DocPayload(title, html.toString(), title);
    }

    private static DocPayload stepOutputDoc(String outputName, PsiElement target) {
        Optional<StepOutputSource> source = stepOutputSource(target);
        StringBuilder details = new StringBuilder();
        source.flatMap(value -> value.outputDescription(outputName)).ifPresent(value -> appendLine(details, GitHubWorkflowLocalize.documentationDescriptionLabel().get(), value));
        source.ifPresent(value -> {
            appendLine(details, GitHubWorkflowLocalize.documentationStepLabel().get(), value.stepLabel());
            appendLine(details, GitHubWorkflowLocalize.documentationUsesLabel().get(), value.usesValue());
            appendLine(details, GitHubWorkflowLocalize.documentationSourceLabel().get(), value.sourceLabel());
        });
        String plainDetails = details.toString();
        String label = GitHubWorkflowLocalize.documentationStepoutputLabel().get();
        StringBuilder html = new StringBuilder(renderParameter(label, outputName, plainDetails, ""));
        source.flatMap(StepOutputSource::url).ifPresent(url -> appendLink(html, url, source.map(StepOutputSource::usesValue).orElse(url)));
        return new DocPayload(
                label + " " + outputName,
                html.toString(),
                plainParameter(label, outputName, plainDetails)
        );
    }

    private static Optional<StepOutputSource> stepOutputSource(PsiElement target) {
        Optional<YAMLSequenceItem> stepItem = WorkflowPsi.getParentStep(target);
        return stepItem.map(step -> {
            String stepId = getText(step, "id").orElse("");
            String stepName = getText(step, "name").orElse("");
            Optional<YAMLKeyValue> uses = getChild(step, FIELD_USES);
            GitHubAction action = uses.map(GitHubActionCache::getAction)
                    .filter(candidate -> candidate != null && candidate.isResolved())
                    .orElse(null);
            return new StepOutputSource(stepId, stepName, uses.flatMap(WorkflowPsi::getText).orElse(""), action);
        });
    }

    private static DocPayload outputDoc(String label, String name, PsiElement target) {
        StringBuilder details = new StringBuilder();
        keyValueAt(target).ifPresent(output -> {
            appendLine(details, GitHubWorkflowLocalize.documentationDescriptionLabel().get(), getText(output, "description").orElse(""));
            appendLine(details, GitHubWorkflowLocalize.documentationValueLabel().get(), getText(output, "value").or(() -> getText(output)).orElse(""));
            outputSourceDetails(output).ifPresent(source -> appendLine(details, GitHubWorkflowLocalize.documentationSourceLabel().get(), source));
        });
        return new DocPayload(
                label + " " + name,
                renderParameter(label, name, details.toString(), ""),
                plainParameter(label, name, details.toString())
        );
    }

    private static Optional<String> outputSourceDetails(YAMLKeyValue output) {
        return WorkflowPsi.getTextElement(output)
                .stream()
                .flatMap(text -> WorkflowReferences.resolve(text).stream())
                .filter(target -> "step-output".equals(target.kind()))
                .findFirst()
                .map(target -> target.target() instanceof YAMLKeyValue uses && FIELD_USES.equals(uses.getKeyText())
                        ? GitHubActionCache.getAction(uses)
                        : null)
                .filter(action -> action != null && action.isResolved())
                .map(action -> action.displayName() + (action.description().isBlank() ? "" : " - " + action.description()));
    }

    private static void appendLine(StringBuilder builder, String name, String value) {
        if (value != null && !value.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(name).append(": ").append(value);
        }
    }

    private static Optional<DocPayload> contextDoc(SimpleElement segment) {
        String text = segment.text();
        return switch (text) {
            case "github" -> Optional.of(simpleDoc(GitHubWorkflowLocalize.documentationContextGithub().get(), "github", GitHubWorkflowLocalize.documentationContextGithubDescription().get()));
            case "inputs" -> Optional.of(simpleDoc(GitHubWorkflowLocalize.documentationContextInputs().get(), "inputs", GitHubWorkflowLocalize.documentationContextInputsDescription().get()));
            case "secrets" -> Optional.of(simpleDoc(GitHubWorkflowLocalize.documentationContextSecrets().get(), "secrets", GitHubWorkflowLocalize.documentationContextSecretsDescription().get()));
            case "env" -> Optional.of(simpleDoc(GitHubWorkflowLocalize.documentationContextEnv().get(), "env", GitHubWorkflowLocalize.documentationContextEnvDescription().get()));
            case "matrix" -> Optional.of(simpleDoc(GitHubWorkflowLocalize.documentationContextMatrix().get(), "matrix", GitHubWorkflowLocalize.documentationContextMatrixDescription().get()));
            case "steps" -> Optional.of(simpleDoc(GitHubWorkflowLocalize.documentationContextSteps().get(), "steps", GitHubWorkflowLocalize.documentationContextStepsDescription().get()));
            case "needs" -> Optional.of(simpleDoc(GitHubWorkflowLocalize.documentationContextNeeds().get(), "needs", GitHubWorkflowLocalize.documentationContextNeedsDescription().get()));
            case "jobs" -> Optional.of(simpleDoc(GitHubWorkflowLocalize.documentationContextJobs().get(), "jobs", GitHubWorkflowLocalize.documentationContextJobsDescription().get()));
            case "outputs" -> Optional.of(simpleDoc(GitHubWorkflowLocalize.documentationContextOutputs().get(), "outputs", GitHubWorkflowLocalize.documentationContextOutputsDescription().get()));
            case "result" -> Optional.of(simpleDoc(GitHubWorkflowLocalize.documentationContextResult().get(), "result", GitHubWorkflowLocalize.documentationContextResultDescription().get()));
            case "outcome" -> Optional.of(simpleDoc(GitHubWorkflowLocalize.documentationContextOutcome().get(), "outcome", GitHubWorkflowLocalize.documentationContextOutcomeDescription().get()));
            case "conclusion" -> Optional.of(simpleDoc(GitHubWorkflowLocalize.documentationContextConclusion().get(), "conclusion", GitHubWorkflowLocalize.documentationContextConclusionDescription().get()));
            default -> Optional.empty();
        };
    }

    private static DocPayload simpleDoc(String label, String name, String description) {
        String html = "<h3>" + escape(label) + " <code>" + escape(name) + "</code></h3><p>" + escape(description) + "</p>";
        return new DocPayload(label + " " + name, html, label + " " + name + "\n" + description);
    }

    private static String renderParameter(String label, String name, String details, String url) {
        StringBuilder html = new StringBuilder("<h3>")
                .append(escape(label))
                .append(" <code>")
                .append(escape(name))
                .append("</code></h3>");
        for (String line : detailLines(details)) {
            int separator = line.indexOf(':');
            if (separator > 0) {
                appendDetail(html, line.substring(0, separator), line.substring(separator + 1).trim());
            }
            else {
                appendParagraph(html, line);
            }
        }
        appendLink(html, url);
        return html.toString();
    }

    private static String plainParameter(String label, String name, String details) {
        return label + " " + name + (details.isBlank() ? "" : "\n" + details);
    }

    private static List<String> detailLines(String details) {
        if (details == null || details.isBlank()) {
            return List.of();
        }
        return List.of(details.split("\\R"));
    }

    private static void appendMap(StringBuilder html, String title, Map<String, String> values) {
        if (values.isEmpty()) {
            return;
        }
        html.append("<h4>").append(escape(title)).append("</h4><ul>");
        values.forEach((name, details) -> html.append("<li><code>")
                .append(escape(name))
                .append("</code>")
                .append(details.isBlank() ? "" : " - " + escape(firstLine(details)))
                .append("</li>"));
        html.append("</ul>");
    }

    private static void appendList(StringBuilder html, String title, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        html.append("<h4>").append(escape(title)).append("</h4><ul>");
        values.forEach(value -> html.append("<li><code>").append(escape(value)).append("</code></li>"));
        html.append("</ul>");
    }

    private static void appendDetail(StringBuilder html, String name, String value) {
        if (!value.isBlank()) {
            html.append("<p><b>").append(escape(name)).append(":</b> ").append(escape(value)).append("</p>");
        }
    }

    private static void appendParagraph(StringBuilder html, String value) {
        if (value != null && !value.isBlank()) {
            html.append("<p>").append(escape(value)).append("</p>");
        }
    }

    private static void appendLink(StringBuilder html, String url) {
        appendLink(html, url, url);
    }

    private static void appendLink(StringBuilder html, String url, String label) {
        if (url != null && !url.isBlank()) {
            html.append("<p><a href=\"").append(escape(url)).append("\">").append(escape(label)).append("</a></p>");
        }
    }

    private static String firstLine(String text) {
        return detailLines(text).stream().findFirst().orElse("");
    }

    private static Optional<PsiElement> textElement(PsiElement element) {
        PsiElement current = element;
        while (current != null && current.getParent() != current) {
            if (WorkflowPsi.isTextElement(current) || current instanceof YAMLScalar) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static Optional<YAMLKeyValue> keyValueAt(PsiElement element) {
        PsiElement current = element;
        while (current != null && current.getParent() != current) {
            if (current instanceof YAMLKeyValue keyValue) {
                return Optional.of(keyValue);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static String escape(String value) {
        return ofNullable(value).orElse("")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    record DocPayload(String title, String html, String hint) {
    }

    private record StepOutputSource(String stepId, String stepName, String usesValue, GitHubAction action) {

        private String stepLabel() {
            if (!stepName.isBlank() && !stepId.isBlank()) {
                return stepName + " (" + stepId + ")";
            }
            if (!stepName.isBlank()) {
                return stepName;
            }
            return stepId;
        }

        private String sourceLabel() {
            if (action == null) {
                return "";
            }
            String kind = action.isAction()
                    ? GitHubWorkflowLocalize.documentationExternalactionLabel().get()
                    : GitHubWorkflowLocalize.documentationReusableworkflowLabel().get();
            String displayName = action.displayName();
            String description = action.description();
            return kind + (displayName.isBlank() ? "" : ": " + displayName)
                    + (description.isBlank() ? "" : " - " + description);
        }

        private Optional<String> outputDescription(String outputName) {
            return ofNullable(action)
                    .filter(GitHubAction::isResolved)
                    .map(GitHubAction::freshOutputs)
                    .map(outputs -> outputs.get(outputName));
        }

        private Optional<String> url() {
            return ofNullable(action)
                    .map(GitHubAction::githubUrl)
                    .filter(url -> !url.isBlank())
                    .or(() -> ofNullable(action)
                            .filter(GitHubAction::isLocal)
                            .map(GitHubAction::downloadUrl)
                            .filter(url -> !url.isBlank()));
        }
    }

    private static class WorkflowDocumentationElement extends FakePsiElement {
        private final PsiElement myDelegate;
        private final DocPayload myPayload;

        private WorkflowDocumentationElement(PsiElement delegate, DocPayload payload) {
            myDelegate = delegate;
            myPayload = payload;
        }

        private DocPayload payload() {
            return myPayload;
        }

        @Override
        public PsiElement getParent() {
            return myDelegate.getParent();
        }

        @Override
        public PsiFile getContainingFile() {
            return myDelegate.getContainingFile();
        }

        @Override
        public TextRange getTextRange() {
            return myDelegate.getTextRange();
        }

        @Override
        public int getTextOffset() {
            return myDelegate.getTextOffset();
        }

        @Override
        public PsiManager getManager() {
            return myDelegate.getManager();
        }

        @Override
        public boolean isValid() {
            return myDelegate.isValid();
        }

        @Override
        public String getName() {
            return myPayload.title();
        }

        @Override
        public String getText() {
            return myPayload.title();
        }

        @Override
        public String toString() {
            return GitHubWorkflowLocalize.documentationProvider().get();
        }
    }
}
