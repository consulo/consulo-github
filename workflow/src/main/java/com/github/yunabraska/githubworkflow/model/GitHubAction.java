/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.model;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.github.yunabraska.githubworkflow.syntax.WorkflowPsi;
import consulo.application.ReadAction;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.CACHE_ONE_DAY;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ON;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.getChild;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowPsi.hasText;
import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.unmodifiableMap;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;

public class GitHubAction {
    private static final Logger LOG = Logger.getInstance(GitHubAction.class);

    private final Map<String, String> myMetaData = new ConcurrentHashMap<>();
    private final Map<String, String> myInputs = new ConcurrentHashMap<>();
    private final Map<String, String> myOutputs = new ConcurrentHashMap<>();
    private final Map<String, String> mySecrets = new ConcurrentHashMap<>();

    private final Set<String> myIgnoredInputs = ConcurrentHashMap.newKeySet();
    private final Set<String> myIgnoredOutputs = ConcurrentHashMap.newKeySet();

    public static GitHubAction createSchemaAction(String url, String content) {
        return new GitHubAction()
            .isResolved(true)
            .isLocal(true)
            .isSuppressed(false)
            .name(content)
            .downloadUrl(url)
            .expiryTime(System.currentTimeMillis() + (CACHE_ONE_DAY * 30));
    }

    public static GitHubAction createGithubAction(boolean isLocal, String usesValue, String absolutePath) {
        GitHubActionCoordinates coordinates = GitHubActionCoordinates.of(isLocal, usesValue, absolutePath);
        return new GitHubAction()
            .name(coordinates.name())
            .usesValue(coordinates.usesValue())
            .downloadUrl(coordinates.downloadUrl())
            .githubUrl(coordinates.githubUrl())
            .expiryTime(System.currentTimeMillis() + (CACHE_ONE_DAY * 14))
            .isLocal(coordinates.local())
            .setAction(coordinates.action())
            .isSuppressed(false);
    }

    private record GitHubActionCoordinates(
        boolean local,
        boolean action,
        String name,
        String usesValue,
        String downloadUrl,
        String githubUrl
    ) {
        static GitHubActionCoordinates of(boolean local, @Nullable String usesValue, @Nullable String absolutePath) {
            String safeUses = ofNullable(usesValue).orElse("");
            String safePath = ofNullable(absolutePath).orElse("");
            boolean action = local ? !isWorkflowFile(safeUses) : isRemoteActionPath(safePath);
            int tagIndex = safeUses.indexOf('@');
            int ownerSeparator = safeUses.indexOf('/');
            int repoSeparator = safeUses.indexOf('/', ownerSeparator + 1);
            if (tagIndex == -1 || ownerSeparator >= tagIndex) {
                return new GitHubActionCoordinates(local, action, safeUses, safeUses, local ? safePath : "", "");
            }
            String ref = safeUses.substring(tagIndex + 1);
            String slug = safeUses.substring(0, repoSeparator > 0 ? repoSeparator : tagIndex);
            if (action) {
                String subPath = repoSeparator < tagIndex && repoSeparator > 0 ? "/" + safeUses.substring(repoSeparator + 1, tagIndex) : "";
                return new GitHubActionCoordinates(
                    local,
                    true,
                    slug + subPath,
                    safeUses,
                    local ? safePath : rawUrl(slug, ref, subPath + "/action.yml"),
                    local ? "" : "https://github.com/" + slug + "/tree/" + ref + subPath + "#readme"
                );
            }
            int fileStart = safeUses.lastIndexOf('/') + 1;
            String workflowName = fileStart >= tagIndex ? "InvalidAction" : safeUses.substring(fileStart, tagIndex);
            return new GitHubActionCoordinates(
                local,
                false,
                slug,
                safeUses,
                local ? safePath : rawUrl(slug, ref, ".github/workflows/" + workflowName),
                local ? "" : "https://github.com/" + slug + "/blob/" + ref + "/.github/workflows/" + workflowName
            );
        }

        private static boolean isRemoteActionPath(String absolutePath) {
            return !absolutePath.contains(".yaml") && !absolutePath.contains(".yml") && !absolutePath.contains(".action.y");
        }

        private static boolean isWorkflowFile(@Nullable String usesValue) {
            return ofNullable(usesValue)
                .map(value -> value.replace('\\', '/'))
                .filter(value -> value.contains(".github/workflows/"))
                .filter(value -> value.endsWith(".yml") || value.endsWith(".yaml"))
                .isPresent();
        }

        private static String rawUrl(String slug, String ref, String path) {
            return "https://raw.githubusercontent.com/" + slug + "/" + ref + "/" + path.replaceFirst("^/+", "");
        }
    }

