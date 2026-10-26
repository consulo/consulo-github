/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.model.SimpleElement;
import consulo.application.Application;
import consulo.document.util.TextRange;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.language.ast.ASTNode;
import consulo.language.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.ui.ex.keymap.util.KeymapUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.psi.YAMLAlias;
import org.jetbrains.yaml.psi.YAMLAnchor;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLScalarList;
import org.jetbrains.yaml.psi.YAMLScalarText;
import org.jetbrains.yaml.psi.YAMLSequence;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.PATTERN_GITHUB_ENV;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.PATTERN_GITHUB_ENV_MULTILINE;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.PATTERN_GITHUB_OUTPUT;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.PATTERN_GITHUB_OUTPUT_GROUP;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.PATTERN_GITHUB_OUTPUT_MULTILINE;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.PATTERN_GITHUB_OUTPUT_TEE;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.PATTERN_SHELL_OUTPUT_ASSIGNMENT;
import static java.util.Collections.unmodifiableList;
import static java.util.Optional.ofNullable;

public class WorkflowPsi {
    private WorkflowPsi() {
    }

    public static Optional<YAMLKeyValue> getParentJob(@Nullable PsiElement psiElement) {
        return getElementUnderParent(psiElement, FIELD_JOBS, YAMLKeyValue.class);
    }

    public static List<SimpleElement> parseEnvVariables(@Nullable LeafPsiElement element) {
        return element == null ? Collections.emptyList() : parseLeafVariables(element, WorkflowPsi::toGithubEnvs);
    }

    public static List<SimpleElement> parseOutputVariables(@Nullable LeafPsiElement element) {
        return element == null ? Collections.emptyList() : parseLeafVariables(element, WorkflowPsi::toGithubOutputs);
    }

    public static List<SimpleElement> parseEnvVariables(@Nullable PsiElement psiElement) {
        return psiElement == null ? Collections.emptyList() : parseVariables(psiElement, WorkflowPsi::toGithubEnvs);
    }

    public static List<SimpleElement> parseOutputVariables(@Nullable PsiElement psiElement) {
        return psiElement == null ? Collections.emptyList() : parseVariables(psiElement, WorkflowPsi::toGithubOutputs);
    }

    public static <T> Optional<T> getChild(@Nullable PsiElement psiElement, Class<T> clazz) {
        return getFirstElement(getChildren(psiElement, clazz));
    }

