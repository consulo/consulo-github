/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.entry;

import com.github.yunabraska.githubworkflow.git.WorkflowLocation;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.model.NodeIcon;
import com.github.yunabraska.githubworkflow.model.SimpleElement;
import com.github.yunabraska.githubworkflow.state.GitHubActionCache;
import com.github.yunabraska.githubworkflow.syntax.Steps;
import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.github.yunabraska.githubworkflow.syntax.WorkflowSyntax;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.language.Language;
import consulo.language.editor.completion.CamelHumpMatcher;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLSequenceItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.currentLineKey;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.isChildOf;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.isValueCompletion;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.keyContextAt;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.pathEndsWith;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.pathMatches;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_ENV;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_JOB;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_NODE;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_OUTPUT;
import static com.github.yunabraska.githubworkflow.model.NodeIcon.ICON_RUNNER;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemOf;
import static com.github.yunabraska.githubworkflow.model.SimpleElement.completionItemsOf;
import static com.github.yunabraska.githubworkflow.syntax.Envs.listEnvs;
import static com.github.yunabraska.githubworkflow.syntax.Inputs.listInputs;
import static com.github.yunabraska.githubworkflow.syntax.JobContext.codeCompletionJob;
import static com.github.yunabraska.githubworkflow.syntax.Jobs.codeCompletionJobs;
import static com.github.yunabraska.githubworkflow.syntax.Jobs.listJobs;
import static com.github.yunabraska.githubworkflow.syntax.Matrix.listMatrix;
import static com.github.yunabraska.githubworkflow.syntax.Needs.codeCompletionNeeds;
import static com.github.yunabraska.githubworkflow.syntax.Needs.codeCompletionPreviousJobs;
import static com.github.yunabraska.githubworkflow.syntax.Needs.listJobNeeds;
import static com.github.yunabraska.githubworkflow.syntax.Secrets.listSecrets;
import static com.github.yunabraska.githubworkflow.syntax.Steps.codeCompletionSteps;
import static com.github.yunabraska.githubworkflow.syntax.Steps.listSteps;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.*;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getAllElements;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChildSteps;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParent;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStep;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getParentStepOrJob;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getProject;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.getCaretBracketItem;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.getStartIndex;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.getWorkflowFile;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.isActionFile;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.isWorkflowFile;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowYaml.toLookupElement;
import static java.util.Collections.singletonList;
import static java.util.Optional.ofNullable;

@ExtensionImpl
public class WorkflowCompletion extends CompletionContributor {

    private static final Pattern REMOTE_USES_REF_PATTERN = Pattern.compile(".*\\buses\\s*:\\s*['\"]?([^\\s'\"#]+)@([^\\s'\"]*)$");
    private static final Pattern REMOTE_USES_TARGET_PATTERN = Pattern.compile(".*\\buses\\s*:\\s*['\"]?([^\\s'\"#@]*)$");