    public Optional<String> getLocalPath(@Nullable Project project) {
        return getLocalVirtualFile(project)
            .map(VirtualFile::getPath)
            .or(() -> isLocal() ? ofNullable(downloadUrl()).filter(WorkflowPsi::hasText) : Optional.empty());
    }

    public Optional<VirtualFile> getLocalVirtualFile(@Nullable Project project) {
        if (!isLocal()) {
            return Optional.empty();
        }
        return ofNullable(project)
            .map(Project::getBaseDir)
            .map(dir -> findActionYaml(usesValue(), dir))
            .or(() -> ofNullable(downloadUrl()).filter(WorkflowPsi::hasText).map(path -> LocalFileSystem.getInstance().findFileByPath(path)));
    }

    public synchronized GitHubAction resolve() {
        if ((!isResolved() || System.currentTimeMillis() >= expiryTime()) && !isSuppressed()) {
            extractParameters();
        }
        return this;
    }

    public Map<String, String> freshInputs() {
        if (isLocal()) {
            extractLocalParameters();
        }
        String manualInput = GitHubWorkflowLocalize.documentationManualinput().get();
        return concatMap(myInputs, myIgnoredInputs.stream().filter(WorkflowPsi::hasText).collect(Collectors.toMap(key -> key, value -> manualInput)));
    }

    public Map<String, String> freshOutputs() {
        return freshOutputs(true);
    }

    public Map<String, String> freshOutputs(boolean withIgnoredItems) {
        if (isLocal()) {
            extractLocalParameters();
        }
        if (!withIgnoredItems) {
            return unmodifiableMap(myOutputs);
        }
        String manualOutput = GitHubWorkflowLocalize.documentationManualoutput().get();
        return concatMap(myOutputs, myIgnoredOutputs.stream().filter(WorkflowPsi::hasText).collect(Collectors.toMap(key -> key, value -> manualOutput)));
    }

    public Map<String, String> freshSecrets() {
        if (isLocal()) {
            extractLocalParameters();
        }
        return unmodifiableMap(mySecrets);
    }

    public String name() {
        return myMetaData.getOrDefault("name", "");
    }

    public GitHubAction name(@Nullable String name) {
        ofNullable(name).ifPresent(s -> myMetaData.put("name", s));
        return this;
    }

    public String displayName() {
        return myMetaData.getOrDefault("displayName", name());
    }

    public GitHubAction displayName(@Nullable String displayName) {
        ofNullable(displayName).filter(WorkflowPsi::hasText).ifPresent(s -> myMetaData.put("displayName", s));
        return this;
    }

    public String description() {
        return myMetaData.getOrDefault("description", "");
    }

    public GitHubAction description(@Nullable String description) {
        ofNullable(description).filter(WorkflowPsi::hasText).ifPresent(s -> myMetaData.put("description", s));
        return this;
    }

    public String downloadUrl() {
        return myMetaData.getOrDefault("downloadUrl", "");
    }

    public GitHubAction downloadUrl(@Nullable String downloadUrl) {
        ofNullable(downloadUrl).ifPresent(s -> myMetaData.put("downloadUrl", s));
        return this;
    }

