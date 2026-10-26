/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.state;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.github.yunabraska.githubworkflow.model.GitHubAction;
import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import com.github.yunabraska.githubworkflow.syntax.WorkflowYaml;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressBuilderFactory;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.fileEditor.FileEditorManager;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.event.PsiTreeChangeAdapter;
import consulo.language.psi.event.PsiTreeChangeEvent;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.concurrent.coroutine.step.CodeExecution;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Singleton;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.model.GitHubAction.createGithubAction;
import static com.github.yunabraska.githubworkflow.model.GitHubAction.findActionYaml;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.CACHE_ONE_DAY;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_USES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getProject;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.toPath;
import static java.util.Optional.ofNullable;

@Singleton
@State(name = "GitHubActionCache", storages = @Storage("githubActionCache.xml"))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class GitHubActionCache implements PersistentStateComponent<GitHubActionCache.CacheState> {
    private static final Logger LOG = Logger.getInstance(GitHubActionCache.class);
    private static final String DEFAULT_REMOTE_REF = "main";
    private static final String EXPORT_HEADER = "github-workflow-cache-v1";
    private static final String REFS_PREFIX = "refs:";

    public static class ActionState {
        public Map<String, String> metaData = new LinkedHashMap<>();
        public Map<String, String> inputs = new LinkedHashMap<>();
        public Map<String, String> outputs = new LinkedHashMap<>();
        public Map<String, String> secrets = new LinkedHashMap<>();
    }

    public static class CacheState {
        public Map<String, ActionState> actions = new LinkedHashMap<>();
    }

    private final Map<String, GitHubAction> myActions = new ConcurrentHashMap<>();
    private final Set<String> myInFlightResolutions = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<String, String>> mySearchResults = new ConcurrentHashMap<>();
    private final Set<String> myInFlightSearches = ConcurrentHashMap.newKeySet();
    private final AtomicReference<ActionResolver> myActionResolver = new AtomicReference<>(GitHubAction::resolve);

    /**
     * Strategy used by cache refresh operations to resolve action metadata.
     */
    @FunctionalInterface
    public interface ActionResolver {
        /**
         * Resolves remote or local action metadata for cache refresh operations.
         *
         * @param action action metadata object to resolve
         * @return the resolved action object, usually the same instance
         */
        GitHubAction resolve(GitHubAction action);
    }

    public static GitHubActionCache getActionCache() {
        return Application.get().getInstance(GitHubActionCache.class);
    }

    @Override
    public CacheState getState() {
        CacheState result = new CacheState();
        new LinkedHashMap<>(myActions).forEach((key, action) -> {
            ActionState actionState = new ActionState();
            actionState.metaData.putAll(action.getMetaData());
            actionState.inputs.putAll(action.getInputs());
            actionState.outputs.putAll(action.getOutputs());
            actionState.secrets.putAll(action.getSecrets());
            result.actions.put(key, actionState);
        });
        return result;
    }

    @Override
    public void loadState(CacheState state) {
        myActions.clear();
        ofNullable(state.actions).ifPresent(actions -> actions.forEach((key, actionState) -> {
            if (key != null && actionState != null) {
                myActions.put(key, new GitHubAction()
                    .setMetaData(actionState.metaData)
                    .setInputs(actionState.inputs)
                    .setOutputs(actionState.outputs)
                    .setSecrets(actionState.secrets));
            }
        }));
    }

    public Collection<GitHubAction> knownActions() {
        return List.copyOf(myActions.values());
    }

    public void cleanUp() {
        long currentTime = System.currentTimeMillis();
        new HashMap<>(myActions).forEach((key, action) -> {
            if (currentTime > action.expiryTime()) {
                if (action.isSuppressed() || !action.ignoredInputs().isEmpty() || !action.ignoredOutputs().isEmpty()) {
                    saveNewAction(ProjectManager.getInstance().getDefaultProject(), action);
                }
                else {
                    myActions.remove(key);
                }
            }
        });
    }

    public @Nullable GitHubAction get(@Nullable Project project, String usesValue) {
        String usesCleaned = usesValue.replace("IntellijIdeaRulezzz", "");
        boolean isLocal = isLocalUses(usesCleaned);
        String normalizedUses = normalizeUsesValue(usesCleaned, isLocal);
        String path = getAbsolutePath(isLocal, normalizedUses, project);
        GitHubAction cached = ofNullable(myActions.get(path))
            .or(() -> ofNullable(myActions.get(normalizedUses)))
            .or(() -> ofNullable(myActions.get(usesCleaned)))
            .orElse(null);
        if (cached != null) {
            return cachedOrRefresh(normalizedUses, path, isLocal, cached);
        }
        return createdOrQueuedRemote(normalizedUses, path, isLocal, null);
    }

    private @Nullable GitHubAction cachedOrRefresh(String usesValue, String path, boolean isLocal, GitHubAction action) {
        if (!isLocal && !action.isResolved()) {
            return queueRefresh(action);
        }
        if (System.currentTimeMillis() < action.expiryTime()) {
            return action;
        }
        if (action.isResolved() && !isLocal) {
            queueRefresh(action);
            return action;
        }
        return createdOrQueuedRemote(usesValue, path, isLocal, action);
    }

    private GitHubAction queueRefresh(GitHubAction action) {
        if (canResolve(action)) {
            resolveInBackground(List.of(action));
        }
        return action;
    }

    private static boolean canResolve(GitHubAction action) {
        return action.isLocal() || RemoteActionProviders.hasAccountFor(action.usesValue());
    }

    public @Nullable String remove(@Nullable String usesValue) {
        ofNullable(usesValue).ifPresent(myActions::remove);
        return usesValue;
    }

    public CacheSummary summary() {
        List<GitHubAction> actions = myActions.values().stream().distinct().toList();
        long resolved = actions.stream().filter(GitHubAction::isResolved).count();
        long remote = actions.stream().filter(action -> !action.isLocal()).count();
        long expired = actions.stream().filter(action -> System.currentTimeMillis() >= action.expiryTime()).count();
        long suppressed = actions.stream().filter(GitHubAction::hasSuppressedWarnings).count();
        return new CacheSummary(actions.size(), resolved, remote, expired, suppressed);
    }

    public List<CacheEntry> entries() {
        long now = System.currentTimeMillis();
        return myActions.entrySet().stream()
            .map(entry -> CacheEntry.from(entry.getKey(), entry.getValue(), now))
            .sorted((left, right) -> left.key().compareToIgnoreCase(right.key()))
            .toList();
    }

    public CacheSummary removeAll(@Nullable Collection<String> keys) {
        ofNullable(keys).stream()
            .flatMap(Collection::stream)
            .filter(WorkflowPsi::hasText)
            .forEach(myActions::remove);
        triggerSyntaxHighlightingForActiveFiles();
        return summary();
    }

    public long estimatedSizeBytes() {
        return myActions.entrySet().stream()
            .mapToLong(entry -> estimate(entry.getKey()) + estimate(entry.getValue()))
            .sum();
    }

    /**
     * Exports cache metadata to a portable line-based file.
     *
     * @param output target file path
     * @return current cache summary after export
     * @throws IOException when the file cannot be written
     */
    public CacheSummary exportCache(Path output) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write(EXPORT_HEADER);
            writer.newLine();
            for (Map.Entry<String, GitHubAction> entry : new LinkedHashMap<>(myActions).entrySet()) {
                writer.write(encode(entry.getKey()));
                writer.write('\t');
                writer.write(encode(entry.getValue().getMetaData()));
                writer.write('\t');
                writer.write(encode(entry.getValue().getInputs()));
                writer.write('\t');
                writer.write(encode(entry.getValue().getOutputs()));
                writer.write('\t');
                writer.write(encode(entry.getValue().getSecrets()));
                writer.newLine();
            }
        }
        return summary();
    }

    /**
     * Imports cache metadata from a file produced by {@link #exportCache(Path)}.
     *
     * @param input source file path
     * @return cache summary after import
     * @throws IOException when the file cannot be read or decoded
     */
    public CacheSummary importCache(Path input) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (!EXPORT_HEADER.equals(header)) {
                throw new IOException(GitHubWorkflowLocalize.settingsCacheImportUnsupported().get());
            }
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\t", -1);
                if (parts.length != 5) {
                    throw new IOException(GitHubWorkflowLocalize.settingsCacheImportBrokenline().get());
                }
                GitHubAction action = new GitHubAction()
                    .setMetaData(decode(parts[1]))
                    .setInputs(decode(parts[2]))
                    .setOutputs(decode(parts[3]))
                    .setSecrets(decode(parts[4]));
                String key = decode(parts[0]).getOrDefault("key", "");
                if (key.isBlank()) {
                    throw new IOException(GitHubWorkflowLocalize.settingsCacheImportBrokenkey().get());
                }
                myActions.put(key, action);
            }
        }
        triggerSyntaxHighlightingForActiveFiles();
        return summary();
    }

    public CacheSummary clear() {
        myActions.clear();
        myInFlightResolutions.clear();
        mySearchResults.clear();
        triggerSyntaxHighlightingForActiveFiles();
        return summary();
    }

    public CacheSummary refreshResolvedRemoteActions(@Nullable Project project) {
        List<GitHubAction> actions = myActions.values().stream()
            .distinct()
            .filter(GitHubAction::isResolved)
            .filter(action -> !action.isLocal())
            .peek(action -> action.expiryTime(0))
            .toList();
        resolveAsync(project, actions);
        return summary();
    }

    public void resolveUnresolvedRemoteActions() {
        mySearchResults.clear();
        resolveInBackground(myActions.values().stream()
            .distinct()
            .filter(action -> !action.isLocal())
            .filter(action -> !action.isResolved())
            .filter(action -> !action.isSuppressed())
            .filter(GitHubActionCache::canResolve)
            .toList());
    }

    public long restoreWarnings() {
        List<GitHubAction> suppressedActions = myActions.values().stream()
            .distinct()
            .filter(GitHubAction::hasSuppressedWarnings)
            .toList();
        suppressedActions.forEach(GitHubAction::restoreWarnings);
        if (!suppressedActions.isEmpty()) {
            triggerSyntaxHighlightingForActiveFiles();
        }
        return suppressedActions.size();
    }

    public List<String> remoteRefsFor(@Nullable String usesBase, int limit) {
        if (usesBase == null || usesBase.isBlank() || limit < 1) {
            return List.of();
        }
        List<String> cachedRefs = cachedRemoteRefsFor(usesBase, limit);
        if (!cachedRefs.isEmpty()) {
            return cachedRefs;
        }
        String key = REFS_PREFIX + usesBase;
        if (RemoteActionProviders.hasAccountFor(usesBase) && myInFlightResolutions.add(key)) {
            Application.get().executeOnPooledThread(() -> {
                try {
                    List<String> refs = RemoteActionProviders.latestRefs(usesBase, limit);
                    if (!refs.isEmpty()) {
                        GitHubAction action = createGithubAction(false, usesBase + "@" + refs.get(0), usesBase + "@" + refs.get(0))
                            .remoteRefs(refs)
                            .expiryTime(System.currentTimeMillis() + CACHE_ONE_DAY);
                        myActions.put(key, action);
                    }
                }
                catch (Exception e) {
                    LOG.warn("Failed to load remote refs [" + usesBase + "]", e);
                }
                finally {
                    myInFlightResolutions.remove(key);
                }
            });
        }
        return List.of();
    }

    public Map<String, String> searchUses(@Nullable String usesPrefix, int limit) {
        if (usesPrefix == null || usesPrefix.isBlank() || limit < 1) {
            return Map.of();
        }
        String key = usesPrefix.trim();
        Map<String, String> cached = mySearchResults.get(key);
        if (cached != null) {
            return cached;
        }
        if (RemoteActionProviders.hasAccountFor(key) && myInFlightSearches.add(key)) {
            Application.get().executeOnPooledThread(() -> {
                try {
                    mySearchResults.put(key, Map.copyOf(RemoteActionProviders.searchUses(key, limit)));
                }
                catch (Exception e) {
                    LOG.warn("Failed to search remote uses [" + key + "]", e);
                }
                finally {
                    myInFlightSearches.remove(key);
                }
            });
        }
        return Map.of();
    }

    private List<String> cachedRemoteRefsFor(String usesBase, int limit) {
        return myActions.values().stream()
            .distinct()
            .filter(action -> !action.isLocal())
            .filter(action -> remoteBase(action.usesValue()).filter(usesBase::equals).isPresent())
            .flatMap(action -> action.remoteRefs().stream())
            .distinct()
            .limit(limit)
            .toList();
    }

    private static Optional<String> remoteBase(@Nullable String usesValue) {
        String value = ofNullable(usesValue).orElse("");
        int refSeparator = value.lastIndexOf('@');
        return refSeparator > 0 ? Optional.of(value.substring(0, refSeparator)) : Optional.empty();
    }

    public @Nullable GitHubAction reloadAsync(@Nullable Project project, @Nullable String usesValue) {
        if (project == null || usesValue == null) {
            return null;
        }
        GitHubAction oldAction = myActions.get(usesValue);
        if (oldAction == null) {
            return null;
        }
        GitHubAction action = saveNewAction(project, oldAction);
        if (action != null) {
            Application.get().executeOnPooledThread(() -> {
                try {
                    myActionResolver.get().resolve(action);
                }
                finally {
                    triggerSyntaxHighlightingForActiveFiles();
                }
            });
        }
        return action;
    }

    public void resolveAsync(@Nullable Project project, @Nullable Collection<GitHubAction> actions) {
        List<GitHubAction> queuedActions = queuedActions(actions);
        if (queuedActions.isEmpty()) {
            return;
        }
        if (project == null || project.isDisposed()) {
            resolveQueued(queuedActions, null);
            return;
        }
        project.getApplication().getInstance(ProgressBuilderFactory.class)
            .newProgressBuilder(project, GitHubWorkflowLocalize.workflowCacheProgressTitle())
            .execute(project.getUIAccess(), () -> Coroutine.first(CodeExecution.<Object, Object>apply((input, continuation) -> {
                resolveQueued(queuedActions, ProgressManager.getInstance().getProgressIndicator());
                return null;
            })));
    }

    private void resolveQueued(List<GitHubAction> queuedActions, @Nullable ProgressIndicator indicator) {
        Runnable task = () -> {
            AtomicInteger index = new AtomicInteger(0);
            double totalActions = queuedActions.size();
            if (indicator != null) {
                indicator.setIndeterminate(false);
            }
            queuedActions.forEach(action -> {
                int i = index.incrementAndGet();
                try {
                    if (indicator != null) {
                        indicator.setText(GitHubWorkflowLocalize.workflowCacheProgressText(
                            action.isAction() ? GitHubWorkflowLocalize.workflowCacheKindAction() : GitHubWorkflowLocalize.workflowCacheKindWorkflow(),
                            action.name()
                        ));
                    }
                    resolveQueuedAction(action);
                }
                catch (Exception e) {
                    LOG.warn("Failed to resolve [" + action.usesValue() + "]", e);
                }
                finally {
                    myInFlightResolutions.remove(action.usesValue());
                    if (indicator != null) {
                        indicator.setFraction(i / totalActions);
                    }
                }
            });
            triggerSyntaxHighlightingForActiveFiles();
        };
        if (indicator == null) {
            Application.get().executeOnPooledThread(task);
        }
        else {
            task.run();
        }
    }

    private void resolveInBackground(@Nullable Collection<GitHubAction> actions) {
        List<GitHubAction> queuedActions = queuedActions(actions);
        if (queuedActions.isEmpty()) {
            return;
        }
        Application.get().executeOnPooledThread(() -> {
            queuedActions.forEach(action -> {
                try {
                    resolveQueuedAction(action);
                }
                catch (Exception e) {
                    LOG.warn("Failed to resolve [" + action.usesValue() + "]", e);
                }
                finally {
                    myInFlightResolutions.remove(action.usesValue());
                }
            });
            triggerSyntaxHighlightingForActiveFiles();
        });
    }

    private List<GitHubAction> queuedActions(@Nullable Collection<GitHubAction> actions) {
        return ofNullable(actions).stream()
            .flatMap(Collection::stream)
            .filter(Objects::nonNull)
            .filter(action -> myInFlightResolutions.add(action.usesValue()))
            .toList();
    }

    private void resolveQueuedAction(GitHubAction action) {
        jitterBeforeRemoteRequest(action);
        myActionResolver.get().resolve(action);
        if (action.isResolved()) {
            action.expiryTime(System.currentTimeMillis() + (CACHE_ONE_DAY * 14));
        }
    }

    public ActionResolver useActionResolverForTests(@Nullable ActionResolver resolver) {
        return myActionResolver.getAndSet(ofNullable(resolver).orElse(GitHubAction::resolve));
    }

    private static void jitterBeforeRemoteRequest(@Nullable GitHubAction action) {
        if (action == null || action.isLocal()) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(75, 251));
        }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    public static void triggerSyntaxHighlightingForActiveFiles() {
        Application application = Application.get();
        if (application.isUnitTestMode()) {
            return;
        }
        application.executeOnPooledThread(() -> Stream.of(ProjectManager.getInstance().getOpenProjects())
            .filter(project -> !project.isDisposed())
            .forEach(GitHubActionCache::triggerSyntaxHighlightingForActiveFiles));
    }

    private static void triggerSyntaxHighlightingForActiveFiles(Project project) {
        List<PsiFile> files = ReadAction.compute(() -> {
            if (project.isDisposed()) {
                return List.<PsiFile>of();
            }
            DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
            return Stream.of(FileEditorManager.getInstance(project).getSelectedFiles())
                .filter(VirtualFile::isValid)
                .filter(virtualFile -> toPath(virtualFile).map(WorkflowYaml::isWorkflowPath).orElse(false))
                .map(virtualFile -> PsiManager.getInstance(project).findFile(virtualFile))
                .filter(Objects::nonNull)
                .filter(PsiFile::isValid)
                .filter(daemonCodeAnalyzer::isHighlightingAvailable)
                .toList();
        });
        if (!files.isEmpty() && !project.isDisposed()) {
            DaemonCodeAnalyzer daemonCodeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
            files.forEach(daemonCodeAnalyzer::restart);
        }
    }

    public static void resolveActionsAsync(@Nullable Collection<GitHubAction> actions) {
        getActionCache().resolveInBackground(ofNullable(actions).orElseGet(List::of).stream()
            .filter(Objects::nonNull)
            .filter(GitHubActionCache::canResolve)
            .toList());
    }

    public static @Nullable GitHubAction reloadActionAsync(@Nullable Project project, @Nullable String usesValue) {
        return getActionCache().reloadAsync(project, usesValue);
    }

    public static @Nullable GitHubAction getAction(@Nullable PsiElement psiElement) {
        return getUsesString(psiElement).map(usesValue -> getActionCache().get(getProject(psiElement), usesValue)).orElse(null);
    }

    public static @Nullable PsiElement removeAction(@Nullable PsiElement psiElement) {
        getUsesString(psiElement).ifPresent(GitHubActionCache::removeAction);
        return psiElement;
    }

    public static @Nullable GitHubAction removeAction(@Nullable GitHubAction action) {
        ofNullable(action).map(GitHubAction::downloadUrl).ifPresent(GitHubActionCache::removeAction);
        return action;
    }

    public static @Nullable String removeAction(@Nullable String usesValue) {
        ofNullable(usesValue).ifPresent(value -> getActionCache().remove(value));
        return usesValue;
    }

    public static Optional<YAMLKeyValue> isUseElement(@Nullable PsiElement psiElement) {
        return ofNullable(psiElement)
            .filter(PsiElement::isValid)
            .filter(YAMLKeyValue.class::isInstance)
            .map(YAMLKeyValue.class::cast)
            .filter(keyValue -> FIELD_USES.equals(keyValue.getKeyText()));
    }

    private @Nullable GitHubAction saveNewAction(@Nullable Project project, GitHubAction oldAction) {
        boolean isLocal = isLocalUses(oldAction.usesValue());
        String normalizedUses = normalizeUsesValue(oldAction.usesValue(), isLocal);
        return saveNewAction(normalizedUses, getAbsolutePath(isLocal, normalizedUses, project), isLocal, oldAction);
    }

    private @Nullable GitHubAction createdOrQueuedRemote(String usesValue, String path, boolean isLocal, @Nullable GitHubAction oldAction) {
        GitHubAction action = saveNewAction(usesValue, path, isLocal, oldAction);
        if (!isLocal && action != null && !action.isResolved()) {
            queueRefresh(action);
        }
        return action;
    }

    private @Nullable GitHubAction saveNewAction(String usesValue, @Nullable String path, boolean isLocal, @Nullable GitHubAction oldAction) {
        if (path == null) {
            return null;
        }
        GitHubAction newAction = createGithubAction(isLocal, usesValue, path);
        if (oldAction != null) {
            newAction.isSuppressed(oldAction.isSuppressed());
            oldAction.ignoredInputs().forEach(input -> newAction.suppressInput(input, true));
            oldAction.ignoredOutputs().forEach(input -> newAction.suppressOutput(input, true));
        }
        myActions.put(path, newAction);
        return newAction;
    }

    private String getAbsolutePath(boolean isLocal, String subPath, @Nullable Project project) {
        if (!isLocal) {
            return subPath;
        }
        return ofNullable(project)
            .map(Project::getBaseDir)
            .map(projectDir -> findActionYaml(subPath, projectDir))
            .flatMap(WorkflowPsi::toPath)
            .map(Path::toString)
            .orElse(subPath);
    }

    private static boolean isLocalUses(@Nullable String usesValue) {
        String normalized = ofNullable(usesValue).orElse("").replace('\\', '/').trim();
        return normalized.startsWith("./")
            || normalized.startsWith("../")
            || normalized.startsWith("/")
            || normalized.matches("^[A-Za-z]:/.*");
    }

    private static String normalizeUsesValue(@Nullable String usesValue, boolean isLocal) {
        String normalized = ofNullable(usesValue).orElse("").trim();
        if (isLocal || normalized.contains("@") || normalized.startsWith("docker://") || normalized.isBlank()) {
            return normalized;
        }
        return normalized + "@" + DEFAULT_REMOTE_REF;
    }

    private static Optional<String> getUsesString(@Nullable PsiElement psiElement) {
        return ofNullable(psiElement).filter(PsiElement::isValid)
            .flatMap(GitHubActionCache::getUsesValue)
            .or(() -> getChildWithUsesValue(psiElement));
    }

    private static Optional<String> getUsesValue(PsiElement psiElement) {
        return isUseElement(psiElement).flatMap(WorkflowPsi::getText);
    }

    private static Optional<String> getChildWithUsesValue(@Nullable PsiElement psiElement) {
        return ofNullable(psiElement).filter(PsiElement::isValid).flatMap(element -> WorkflowPsi.getChild(element, FIELD_USES)).flatMap(WorkflowPsi::getText);
    }

    private static long estimate(@Nullable String value) {
        return value == null ? 0 : value.length() * 2L;
    }

    private static long estimate(GitHubAction action) {
        return estimate(action.getMetaData()) + estimate(action.getInputs()) + estimate(action.getOutputs()) + estimate(action.getSecrets());
    }

    private static long estimate(Map<String, String> values) {
        return values.entrySet().stream()
            .mapToLong(entry -> estimate(entry.getKey()) + estimate(entry.getValue()))
            .sum();
    }

    private static String encode(String key) throws IOException {
        return encode(Map.of("key", key));
    }

    private static String encode(Map<String, String> values) throws IOException {
        Properties properties = new Properties();
        properties.putAll(values);
        StringWriter writer = new StringWriter();
        properties.store(writer, null);
        return Base64.getEncoder().encodeToString(writer.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> decode(String value) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)));
        Map<String, String> result = new LinkedHashMap<>();
        properties.stringPropertyNames().forEach(key -> result.put(key, properties.getProperty(key)));
        return result;
    }

    static class ActionMetadataChangeListener extends PsiTreeChangeAdapter {
        @Override
        public void childReplaced(PsiTreeChangeEvent event) {
            ofNullable(event.getNewChild())
                .filter(psiElement -> WorkflowYaml.getWorkflowFile(psiElement).isPresent())
                .flatMap(psiElement -> WorkflowPsi.getParent(psiElement, FIELD_USES))
                .map(GitHubActionCache::getAction)
                .filter(action -> !action.isResolved())
                .map(List::of)
                .ifPresent(GitHubActionCache::resolveActionsAsync);
        }

        @Override
        public void childrenChanged(PsiTreeChangeEvent event) {
            ofNullable(event.getParent())
                .filter(psiElement -> WorkflowYaml.getWorkflowFile(psiElement).isPresent())
                .map(psiElement -> WorkflowPsi.getAllElements(psiElement, FIELD_USES))
                .map(usesList -> usesList.stream()
                    .map(GitHubActionCache::getAction)
                    .filter(Objects::nonNull)
                    .filter(action -> !action.isLocal())
                    .filter(action -> !action.isResolved())
                    .toList())
                .ifPresent(GitHubActionCache::resolveActionsAsync);
        }
    }

    static void asyncInitAllActions(Project project, @Nullable VirtualFile virtualFile) {
        if (virtualFile == null || !virtualFile.isValid() || !toPath(virtualFile).map(WorkflowYaml::isWorkflowPath).orElse(false)) {
            return;
        }
        Application.get().executeOnPooledThread(() -> {
            List<GitHubAction> actions = ReadAction.compute(() -> project.isDisposed() ? List.<GitHubAction>of() : unresolvedActions(project, virtualFile));
            resolveActionsAsync(actions);
        });
    }

    private static List<GitHubAction> unresolvedActions(Project project, VirtualFile virtualFile) {
        List<GitHubAction> actions = new ArrayList<>();
        Optional.ofNullable(PsiManager.getInstance(project).findFile(virtualFile))
            .map(psiFile -> WorkflowPsi.getAllElements(psiFile, FIELD_USES))
            .ifPresent(usesList -> usesList.stream()
                .map(GitHubActionCache::getAction)
                .filter(Objects::nonNull)
                .filter(action -> !action.isSuppressed())
                .filter(action -> !action.isResolved())
                .forEach(actions::add));
        return actions;
    }

    public record CacheSummary(long total, long resolved, long remote, long expired, long suppressed) {
    }

    public record CacheEntry(
        String key,
        String name,
        String usesValue,
        boolean local,
        boolean resolved,
        boolean expired,
        boolean suppressed,
        long expiryTime
    ) {
        static CacheEntry from(String key, GitHubAction action, long now) {
            return new CacheEntry(
                key,
                action.displayName().isBlank() ? action.name() : action.displayName(),
                action.usesValue(),
                action.isLocal(),
                action.isResolved(),
                now >= action.expiryTime(),
                action.hasSuppressedWarnings(),
                action.expiryTime()
            );
        }
    }
}