    public WorkflowCompletion() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), completionProvider());
    }

    @Override
    public Language getLanguage() {
        return YAMLLanguage.INSTANCE;
    }

    static boolean workflowCompletionTrigger(char typeChar) {
        return switch (typeChar) {
            case '\n', ':', '.', '@' -> true;
            default -> false;
        };
    }

    private static CompletionProvider completionProvider() {
        return (parameters, processingContext, resultSet) -> completionTrigger(parameters, resultSet).ifPresent(WorkflowCompletion::complete);
    }

    private static Optional<CompletionTrigger> completionTrigger(CompletionParameters parameters, CompletionResultSet resultSet) {
        CompletionPsi completionPsi = completionPsi(parameters);
        PsiElement position = completionPsi.position();
        if (getWorkflowFile(position).isEmpty()) {
            return Optional.empty();
        }
        String[] prefix = new String[]{""};
        Optional<String[]> caretBracketItem = completionPsi.offset() < 1
                ? Optional.of(prefix)
                : getCaretBracketItem(position, completionPsi.offset(), prefix);
        return Optional.of(new CompletionTrigger(
                parameters,
                resultSet,
                completionPsi,
                position,
                caretBracketItem,
                prefix[0]
        ));
    }

    private static void complete(CompletionTrigger trigger) {
        if (completeWorkflowValue(trigger)
                || completeExpressionPath(trigger)
                || completeRunEnvironment(trigger)
                || stopInsideExecutableRun(trigger)
                || completeNeeds(trigger)
                || completeUses(trigger)
                || completeShell(trigger)
                || completeWorkflowStructure(trigger)
                || completeCallableSecrets(trigger)) {
            return;
        }
        completeCallableInputs(trigger);
    }

    private static boolean completeWorkflowValue(CompletionTrigger trigger) {
        return workflowValueCompletion(trigger.completionPsi())
                .map(completion -> addStructureCompletion(trigger, completion, getDefaultPrefix(trigger.completionPsi())))
                .orElse(false);
    }

    private static boolean completeExpressionPath(CompletionTrigger trigger) {
        return trigger.caretBracketItem()
                .map(cbi -> {
                    addWorkflowCompletionItems(trigger.resultSet(), cbi, trigger.position(), trigger.prefix());
                    return true;
                })
                .orElse(false);
    }

    private static boolean completeRunEnvironment(CompletionTrigger trigger) {
        if (!isCompletingRunEnvironmentVariable(trigger.completionPsi())) {
            return false;
        }
        addLookupElements(
                trigger.resultSet().withPrefixMatcher(trigger.prefix()),
                defaultEnvs(),
                NodeIcon.ICON_ENV,
                Character.MIN_VALUE
        );
        return true;
    }

    private static boolean stopInsideExecutableRun(CompletionTrigger trigger) {
        return isInsideExecutableRunField(trigger.position());
    }

    private static boolean completeNeeds(CompletionTrigger trigger) {
        if (!isCompletingNeedsField(trigger.parameters(), trigger.position())) {
            return false;
        }
        Optional.of(codeCompletionPreviousJobs(trigger.position()))
                .filter(cil -> !cil.isEmpty())
                .map(WorkflowCompletion::toLookupItems)
                .ifPresent(lookupElements -> addElementsWithPrefix(
                        trigger.resultSet(),
                        getDefaultPrefix(trigger.parameters()),
                        lookupElements
                ));
        return true;
    }

    private static boolean completeUses(CompletionTrigger trigger) {
        if (!isCompletingUsesField(trigger.parameters(), trigger.position())) {
            return false;
        }
        Optional<RemoteUsesRef> remoteUsesRef = remoteUsesRef(trigger.parameters());
        if (remoteUsesRef.isPresent()) {
            RemoteUsesRef ref = remoteUsesRef.get();
            addLookupElements(
                    trigger.resultSet().withPrefixMatcher(ref.prefix()),
                    knownRemoteRefs(trigger.position(), ref.usesBase()),
                    NodeIcon.ICON_NODE,
                    Character.MIN_VALUE
            );
            return true;
        }
        addLookupElements(
                trigger.resultSet().withPrefixMatcher(getDefaultPrefix(trigger.parameters())),
                callableUsesCompletions(trigger.position(), remoteUsesTargetPrefix(trigger.parameters()).orElse("")),
                NodeIcon.ICON_NODE,
                Character.MIN_VALUE
        );
        return true;
    }

    private static boolean completeShell(CompletionTrigger trigger) {
        if (!isCompletingShellField(trigger.parameters(), trigger.position())) {
            return false;
        }
        addLookupElements(
                trigger.resultSet().withPrefixMatcher(getDefaultPrefix(trigger.parameters())),
                shells(),
                NodeIcon.ICON_NODE,
                Character.MIN_VALUE
        );
        return true;
    }

    private static boolean completeWorkflowStructure(CompletionTrigger trigger) {
        return workflowStructureCompletion(trigger.completionPsi())
                .map(completion -> addStructureCompletion(trigger, completion, getDefaultPrefix(trigger.completionPsi())))
                .orElse(false);
    }

    private static boolean completeCallableSecrets(CompletionTrigger trigger) {
        if (!isCompletingCallableSecrets(trigger.position())) {
            return false;
        }
        currentCallableAction(trigger.parameters(), trigger.position())
                .map(GitHubAction::freshSecrets)
                .ifPresent(map -> addLookupElements(
                        trigger.resultSet().withPrefixMatcher(getDefaultPrefix(trigger.parameters())),
                        map,
                        NodeIcon.ICON_SECRET_WORKFLOW,
                        ':'
                ));
        return true;
    }

    private static boolean completeCallableInputs(CompletionTrigger trigger) {
        currentCallableAction(trigger.parameters(), trigger.position())
                .filter(GitHubAction::isResolved)
                .map(GitHubAction::freshInputs)
                .ifPresent(map -> addLookupElements(
                        trigger.resultSet().withPrefixMatcher(getDefaultPrefix(trigger.parameters())),
                        map,
                        NodeIcon.ICON_INPUT,
                        ':'
                ));
        return true;
    }

    private static boolean addStructureCompletion(
            CompletionTrigger trigger,
            StructureCompletion completion,
            String prefix
    ) {
        addLookupElements(
                trigger.resultSet().withPrefixMatcher(prefix),
                completion.items(),
                ICON_NODE,
                completion.suffix()
        );
        return true;
    }

    private static CompletionPsi completionPsi(CompletionParameters parameters) {
        PsiElement position = parameters.getPosition();
        InjectedLanguageManager injectionManager = InjectedLanguageManager.getInstance(position.getProject());
        PsiLanguageInjectionHost host = injectionManager.getInjectionHost(position);
        return host == null
                ? new CompletionPsi(position, parameters.getOffset())
                : new CompletionPsi(host, injectionManager.injectedToHost(position, parameters.getOffset()));
    }

    private static boolean isCompletingCallableSecrets(PsiElement position) {
        return getParent(position, FIELD_SECRETS)
                .filter(secrets -> getParent(secrets, FIELD_ON).isEmpty())
                .isPresent();
    }

    private static boolean isCompletingUsesField(CompletionParameters parameters, PsiElement position) {
        if (getParent(position, FIELD_USES).isPresent()) {
            return true;
        }
        String wholeText = parameters.getOriginalFile().getText();
        String beforeCaret = lineBeforeCaret(wholeText, parameters.getOffset());
        return beforeCaret.matches("\\s*-?\\s*" + FIELD_USES + "\\s*:\\s*.*");
    }

    private static boolean isCompletingShellField(CompletionParameters parameters, PsiElement position) {
        if (getParent(position, FIELD_SHELL).isPresent()) {
            return true;
        }
        String wholeText = parameters.getOriginalFile().getText();
        String beforeCaret = lineBeforeCaret(wholeText, parameters.getOffset());
        return beforeCaret.matches("\\s*" + FIELD_SHELL + "\\s*:\\s*.*");
    }

    private static boolean isCompletingRunEnvironmentVariable(CompletionPsi completionPsi) {
        if (!isInsideExecutableRunField(completionPsi.position())) {
            return false;
        }
        String wholeText = completionPsi.position().getContainingFile().getText();
        String beforeCaret = lineBeforeCaret(wholeText, completionPsi.offset());
        return beforeCaret.matches(".*\\$[A-Za-z0-9_]*$");
    }

    private static boolean isInsideExecutableRunField(PsiElement position) {
        return getParent(position, FIELD_RUN)
                .filter(run -> getParent(run, "defaults").isEmpty())
                .isPresent();
    }

    private static Optional<StructureCompletion> workflowStructureCompletion(CompletionPsi completionPsi) {
        Optional<WorkflowLocation.KeyContext> context = keyContextAt(completionPsi.position(), completionPsi.offset());
        if (context.isEmpty() || isValueCompletion(context.get().currentLine())) {
            return Optional.empty();
        }
        List<String> path = context.get().path();
        Optional<Map<String, String>> keys = WorkflowSyntax.completionKeysForPath(path);
        if (keys.isPresent()) {
            return Optional.of(new StructureCompletion(keys.get(), ':'));
        }
        if (pathMatches(path, FIELD_ON, "*", "types")) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.eventActivityTypesFor(path.get(1)), Character.MIN_VALUE));
        }
        if (pathMatches(path, FIELD_ON, "*", "*")) {
            return workflowEventFilterValueCompletion(completionPsi, context.get());
        }
        return Optional.empty();
    }

    private static Optional<StructureCompletion> workflowValueCompletion(CompletionPsi completionPsi) {
        Optional<WorkflowLocation.KeyContext> context = keyContextAt(completionPsi.position(), completionPsi.offset());
        if (context.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> key = currentLineKey(context.get().currentLine());
        if (key.isEmpty()) {
            return Optional.empty();
        }
        if (context.get().currentLine().contains("${{")) {
            return Optional.empty();
        }
        List<String> path = context.get().path();
        String currentKey = key.get();
        Optional<StructureCompletion> eventFilterValueCompletion = workflowEventFilterValueCompletion(completionPsi, context.get());
        if (eventFilterValueCompletion.isPresent()) {
            return eventFilterValueCompletion;
        }
        if ("runs-on".equals(currentKey)) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.runnerLabels(), Character.MIN_VALUE));
        }
        if ("permissions".equals(currentKey)) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.permissionShorthandValues(), Character.MIN_VALUE));
        }
        if ("types".equals(currentKey) && pathMatches(path, FIELD_ON, "*")) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.eventActivityTypesFor(path.get(1)), Character.MIN_VALUE));
        }
        if ("type".equals(currentKey)
                && (isChildOf(path, FIELD_ON, "workflow_dispatch", FIELD_INPUTS)
                || isChildOf(path, FIELD_ON, "workflow_call", FIELD_INPUTS))) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.workflowInputTypesFor(path.get(1)), Character.MIN_VALUE));
        }
        if (pathEndsWith(path, "permissions")) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.permissionValuesFor(currentKey), Character.MIN_VALUE));
        }
        if ("required".equals(currentKey)
                || "continue-on-error".equals(currentKey)
                || "fail-fast".equals(currentKey)
                || "cancel-in-progress".equals(currentKey)) {
            return Optional.of(new StructureCompletion(WorkflowSyntax.booleanValues(), Character.MIN_VALUE));
        }
        return Optional.empty();
    }

    private static Optional<StructureCompletion> workflowEventFilterValueCompletion(
            CompletionPsi completionPsi,
            WorkflowLocation.KeyContext context
    ) {
        return eventFilterContext(context)
                .map(eventFilter -> eventFilterValueCompletions(completionPsi.position(), eventFilter.event(), eventFilter.filter()))
                .filter(values -> !values.isEmpty())
                .map(values -> new StructureCompletion(values, Character.MIN_VALUE));
    }

    private static Optional<EventFilterContext> eventFilterContext(WorkflowLocation.KeyContext context) {
        List<String> path = context.path();
        Optional<String> currentKey = currentLineKey(context.currentLine());
        if (currentKey.isPresent() && pathMatches(path, FIELD_ON, "*")) {
            String event = path.get(1);
            String filter = currentKey.get();
            return WorkflowSyntax.eventFilterKeysFor(event).containsKey(filter)
                    ? Optional.of(new EventFilterContext(event, filter))
                    : Optional.empty();
        }
        if (pathMatches(path, FIELD_ON, "*", "*")) {
            String event = path.get(1);
            String filter = path.get(2);
            return WorkflowSyntax.eventFilterKeysFor(event).containsKey(filter)
                    ? Optional.of(new EventFilterContext(event, filter))
                    : Optional.empty();
        }
        return Optional.empty();
    }

    private static Map<String, String> eventFilterValueCompletions(
            PsiElement position,
            String event,
            String filter
    ) {
        return switch (filter) {
            case "types" -> WorkflowSyntax.eventActivityTypesFor(event);
            case "branches", "branches-ignore" -> localGitRefs(position, "heads", GitHubWorkflowLocalize.completionWorkflowEventfilterBranches());
            case "tags", "tags-ignore" -> localGitRefs(position, "tags", GitHubWorkflowLocalize.completionWorkflowEventfilterTags());
            case "paths", "paths-ignore" -> localProjectPaths(position);
            case "cron" -> WorkflowSyntax.cronValues();
            default -> Collections.emptyMap();
        };
    }

    private static Optional<RemoteUsesRef> remoteUsesRef(CompletionParameters parameters) {
        String wholeText = parameters.getOriginalFile().getText();
        String beforeCaret = lineBeforeCaret(wholeText, parameters.getOffset());
        Matcher matcher = REMOTE_USES_REF_PATTERN.matcher(beforeCaret);
        return matcher.matches()
                ? Optional.of(new RemoteUsesRef(matcher.group(1), matcher.group(2)))
                : Optional.empty();
    }

    private static Optional<String> remoteUsesTargetPrefix(CompletionParameters parameters) {
        String wholeText = parameters.getOriginalFile().getText();
        String beforeCaret = lineBeforeCaret(wholeText, parameters.getOffset());
        Matcher matcher = REMOTE_USES_TARGET_PATTERN.matcher(beforeCaret);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static Map<String, String> callableUsesCompletions(PsiElement position, String usesPrefix) {
        Map<String, String> result = new LinkedHashMap<>(localUsesCompletions(position));
        knownRemoteUses(position).forEach((usesBase, description) -> result.putIfAbsent(usesBase, description));
        GitHubActionCache.getActionCache().searchUses(usesPrefix, 10).forEach(result::putIfAbsent);
        return result;
    }

    private static Map<String, String> localUsesCompletions(PsiElement position) {
        Project project = getProject(position);
        if (project == null) {
            return Collections.emptyMap();
        }
        boolean reusableWorkflowUse = isReusableWorkflowUse(position);
        VirtualFile currentFile = position.getContainingFile().getVirtualFile();
        Map<String, String> result = new LinkedHashMap<>();
        ProjectFileIndex.getInstance(project).iterateContent(file -> {
            toLocalUsesValue(project, currentFile, file, reusableWorkflowUse)
                    .ifPresent(value -> result.putIfAbsent(value, (reusableWorkflowUse
                            ? GitHubWorkflowLocalize.completionUsesLocalWorkflow()
                            : GitHubWorkflowLocalize.completionUsesLocalAction()).get()));
            return true;
        });
        return result;
    }

    private static Map<String, String> localProjectPaths(PsiElement position) {
        Project project = getProject(position);
        if (project == null) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        ProjectFileIndex.getInstance(project).iterateContent(file -> {
            if (!file.isDirectory() && !file.getPath().contains("/.git/")) {
                VirtualFile contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file);
                if (contentRoot != null) {
                    String path = Path.of(contentRoot.getPath()).relativize(Path.of(file.getPath())).toString().replace('\\', '/');
                    if (!path.isBlank()) {
                        result.putIfAbsent(path, GitHubWorkflowLocalize.completionWorkflowEventfilterPaths().get());
                    }
                }
            }
            return result.size() < 200;
        });
        return result;
    }

    private static Map<String, String> localGitRefs(PsiElement position, String namespace, LocalizeValue description) {
        Map<String, String> result = new LinkedHashMap<>();
        repositoryRoot(position)
                .flatMap(WorkflowLocation.RepositoryResolver::gitDir)
                .ifPresent(gitDir -> {
                    readLooseRefs(gitDir.resolve("refs").resolve(namespace), result, description);
                    readPackedRefs(gitDir.resolve("packed-refs"), "refs/" + namespace + "/", result, description);
                });
        return result;
    }

    private static void readLooseRefs(Path refRoot, Map<String, String> result, LocalizeValue description) {
        if (!Files.isDirectory(refRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(refRoot)) {
            paths.filter(Files::isRegularFile)
                    .map(refRoot::relativize)
                    .map(Path::toString)
                    .map(value -> value.replace('\\', '/'))
                    .filter(value -> !value.isBlank())
                    .sorted()
                    .forEach(ref -> result.putIfAbsent(ref, description.get()));
        }
        catch (IOException ignored) {
        }
    }

    private static void readPackedRefs(Path packedRefs, String prefix, Map<String, String> result, LocalizeValue description) {
        if (!Files.isRegularFile(packedRefs)) {
            return;
        }
        try (Stream<String> lines = Files.lines(packedRefs)) {
            lines.map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#") && !line.startsWith("^"))
                    .map(line -> line.split("\\s+", 2))
                    .filter(parts -> parts.length == 2 && parts[1].startsWith(prefix))
                    .map(parts -> parts[1].substring(prefix.length()))
                    .filter(value -> !value.isBlank())
                    .forEach(ref -> result.putIfAbsent(ref, description.get()));
        }
        catch (IOException ignored) {
        }
    }

    private static Optional<Path> repositoryRoot(PsiElement position) {
        Path current = ofNullable(position)
                .map(PsiElement::getContainingFile)
                .map(file -> file.getVirtualFile())
                .map(VirtualFile::getPath)
                .map(Path::of)
                .map(Path::getParent)
                .orElse(null);
        while (current != null) {
            if (Files.isDirectory(current.resolve(".git")) || Files.isRegularFile(current.resolve(".git"))) {
                return Optional.of(current);
            }
            current = current.getParent();
        }
        return ofNullable(getProject(position))
                .map(Project::getBasePath)
                .map(Path::of)
                .filter(path -> Files.isDirectory(path.resolve(".git")) || Files.isRegularFile(path.resolve(".git")));
    }

    private static Map<String, String> knownRemoteRefs(PsiElement position, String usesBase) {
        Map<String, String> result = new LinkedHashMap<>();
        knownRemoteActions(position).stream()
                .filter(action -> splitRemoteUses(action.usesValue()).map(uses -> usesBase.equals(uses.base())).orElse(false))
                .flatMap(action -> action.remoteRefs().stream())
                .forEach(ref -> result.putIfAbsent(ref, GitHubWorkflowLocalize.completionUsesRefKnown().get()));
        knownRemoteUsesValues(position).stream()
                .map(WorkflowCompletion::splitRemoteUses)
                .flatMap(Optional::stream)
                .filter(uses -> usesBase.equals(uses.base()))
                .forEach(uses -> result.putIfAbsent(uses.ref(), GitHubWorkflowLocalize.completionUsesRefKnown().get()));
        GitHubActionCache.getActionCache().remoteRefsFor(usesBase, 10)
                .forEach(ref -> result.putIfAbsent(ref, GitHubWorkflowLocalize.completionUsesRefRemote().get()));
        return result;
    }

    private static Map<String, String> knownRemoteUses(PsiElement position) {
        Map<String, String> result = new LinkedHashMap<>();
        knownRemoteUsesValues(position).stream()
                .map(WorkflowCompletion::splitRemoteUses)
                .flatMap(Optional::stream)
                .forEach(uses -> result.putIfAbsent(uses.base(), GitHubWorkflowLocalize.completionUsesRemoteKnown().get()));
        return result;
    }

    private static List<String> knownRemoteUsesValues(PsiElement position) {
        return Stream.concat(
                        knownRemoteActions(position).stream().map(GitHubAction::usesValue),
                        getAllElements(position.getContainingFile(), FIELD_USES).stream()
                                .map(WorkflowPsi::getText)
                                .flatMap(Optional::stream)
                )
                .filter(uses -> uses.contains("@") && !uses.startsWith("."))
                .distinct()
                .toList();
    }

    private static List<GitHubAction> knownRemoteActions(PsiElement position) {
        return GitHubActionCache.getActionCache().knownActions().stream()
                .filter(action -> action.usesValue().contains("@") && !action.usesValue().startsWith("."))
                .distinct()
                .toList();
    }

    private static Optional<RemoteUses> splitRemoteUses(String usesValue) {
        int refSeparator = usesValue.lastIndexOf('@');
        if (refSeparator < 1 || refSeparator == usesValue.length() - 1) {
            return Optional.empty();
        }
        return Optional.of(new RemoteUses(
                usesValue.substring(0, refSeparator),
                usesValue.substring(refSeparator + 1)
        ));
    }

    private static boolean isReusableWorkflowUse(PsiElement position) {
        return getParentStep(position).isEmpty() && currentJob(position).isPresent();
    }

    private static Optional<String> toLocalUsesValue(
            Project project,
            VirtualFile currentFile,
            VirtualFile file,
            boolean reusableWorkflowUse
    ) {
        if (file == null || file.isDirectory() || file.equals(currentFile)) {
            return Optional.empty();
        }
        VirtualFile contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file);
        if (contentRoot == null) {
            return Optional.empty();
        }
        Path basePath = Path.of(contentRoot.getPath());
        Path path = Path.of(file.getPath());
        if (reusableWorkflowUse && isWorkflowFile(path)) {
            return Optional.of("./" + basePath.relativize(path));
        }
        if (!reusableWorkflowUse && isActionFile(path)) {
            Path actionDirectory = path.getParent();
            Path relative = basePath.relativize(actionDirectory);
            return Optional.of(relative.toString().isEmpty() ? "./" : "./" + relative);
        }
        return Optional.empty();
    }

    private static Optional<GitHubAction> currentCallableAction(CompletionParameters parameters, PsiElement position) {
        return currentCallable(position)
                .map(GitHubActionCache::getAction)
                .filter(GitHubAction::isResolved)
                .or(() -> nearestPreviousUsesValue(parameters)
                        .map(usesValue -> GitHubActionCache.getActionCache().get(getProject(position), usesValue)));
    }

    private static Optional<YAMLKeyValue> currentCallable(PsiElement position) {
        return getParent(position, FIELD_WITH)
                .flatMap(with -> getParentStepOrJob(with)
                        .flatMap(callable -> WorkflowPsi.getChild(callable, FIELD_USES))
                )
                .or(() -> currentStep(position).flatMap(callable -> WorkflowPsi.getChild(callable, FIELD_USES)))
                .or(() -> currentJob(position).flatMap(callable -> WorkflowPsi.getChild(callable, FIELD_USES)))
                .or(() -> currentStepOrJob(position).flatMap(callable -> WorkflowPsi.getChild(callable, FIELD_USES)));
    }

    private static Optional<String> nearestPreviousUsesValue(CompletionParameters parameters) {
        String wholeText = parameters.getOriginalFile().getText();
        int offset = WorkflowLocation.boundedOffset(wholeText, parameters.getOffset());
        int lineStart = WorkflowLocation.currentLineStart(wholeText, offset);
        String beforeCaret = wholeText.substring(0, Math.min(lineStart, wholeText.length()));
        String[] lines = beforeCaret.split("\\R");
        for (int index = lines.length - 1; index >= 0; index--) {
            String line = lines[index];
            String trimmed = line.trim();
            if (trimmed.startsWith(FIELD_USES + ":")) {
                return Optional.of(trimmed.substring((FIELD_USES + ":").length()).trim())
                        .map(WorkflowPsi::removeQuotes)
                        .filter(WorkflowPsi::hasText);
            }
        }
        return Optional.empty();
    }

    private static void addWorkflowCompletionItems(CompletionResultSet resultSet, String[] cbi, PsiElement position, String prefix) {
        Map<Integer, List<SimpleElement>> completionResultMap = new HashMap<>();
        for (int i = 0; i < cbi.length; i++) {
            List<SimpleElement> previousCompletions = ofNullable(completionResultMap.getOrDefault(i - 1, null)).orElseGet(ArrayList::new);
            int index = i;
            if (i != 0 && !previousCompletions.isEmpty() && previousCompletions.stream().noneMatch(item -> item.key().equals(cbi[index]))) {
                return;
            }
            else {
                addCompletionItems(cbi, i, position, completionResultMap);
            }
        }
        ofNullable(completionResultMap.getOrDefault(cbi.length - 1, null))
                .map(WorkflowCompletion::toLookupItems)
                .ifPresent(lookupElements -> addElementsWithPrefix(resultSet, prefix, lookupElements));
    }

    private static void addElementsWithPrefix(CompletionResultSet resultSet, String prefix, List<LookupElement> lookupElements) {
        resultSet.withPrefixMatcher(new CamelHumpMatcher(prefix)).addAllElements(lookupElements);
    }

    private static void addCompletionItems(String[] cbi, int i, PsiElement position, Map<Integer, List<SimpleElement>> completionItemMap) {
        if (i == 0) {
            handleFirstItem(cbi, i, position, completionItemMap);
        }
        else if (i == 1) {
            handleSecondItem(cbi, i, position, completionItemMap);
        }
        else if (i == 2) {
            handleThirdItem(cbi, i, position, completionItemMap);
        }
        else if (i == 3) {
            handleFourthItem(cbi, i, position, completionItemMap);
        }
    }

    private static boolean isCompletingNeedsField(CompletionParameters parameters, PsiElement position) {
        if (getParent(position, FIELD_NEEDS).isPresent()) {
            return true;
        }
        String wholeText = parameters.getOriginalFile().getText();
        String beforeCaret = lineBeforeCaret(wholeText, parameters.getOffset());
        return beforeCaret.matches("\\s*" + FIELD_NEEDS + "\\s*:\\s*.*");
    }

    private static Optional<PsiElement> currentStepOrJob(PsiElement position) {
        return getParentStepOrJob(position)
                .or(() -> currentStep(position).map(PsiElement.class::cast))
                .or(() -> currentJob(position).map(PsiElement.class::cast));
    }

    private static Optional<YAMLSequenceItem> currentStep(PsiElement position) {
        return ofNullable(position)
                .map(PsiElement::getContainingFile)
                .map(file -> currentOrPrevious(position, getAllElements(file, FIELD_STEPS).stream().flatMap(steps -> getChildSteps(steps).stream()))
                        .findFirst()
                        .orElse(null));
    }

    private static Optional<YAMLKeyValue> currentJob(PsiElement position) {
        return ofNullable(position)
                .map(PsiElement::getContainingFile)
                .map(file -> currentOrPrevious(position, getAllElements(file, FIELD_JOBS).stream().flatMap(jobs -> WorkflowPsi.getChildren(jobs, YAMLKeyValue.class).stream()))
                        .findFirst()
                        .orElse(null));
    }

    private static <T extends PsiElement> Stream<T> currentOrPrevious(PsiElement position, Stream<T> candidates) {
        int offset = ofNullable(position.getTextRange()).map(TextRange::getStartOffset).orElse(-1);
        return candidates
                .filter(candidate -> containsOffset(candidate, position) || candidate.getTextRange().getStartOffset() <= offset)
                .reduce((previous, current) -> containsOffset(previous, position) ? previous : current)
                .stream();
    }

    private static boolean containsOffset(PsiElement candidate, PsiElement position) {
        TextRange candidateRange = candidate.getTextRange();
        TextRange positionRange = position.getTextRange();
        return candidateRange != null && positionRange != null && candidateRange.contains(positionRange.getStartOffset());
    }

    private static void handleThirdItem(String[] cbi, int i, PsiElement position, Map<Integer, List<SimpleElement>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_JOBS -> completionItemMap.put(i, codeCompletionJobs(cbi[1], position));
            case FIELD_NEEDS -> completionItemMap.put(i, codeCompletionNeeds(cbi[1], position));
            case FIELD_STEPS -> completionItemMap.put(i, Steps.codeCompletionSteps(cbi[1], position));
            case FIELD_JOB -> completionItemMap.put(i, codeCompletionJob(cbi[1], cbi[2], position));
            default -> {
            }
        }
    }

    private static void handleFourthItem(String[] cbi, int i, PsiElement position, Map<Integer, List<SimpleElement>> completionItemMap) {
        if (FIELD_JOB.equals(cbi[0])) {
            completionItemMap.put(i, codeCompletionJob(cbi[1], cbi[2], cbi[3], position));
        }
        else if (FIELD_NEEDS.equals(cbi[0]) && FIELD_OUTPUTS.equals(cbi[2])) {
            completionItemMap.put(i, codeCompletionNeeds(cbi[1], position));
        }
        else if (FIELD_JOBS.equals(cbi[0]) && FIELD_OUTPUTS.equals(cbi[2])) {
            completionItemMap.put(i, codeCompletionJobs(cbi[1], position));
        }
    }

    private static void handleSecondItem(String[] cbi, int i, PsiElement position, Map<Integer, List<SimpleElement>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_STEPS -> completionItemMap.put(i, List.of(
                    completionItemOf(FIELD_OUTPUTS, GitHubWorkflowLocalize.completionStepsOutputs().get(), ICON_OUTPUT),
                    completionItemOf(FIELD_CONCLUSION, GitHubWorkflowLocalize.completionStepsConclusion().get(), ICON_OUTPUT),
                    completionItemOf(FIELD_OUTCOME, GitHubWorkflowLocalize.completionStepsOutcome().get(), ICON_OUTPUT)
            ));
            case FIELD_JOBS, FIELD_NEEDS -> completionItemMap.put(i, List.of(
                    completionItemOf(FIELD_OUTPUTS, GitHubWorkflowLocalize.completionJobsOutputs().get(), ICON_OUTPUT),
                    completionItemOf(FIELD_RESULT, GitHubWorkflowLocalize.completionJobsResult().get(), ICON_OUTPUT)
            ));
            case FIELD_JOB -> completionItemMap.put(i, codeCompletionJob(cbi[1], position));
            default -> {
            }
        }
    }

    private static void handleFirstItem(String[] cbi, int i, PsiElement position, Map<Integer, List<SimpleElement>> completionItemMap) {
        switch (cbi[0]) {
            case FIELD_STEPS -> completionItemMap.put(i, codeCompletionSteps(position));
            case FIELD_JOBS -> completionItemMap.put(i, codeCompletionJobs(position));
            case FIELD_ENVS -> completionItemMap.put(i, listEnvs(position));
            case FIELD_GITHUB -> completionItemMap.put(i, codeCompletionContext(FIELD_GITHUB, ICON_ENV));
            case FIELD_JOB -> completionItemMap.put(i, codeCompletionJob());
            case FIELD_MATRIX -> completionItemMap.put(i, listMatrix(position));
            case FIELD_RUNNER -> completionItemMap.put(i, codeCompletionContext(FIELD_RUNNER, ICON_RUNNER));
            case FIELD_STRATEGY -> completionItemMap.put(i, codeCompletionContext(FIELD_STRATEGY, ICON_NODE));
            case FIELD_INPUTS -> completionItemMap.put(i, listInputs(position));
            case FIELD_SECRETS -> completionItemMap.put(i, listSecrets(position));
            case FIELD_NEEDS -> completionItemMap.put(i, codeCompletionNeeds(position));
            default -> {
                boolean isOnOutput = getParent(position, FIELD_OUTPUTS).flatMap(outputs -> getParent(position, FIELD_ON)).isPresent();
                if (isOnOutput) {
                    completionItemMap.put(i, singletonList(completionItemOf(FIELD_JOBS, DEFAULT_VALUE_MAP.get(FIELD_DEFAULT).get().get(FIELD_JOBS), ICON_JOB)));
                }
                else if (getParent(position, "runs-on").isEmpty() && getParent(position, "os").isEmpty()) {
                    addDefaultWorkflowCompletionItems(i, position, completionItemMap);
                }
            }
        }
    }

    private static List<SimpleElement> codeCompletionContext(String field, NodeIcon icon) {
        return completionItemsOf(DEFAULT_VALUE_MAP.get(field).get(), icon);
    }

    private static void addDefaultWorkflowCompletionItems(int i, PsiElement position, Map<Integer, List<SimpleElement>> completionItemMap) {
        ofNullable(expressionRoots())
                .map(map -> {
                    Map<String, String> copyMap = new HashMap<>(map);
                    Optional.of(listInputs(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_INPUTS));
                    Optional.of(listSecrets(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_SECRETS));
                    Optional.of(listJobs(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_JOBS));
                    Optional.of(listMatrix(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_MATRIX));
                    Optional.of(listSteps(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_STEPS));
                    Optional.of(listJobNeeds(position)).filter(List::isEmpty).ifPresent(empty -> copyMap.remove(FIELD_NEEDS));
                    return copyMap;
                })
                .map(map -> completionItemsOf(map, ICON_NODE))
                .ifPresent(items -> completionItemMap.put(i, items));
    }

    private static String getDefaultPrefix(CompletionParameters parameters) {
        String wholeText = parameters.getOriginalFile().getText();
        int caretOffset = parameters.getOffset();
        return getDefaultPrefix(wholeText, caretOffset);
    }

    private static String getDefaultPrefix(CompletionPsi completionPsi) {
        String wholeText = completionPsi.position().getContainingFile().getText();
        return getDefaultPrefix(wholeText, completionPsi.offset());
    }

    private static String getDefaultPrefix(String wholeText, int caretOffset) {
        int offset = WorkflowLocation.boundedOffset(wholeText, caretOffset);
        if (offset < 1) {
            return "";
        }
        int indexStart = getStartIndex(wholeText, offset - 1);
        if (indexStart < 0 || indexStart > offset) {
            return "";
        }
        return wholeText.substring(indexStart, offset)
                .replace("IntellijIdeaRulezzz", "")
                .replaceFirst("^[\\s\\[,'\"]+", "")
                .trim();
    }

    public static String lineBeforeCaret(String wholeText, int rawOffset) {
        return WorkflowLocation.lineBeforeCaret(wholeText, rawOffset);
    }

    private static void addLookupElements(CompletionResultSet resultSet, Map<String, String> map, NodeIcon icon, char suffix) {
        if (!map.isEmpty()) {
            resultSet.addAllElements(toLookupElements(map, icon, suffix));
        }
    }

    private static List<LookupElement> toLookupElements(Map<String, String> map, NodeIcon icon, char suffix) {
        return map.entrySet().stream().map(item -> toLookupElement(icon, suffix, item.getKey(), item.getValue())).toList();
    }

    private static List<LookupElement> toLookupItems(List<SimpleElement> items) {
        return items.stream().map(SimpleElement::toLookupElement).toList();
    }

    private record RemoteUses(String base, String ref) {
    }

    private record RemoteUsesRef(String usesBase, String prefix) {
    }

    private record StructureCompletion(Map<String, String> items, char suffix) {
    }

    private record EventFilterContext(String event, String filter) {
    }

    private record CompletionPsi(PsiElement position, int offset) {
    }

    private record CompletionTrigger(
            CompletionParameters parameters,
            CompletionResultSet resultSet,
            CompletionPsi completionPsi,
            PsiElement position,
            Optional<String[]> caretBracketItem,
            String prefix
    ) {
    }
}