    public static <T> Optional<T> getFirstElement(List<T> list) {
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(0));
    }

    public static List<YAMLKeyValue> getAllJobs(@Nullable PsiElement psiElement) {
        List<YAMLKeyValue> result = new ArrayList<>();
        getAllJobs(result, ofNullable(psiElement).map(PsiElement::getContainingFile).orElse(null));
        return unmodifiableList(result);
    }

    public static void getAllJobs(@Nullable List<YAMLKeyValue> result, @Nullable PsiElement element) {
        if (result == null || element == null) {
            return;
        }
        if (element instanceof YAMLKeyValue keyValue && FIELD_JOBS.equals(keyValue.getKeyText())) {
            result.addAll(getChildren(keyValue));
        }
        else {
            Arrays.stream(element.getChildren()).forEach(child -> getAllJobs(result, child));
        }
    }

    public static List<YAMLKeyValue> getChildren(@Nullable PsiElement psiElement) {
        return getChildren(psiElement, YAMLKeyValue.class);
    }

    public static Optional<String> getText(@Nullable PsiElement psiElement) {
        return getTextElements(psiElement).stream().map(PsiElement::getText).map(WorkflowPsi::removeQuotes).filter(WorkflowPsi::hasText).findFirst();
    }

    public static Optional<String> getText(@Nullable PsiElement psiElement, String key) {
        return getChild(psiElement, key).flatMap(WorkflowPsi::getText);
    }

    public static Optional<PsiElement> getTextElement(@Nullable PsiElement psiElement) {
        List<PsiElement> textValues = getTextElements(psiElement);
        return textValues.isEmpty() ? Optional.empty() : Optional.of(textValues.get(0));
    }

    public static List<PsiElement> getTextElements(@Nullable PsiElement psiElement) {
        ArrayList<PsiElement> result = new ArrayList<>();
        getTextElements(result, psiElement);
        return unmodifiableList(result);
    }

    public static void getTextElements(List<PsiElement> result, @Nullable PsiElement psiElement) {
        ofNullable(psiElement).ifPresent(element -> {
            if (isTextElement(element)) {
                if (hasText(element.getText())) {
                    result.add(element);
                }
            }
            else {
                Arrays.stream(element.getChildren()).forEach(child -> getTextElements(result, child));
            }
        });
    }

    public static boolean isTextElement(@Nullable PsiElement element) {
        return element instanceof YAMLScalarText || isPlainText(element) || element instanceof YAMLQuotedText;
    }

    public static boolean isPlainText(@Nullable PsiElement element) {
        if (!(element instanceof YAMLScalar)) {
            return false;
        }
        ASTNode node = element.getNode();
        return node != null && node.getElementType() == YAMLElementTypes.SCALAR_PLAIN_VALUE;
    }

    public static boolean isBlockScalar(@Nullable PsiElement element) {
        return element instanceof YAMLScalarList || element instanceof YAMLScalarText || isPlainText(element);
    }

    public static List<TextRange> getContentRanges(YAMLScalar scalar) {
        String text = scalar.getText();
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (scalar instanceof YAMLQuotedText) {
            return text.length() < 2 ? List.of() : List.of(new TextRange(1, text.length() - 1));
        }
        boolean blockHeader = scalar instanceof YAMLScalarList || scalar instanceof YAMLScalarText;
        List<TextRange> lines = new ArrayList<>();
        int lineStart = 0;
        while (lineStart <= text.length()) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) {
                lineEnd = text.length();
            }
            lines.add(new TextRange(lineStart, lineEnd));
            if (lineEnd == text.length()) {
                break;
            }
            lineStart = lineEnd + 1;
        }
        if (blockHeader && !lines.isEmpty()) {
            lines = lines.subList(1, lines.size());
        }
        int indent = Integer.MAX_VALUE;
        for (TextRange line : lines) {
            String value = line.substring(text);
            if (!value.isBlank()) {
                indent = Math.min(indent, leadingSpaces(value));
            }
        }
        if (indent == Integer.MAX_VALUE) {
            indent = 0;
        }
        List<TextRange> result = new ArrayList<>();
        for (TextRange line : lines) {
            String value = line.substring(text);
            int start = line.getStartOffset() + (blockHeader ? Math.min(indent, leadingSpaces(value)) : leadingSpaces(value));
            int end = line.getEndOffset();
            if (end > start && text.charAt(end - 1) == '\r') {
                end--;
            }
            if (start > end) {
                continue;
            }
            if (!result.isEmpty() && result.get(result.size() - 1).getEndOffset() + 1 == start && blockHeader) {
                TextRange last = result.get(result.size() - 1);
                result.set(result.size() - 1, new TextRange(last.getStartOffset(), end));
            }
            else {
                result.add(new TextRange(start, end));
            }
        }
        return result;
    }

    private static int leadingSpaces(String value) {
        int result = 0;
        while (result < value.length() && value.charAt(result) == ' ') {
            result++;
        }
        return result;
    }

    public static List<YAMLKeyValue> getAllElements(@Nullable PsiElement psiElement, @Nullable String keyName) {
        return psiElement == null || keyName == null ? Collections.emptyList() : unmodifiableList(getAllElements(new ArrayList<>(), psiElement, keyName));
    }

    public static List<YAMLKeyValue> getAllElements(List<YAMLKeyValue> result, PsiElement psiElement, String keyName) {
        if (psiElement instanceof YAMLKeyValue keyValue && keyName.equals(keyValue.getKeyText())) {
            result.add(keyValue);
        }

        for (PsiElement child : psiElement.getChildren()) {
            getAllElements(result, child, keyName);
        }

        return result;
    }

    public static Optional<PsiElement> getParentStepOrJob(@Nullable PsiElement psiElement) {
        return getParentStep(psiElement).map(PsiElement.class::cast).or(() -> getParentJob(psiElement));
    }

    public static Optional<YAMLSequenceItem> getParentStep(@Nullable PsiElement psiElement) {
        return getElementUnderParent(psiElement, FIELD_STEPS, YAMLSequenceItem.class);
    }

    public static List<YAMLSequenceItem> getChildSteps(@Nullable PsiElement psiElement) {
        return ofNullable(psiElement)
            .map(element -> element instanceof YAMLKeyValue keyValue && FIELD_STEPS.equals(keyValue.getKeyText()) ? List.of(keyValue) : getAllElements(element, FIELD_STEPS))
            .map(yamlKeyValues -> yamlKeyValues.stream().flatMap(steps -> getChildren(steps, YAMLSequenceItem.class).stream().filter(Objects::nonNull)).toList())
            .orElseGet(Collections::emptyList);
    }

    public static <T> List<T> getChildren(@Nullable PsiElement psiElement, Class<T> clazz) {
        return ofNullable(psiElement)
            .map(PsiElement::getChildren)
            .map(psiElements -> Arrays.stream(psiElements).filter(clazz::isInstance).map(clazz::cast).toList())
            .filter(children -> !children.isEmpty())
            .or(() -> getAliasedChildren(psiElement, clazz))
            .or(() -> ofNullable(psiElement)
                .map(PsiElement::getChildren)
                .flatMap(psiElements -> Arrays.stream(psiElements).map(child -> getChildren(child, clazz)).filter(children -> !children.isEmpty()).findFirst())
            )
            .orElseGet(Collections::emptyList);
    }

    private static <T> Optional<List<T>> getAliasedChildren(@Nullable PsiElement psiElement, Class<T> clazz) {
        return findDirectAlias(psiElement)
            .flatMap(alias -> findAnchor(alias.getContainingFile(), alias.getAliasName()))
            .map(YAMLAnchor::getMarkedValue)
            .map(value -> getChildren(value, clazz))
            .filter(children -> !children.isEmpty());
    }

    private static Optional<YAMLAlias> findDirectAlias(@Nullable PsiElement psiElement) {
        return ofNullable(psiElement)
            .map(PsiElement::getChildren)
            .flatMap(children -> Arrays.stream(children)
                .filter(YAMLAlias.class::isInstance)
                .map(YAMLAlias.class::cast)
                .findFirst());
    }

    private static Optional<YAMLAnchor> findAnchor(@Nullable PsiElement psiElement, @Nullable String name) {
        String normalizedName = normalizeAnchorName(name);
        if (psiElement == null) {
            return Optional.empty();
        }
        if (psiElement instanceof YAMLAnchor anchor && isAnchorName(anchor, normalizedName)) {
            return Optional.of(anchor);
        }
        return Arrays.stream(psiElement.getChildren())
            .map(child -> findAnchor(child, normalizedName))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    private static boolean isAnchorName(YAMLAnchor anchor, String name) {
        return Objects.equals(name, normalizeAnchorName(anchor.getName()))
            || Objects.equals(name, normalizeAnchorName(anchor.getText()));
    }

    private static String normalizeAnchorName(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.startsWith("&") || name.startsWith("*") ? name.substring(1) : name;
    }

    public static Optional<YAMLKeyValue> getChild(@Nullable PsiElement psiElement, @Nullable String childKey) {
        return psiElement == null || childKey == null ? Optional.empty() : Optional.of(psiElement)
            .map(WorkflowPsi::getChildren)
            .flatMap(children -> children.stream()
                .filter(Objects::nonNull)
                .filter(child -> childKey.equals(child.getKeyText()))
                .findFirst()
                .or(() -> children.stream()
                    .filter(Objects::nonNull)
                    .map(child -> getChild(child, childKey))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst())
            );
    }

    public static <T extends PsiElement> Optional<T> getElementUnderParent(@Nullable PsiElement psiElement, @Nullable String keyName, Class<T> clazz) {
        return psiElement == null || keyName == null ? Optional.empty() : getParent(psiElement, yamlKeyValue -> keyName.equals(yamlKeyValue.getKeyText()))
            .flatMap(yamlKeyValue -> getClosestChild(psiElement, yamlKeyValue, clazz));
    }

    public static Optional<YAMLKeyValue> getParent(@Nullable PsiElement psiElement, @Nullable String fieldKey) {
        return psiElement == null || fieldKey == null ? Optional.empty() : getParent(psiElement, parent -> fieldKey.equals(parent.getKeyText()));
    }

    public static Optional<YAMLKeyValue> getParent(@Nullable PsiElement psiElement, @Nullable Predicate<YAMLKeyValue> filter) {
        return psiElement == null || filter == null ? Optional.empty() : Optional.of(psiElement)
            .flatMap(WorkflowPsi::toYAMLKeyValue)
            .filter(filter)
            .or(() -> Optional.ofNullable(psiElement.getParent())
                .flatMap(parent -> getParent(parent, filter))
            );
    }

    public static Optional<YAMLKeyValue> toYAMLKeyValue(@Nullable PsiElement psiElement) {
        if (psiElement instanceof YAMLKeyValue element) {
            return Optional.of(element);
        }
        return Optional.empty();
    }

    public static String getDescription(@Nullable PsiElement psiElement, boolean requiredField) {
        if (psiElement == null) {
            return "";
        }
        List<String> details = new ArrayList<>();
        getText(psiElement, "description").or(() -> getText(psiElement, "desc"))
            .ifPresent(description -> details.add(GitHubWorkflowLocalize.documentationDescription(description).get()));
        getText(psiElement, "type")
            .ifPresent(type -> details.add(GitHubWorkflowLocalize.documentationType(type).get()));
        if (requiredField) {
            details.add(GitHubWorkflowLocalize.documentationRequired(getText(psiElement, "required").map(Boolean::parseBoolean).orElse(false)).get());
        }
        getText(psiElement, "default")
            .ifPresent(defaultValue -> details.add(GitHubWorkflowLocalize.documentationDefault(defaultValue).get()));
        getText(psiElement, "deprecationMessage")
            .ifPresent(message -> details.add(GitHubWorkflowLocalize.documentationDeprecated(message).get()));
        return String.join("\n", details);
    }

    public static Optional<Path> toPath(@Nullable VirtualFile virtualFile) {
        return ofNullable(virtualFile).map(VirtualFile::getPath).flatMap(WorkflowPsi::toPath);
    }

    public static Optional<Path> toPath(@Nullable String path) {
        try {
            return ofNullable(path)
                .map(String::trim)
                .filter(WorkflowPsi::looksLikePathText)
                .map(Paths::get)
                .filter(p -> Files.exists(p) || Application.get().isUnitTestMode());
        }
        catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static boolean looksLikePathText(@Nullable String path) {
        return hasText(path) && !path.startsWith("{") && !path.matches("^<[0-9a-fA-F-]{36}>.*");
    }

    public static @Nullable Project getProject(@Nullable PsiElement psiElement) {
        return psiElement != null && psiElement.isValid() ? psiElement.getProject() : null;
    }

    public static String removeQuotes(String result) {
        return removeBrackets(result, '"', '\'');
    }

    public static boolean hasText(@Nullable String str) {
        return str != null && !str.isEmpty() && containsText(str);
    }

    public static String goToDeclarationString() {
        return GitHubWorkflowLocalize.documentationOpenDeclaration(Arrays.stream(KeymapUtil.getActiveKeymapShortcuts("GotoDeclaration").getShortcuts())
            .limit(2)
            .map(KeymapUtil::getShortcutText)
            .collect(Collectors.joining(", "))
        ).get();
    }

    private static Map<String, String> toGithubOutputs(String text) {
        Map<String, String> variables = new HashMap<>();
        if (text.contains("GITHUB_OUTPUT")) {
            putMatches(variables, PATTERN_GITHUB_OUTPUT.matcher(text), false);
            putMatches(variables, PATTERN_GITHUB_OUTPUT_TEE.matcher(text), false);
            putGroupedOutputMatches(variables, text);
            putMatches(variables, PATTERN_GITHUB_OUTPUT_MULTILINE.matcher(text), true);
        }
        return variables;
    }

    private static Map<String, String> toGithubEnvs(String text) {
        Map<String, String> variables = new HashMap<>();
        if (text.contains("GITHUB_ENV")) {
            putMatches(variables, PATTERN_GITHUB_ENV.matcher(text), false);
            putMatches(variables, PATTERN_GITHUB_ENV_MULTILINE.matcher(text), true);
        }
        return variables;
    }

    private static void putGroupedOutputMatches(Map<String, String> variables, String text) {
        Matcher matcher = PATTERN_GITHUB_OUTPUT_GROUP.matcher(text);
        while (matcher.find()) {
            putMatches(variables, PATTERN_SHELL_OUTPUT_ASSIGNMENT.matcher(matcher.group(1)), false);
        }
    }

    private static void putMatches(Map<String, String> variables, Matcher matcher, boolean multiline) {
        while (matcher.find()) {
            if (matcher.groupCount() >= 1) {
                variables.putIfAbsent(matcher.group(1), multiline ? "<multiline>" : matcher.group(2));
            }
        }
    }

    private static String removeBrackets(String text, char... chars) {
        if (text != null && text.length() > 1) {
            for (char c : chars) {
                if (text.charAt(0) == c && text.charAt(text.length() - 1) == (c == '[' ? ']' : validateRoundBracket(c))) {
                    return text.substring(1, text.length() - 1);
                }
            }
        }
        return text;
    }

    private static <T extends PsiElement> Optional<T> getClosestChild(PsiElement from, YAMLKeyValue to, Class<T> clazz) {
        return listAllParents(from, to).stream()
            .filter(Objects::nonNull)
            .filter(parent -> !(parent instanceof YAMLSequence))
            .filter(clazz::isInstance)
            .findFirst()
            .map(clazz::cast);
    }

    private static List<PsiElement> listAllParents(PsiElement from, PsiElement to) {
        List<PsiElement> result = new ArrayList<>();
        listAllParents(result, from.getParent(), to);
        Collections.reverse(result);
        return result;
    }

    private static void listAllParents(List<PsiElement> result, @Nullable PsiElement from, PsiElement to) {
        if (from != null && from != to) {
            result.add(from);
            listAllParents(result, from.getParent(), to);
        }
    }

    private static List<SimpleElement> parseLeafVariables(LeafPsiElement element, Function<String, Map<String, String>> method) {
        return method.apply(element.getText()).entrySet().stream()
            .map(env -> new SimpleElement(env.getKey(), env.getValue(), element.getTextRange()))
            .toList();
    }

    private static List<SimpleElement> parseVariables(PsiElement psiElement, Function<String, Map<String, String>> method) {
        List<SimpleElement> lineElements = getLineElements(psiElement);
        Map<String, SimpleElement> result = new LinkedHashMap<>();
        lineElements.forEach(line -> method.apply(line.text()).forEach((key, value) -> result.putIfAbsent(key, new SimpleElement(key, value, line.range()))));
        method.apply(psiElement.getText()).forEach((key, value) -> result.putIfAbsent(key, new SimpleElement(key, value, psiElement.getTextRange())));
        return new ArrayList<>(result.values());
    }

    private static List<SimpleElement> getLineElements(PsiElement psiElement) {
        return getChildren(psiElement, YAMLScalar.class).stream()
            .filter(WorkflowPsi::isBlockScalar)
            .findFirst()
            .map(psi -> {
                TextRange parentRange = psi.getTextRange();
                return getContentRanges(psi).stream().map(textRange -> new SimpleElement(
                    null,
                    removeQuotes(psi.getText().substring(textRange.getStartOffset(), textRange.getEndOffset())),
                    new TextRange(parentRange.getStartOffset() + textRange.getStartOffset(), parentRange.getStartOffset() + textRange.getEndOffset())
                )).filter(element -> element.startIndexOffset() < element.endIndexOffset()).filter(element -> hasText(element.text())).toList();
            })
            .orElseGet(Collections::emptyList);
    }

    private static boolean containsText(CharSequence str) {
        int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static char validateRoundBracket(char c) {
        return c == '(' ? ')' : c;
    }
}