    public String githubUrl() {
        return myMetaData.getOrDefault("githubUrl", "");
    }

    public GitHubAction githubUrl(@Nullable String githubUrl) {
        ofNullable(githubUrl).ifPresent(s -> myMetaData.put("githubUrl", s));
        return this;
    }

    public String usesValue() {
        return myMetaData.getOrDefault("usesValue", "");
    }

    public GitHubAction usesValue(@Nullable String usesValue) {
        ofNullable(usesValue).ifPresent(s -> myMetaData.put("usesValue", s));
        return this;
    }

    public long expiryTime() {
        try {
            return Long.parseLong(myMetaData.getOrDefault("expiryTime", "-1"));
        }
        catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public GitHubAction expiryTime(long expiryTime) {
        myMetaData.put("expiryTime", Long.toString(expiryTime));
        return this;
    }

    public boolean isLocal() {
        return parseBoolean(myMetaData.getOrDefault("isLocal", "false"));
    }

    public GitHubAction isLocal(boolean local) {
        myMetaData.put("isLocal", Boolean.toString(local));
        return this;
    }

    public boolean isAction() {
        return parseBoolean(myMetaData.getOrDefault("isAction", "false"));
    }

    public GitHubAction setAction(boolean action) {
        myMetaData.put("isAction", Boolean.toString(action));
        return this;
    }

    public boolean isResolved() {
        return parseBoolean(myMetaData.getOrDefault("isResolved", "false"));
    }

    public GitHubAction isResolved(boolean isResolved) {
        myMetaData.put("isResolved", Boolean.toString(isResolved));
        return this;
    }

    public boolean isSuppressed() {
        return parseBoolean(myMetaData.getOrDefault("isSuppressed", "false"));
    }

    public GitHubAction isSuppressed(boolean isSuppressed) {
        myMetaData.put("isSuppressed", Boolean.toString(isSuppressed));
        return this;
    }

    public GitHubAction suppressInput(String id, boolean supress) {
        if (supress) {
            myIgnoredInputs.add(id);
        }
        else {
            myIgnoredInputs.remove(id);
        }
        myMetaData.put("ignoredInputs", myIgnoredInputs.stream().filter(WorkflowPsi::hasText).collect(Collectors.joining(";")));
        return this;
    }

    public GitHubAction suppressOutput(String id, boolean supress) {
        if (supress) {
            myIgnoredOutputs.add(id);
        }
        else {
            myIgnoredOutputs.remove(id);
        }
        myMetaData.put("ignoredOutputs", myIgnoredOutputs.stream().filter(WorkflowPsi::hasText).collect(Collectors.joining(";")));
        return this;
    }

    public Set<String> ignoredInputs() {
        return myIgnoredInputs.stream()
            .filter(WorkflowPsi::hasText)
            .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> ignoredOutputs() {
        return myIgnoredOutputs.stream()
            .filter(WorkflowPsi::hasText)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Checks whether this action has any suppressed validation warnings.
     *
     * @return {@code true} when the whole action, at least one input, or at least one output is suppressed
     */
    public boolean hasSuppressedWarnings() {
        return isSuppressed()
            || myIgnoredInputs.stream().anyMatch(WorkflowPsi::hasText)
            || myIgnoredOutputs.stream().anyMatch(WorkflowPsi::hasText);
    }

    /**
     * Restores all validation warnings for this action metadata entry.
     *
     * @return this action instance after suppressions were removed
     */
    public GitHubAction restoreWarnings() {
        isSuppressed(false);
        myIgnoredInputs.clear();
        myIgnoredOutputs.clear();
        myMetaData.put("ignoredInputs", "");
        myMetaData.put("ignoredOutputs", "");
        return this;
    }

    public Map<String, String> getInputs() {
        return unmodifiableMap(myInputs);
    }

    public GitHubAction setInputs(@Nullable Map<String, String> inputs) {
        ofNullable(inputs).ifPresent(myInputs::putAll);
        return this;
    }

    public Map<String, String> getOutputs() {
        return unmodifiableMap(myOutputs);
    }

    public GitHubAction setOutputs(@Nullable Map<String, String> outputs) {
        ofNullable(outputs).ifPresent(myOutputs::putAll);
        return this;
    }

    public Map<String, String> getSecrets() {
        return unmodifiableMap(mySecrets);
    }

    public GitHubAction setSecrets(@Nullable Map<String, String> secrets) {
        ofNullable(secrets).ifPresent(mySecrets::putAll);
        return this;
    }

    public Map<String, String> getMetaData() {
        return unmodifiableMap(myMetaData);
    }

    public GitHubAction setMetaData(@Nullable Map<String, String> metaData) {
        ofNullable(metaData).ifPresent(values -> {
            myMetaData.putAll(values);
            myIgnoredInputs.addAll(Arrays.stream(values.getOrDefault("ignoredInputs", "").split(";")).filter(WorkflowPsi::hasText).toList());
            myIgnoredOutputs.addAll(Arrays.stream(values.getOrDefault("ignoredOutputs", "").split(";")).filter(WorkflowPsi::hasText).toList());
        });
        return this;
    }

    public static @Nullable VirtualFile findActionYaml(String subPath, VirtualFile projectDir) {
        String localPath = normalizeLocalPath(subPath);
        return ofNullable(projectDir.findFileByRelativePath(localPath)).filter(p -> !p.isDirectory())
            .or(() -> ofNullable(projectDir.findFileByRelativePath(actionYamlPath(localPath, "action.yml"))).filter(VirtualFile::isValid).filter(p -> !p.isDirectory()))
            .or(() -> ofNullable(projectDir.findFileByRelativePath(actionYamlPath(localPath, "action.yaml"))).filter(VirtualFile::isValid).filter(p -> !p.isDirectory()))
            .orElse(null);
    }

    private static String normalizeLocalPath(@Nullable String subPath) {
        if (subPath == null || subPath.isBlank() || ".".equals(subPath) || "./".equals(subPath)) {
            return "";
        }
        return subPath.startsWith("./") ? subPath.substring(2) : subPath;
    }

    private static String actionYamlPath(String localPath, String fileName) {
        return localPath.isBlank() ? fileName : localPath + "/" + fileName;
    }

    private void extractParameters() {
        boolean wasResolved = isResolved();
        try {
            if (isLocal()) {
                extractLocalParameters();
            }
            else {
                extractRemoteParameters();
            }
        }
        catch (Exception e) {
            LOG.warn("Failed to set parameters [" + name() + "]", e);
            if (wasResolved) {
                expiryTime(System.currentTimeMillis() + CACHE_ONE_DAY);
            }
            else {
                isResolved(false);
            }
        }
    }

    private void extractRemoteParameters() {
        boolean wasResolved = isResolved();
        RemoteActionProviders.resolve(usesValue())
            .ifPresentOrElse(resolution -> {
                name(resolution.name());
                downloadUrl(resolution.downloadUrl());
                githubUrl(resolution.githubUrl());
                setAction(resolution.action());
                remoteRefs(resolution.refs());
                setParameters(resolution.content());
            }, () -> {
                if (wasResolved) {
                    expiryTime(System.currentTimeMillis() + CACHE_ONE_DAY);
                }
                else {
                    isResolved(false);
                }
            });
    }

    private void extractLocalParameters() {
        of(downloadUrl()).flatMap(WorkflowPsi::toPath).filter(Files::isRegularFile).map(file -> {
            try {
                return Files.readString(file);
            }
            catch (IOException ignored) {
                return null;
            }
        }).or(this::readLocalVirtualFileContent).ifPresent(this::setParameters);
    }

    private Optional<String> readLocalVirtualFileContent() {
        return Arrays.stream(ProjectManager.getInstance().getOpenProjects())
            .map(this::getLocalVirtualFile)
            .flatMap(Optional::stream)
            .filter(VirtualFile::isValid)
            .filter(virtualFile -> !virtualFile.isDirectory())
            .findFirst()
            .flatMap(GitHubAction::readVirtualFileContent);
    }

    private static Optional<String> readVirtualFileContent(VirtualFile virtualFile) {
        try {
            return Optional.of(new String(virtualFile.contentsToByteArray(), StandardCharsets.UTF_8));
        }
        catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private void setParameters(String content) {
        isResolved(hasText(content));
        readPsiElement(ProjectManager.getInstance().getDefaultProject(), downloadUrl(), content, psiFile -> {
            displayName(WorkflowPsi.getText(psiFile.getContainingFile(), "name").orElse(name()));
            description(WorkflowPsi.getText(psiFile.getContainingFile(), "description").orElse(""));
            myInputs.clear();
            myInputs.putAll(getActionParameters(psiFile, FIELD_INPUTS, isAction()));
            myOutputs.clear();
            myOutputs.putAll(getActionParameters(psiFile, FIELD_OUTPUTS, isAction()));
            mySecrets.clear();
            mySecrets.putAll(getActionParameters(psiFile, FIELD_SECRETS, isAction()));
        });
    }

    public List<String> remoteRefs() {
        return Arrays.stream(myMetaData.getOrDefault("remoteRefs", "").split(";"))
            .filter(WorkflowPsi::hasText)
            .toList();
    }

    public GitHubAction remoteRefs(@Nullable List<String> refs) {
        myMetaData.put("remoteRefs", ofNullable(refs).orElseGet(List::of).stream()
            .filter(WorkflowPsi::hasText)
            .distinct()
            .collect(Collectors.joining(";")));
        return this;
    }

    private static Map<String, String> getActionParameters(PsiElement psiElement, String fieldName, boolean action) {
        return (action
            ? getChild(psiElement.getContainingFile(), fieldName)
            : getChild(psiElement.getContainingFile(), FIELD_ON)
            .flatMap(on -> getChild(on, "workflow_call"))
            .flatMap(workflowCall -> getChild(workflowCall, fieldName)))
            .map(WorkflowPsi::getChildren)
            .map(children -> children.stream().collect(Collectors.toMap(
                YAMLKeyValue::getKeyText,
                field -> WorkflowPsi.getDescription(field, FIELD_INPUTS.equals(fieldName)),
                (existing, replacement) -> existing
            )))
            .orElseGet(Collections::emptyMap);
    }

    private static void readPsiElement(Project project, String fileName, String fileContent, @Nullable Consumer<PsiFile> action) {
        if (action == null) {
            return;
        }
        ReadAction.run(() -> {
            try {
                String name = hasText(fileName) ? fileName.substring(fileName.lastIndexOf('/') + 1) : "action.yml";
                action.accept(PsiFileFactory.getInstance(project).createFileFromText(name, YAMLFileType.YML, fileContent.replaceAll("\r?\\n|\\r", "\n")));
            }
            catch (Exception e) {
                LOG.warn("Failed to read action metadata [" + fileName + "]", e);
            }
        });
    }

    private static <K, V> Map<K, V> concatMap(Map<K, V> map1, Map<K, V> map2) {
        return Stream.concat(map1.entrySet().stream(), map2.entrySet().stream()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GitHubAction that = (GitHubAction) o;
        return Objects.equals(downloadUrl(), that.downloadUrl());
    }

    @Override
    public int hashCode() {
        return Objects.hash(downloadUrl());
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", GitHubAction.class.getSimpleName() + "[", "]")
            .add("metaData=" + myMetaData)
            .add("inputs=" + (myInputs.size() + myIgnoredInputs.size()))
            .add("outputs=" + (myOutputs.size() + myIgnoredOutputs.size()))
            .add("secrets=" + mySecrets.size())
            .toString();
    }
}
