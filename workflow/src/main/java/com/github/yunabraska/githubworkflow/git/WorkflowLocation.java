/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.git;

import com.github.yunabraska.githubworkflow.syntax.WorkflowYaml;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WorkflowLocation {
    private final YAMLKeyValue myKeyValue;
    private final List<String> myPath;
    private final boolean myWorkflowFile;

    private WorkflowLocation(YAMLKeyValue keyValue, List<String> path, boolean workflowFile) {
        myKeyValue = keyValue;
        myPath = List.copyOf(path);
        myWorkflowFile = workflowFile;
    }

    public static Optional<WorkflowLocation> from(@Nullable PsiElement element) {
        return Optional.ofNullable(element)
            .filter(PsiElement::isValid)
            .flatMap(WorkflowLocation::keyValueOf)
            .map(keyValue -> new WorkflowLocation(keyValue, pathOf(keyValue), isWorkflowFile(keyValue)));
    }

    public YAMLKeyValue keyValue() {
        return myKeyValue;
    }

    public List<String> path() {
        return myPath;
    }

    public boolean workflowFile() {
        return myWorkflowFile;
    }

    public static boolean pathEndsWith(List<String> path, String... expected) {
        if (path.size() < expected.length) {
            return false;
        }
        int offset = path.size() - expected.length;
        for (int index = 0; index < expected.length; index++) {
            if (!expected[index].equals(path.get(offset + index))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isChildOf(List<String> path, String... expectedParent) {
        return path.size() == expectedParent.length + 1 && pathEndsWith(path.subList(0, path.size() - 1), expectedParent);
    }

    public static boolean pathMatches(List<String> path, String... pattern) {
        if (path.size() != pattern.length) {
            return false;
        }
        for (int index = 0; index < pattern.length; index++) {
            if (!"*".equals(pattern[index]) && !pattern[index].equals(path.get(index))) {
                return false;
            }
        }
        return true;
    }

    public static Optional<KeyContext> keyContextAt(@Nullable PsiElement position, int rawOffset) {
        return Optional.ofNullable(position)
            .map(PsiElement::getContainingFile)
            .map(file -> keyContextFromText(file.getText(), rawOffset));
    }

    public static KeyContext keyContextFromText(String wholeText, int rawOffset) {
        int offset = boundedOffset(wholeText, rawOffset);
        int lineStart = currentLineStart(wholeText, offset);
        String currentLine = lineBeforeCaret(wholeText, offset);
        int currentIndent = leadingSpaces(currentLine);
        List<YamlAncestor> stack = new ArrayList<>();
        wholeText.substring(0, Math.min(lineStart, wholeText.length())).lines().forEach(raw -> {
            String content = raw.trim();
            if (!content.isBlank() && !content.startsWith("#")) {
                currentLineKey(content).ifPresent(key -> {
                    int indent = leadingSpaces(raw);
                    while (!stack.isEmpty() && stack.get(stack.size() - 1).indent() >= indent) {
                        stack.remove(stack.size() - 1);
                    }
                    stack.add(new YamlAncestor(indent, key));
                });
            }
        });
        while (!stack.isEmpty() && stack.get(stack.size() - 1).indent() >= currentIndent) {
            stack.remove(stack.size() - 1);
        }
        return new KeyContext(stack.stream().map(YamlAncestor::key).toList(), currentLine);
    }

    public static List<String> pathOf(YAMLKeyValue element) {
        List<String> result = new ArrayList<>();
        PsiElement current = element.getParent();
        while (current != null && current != element.getContainingFile()) {
            if (current instanceof YAMLKeyValue keyValue) {
                result.add(0, keyValue.getKeyText());
            }
            current = current.getParent();
        }
        return result;
    }

    public static Optional<String> currentLineKey(String currentLine) {
        return yamlKey(currentLine.trim());
    }

    public static boolean isValueCompletion(String currentLine) {
        return currentLine.replace("IntellijIdeaRulezzz", "").matches("\\s*[^:#]+:\\s*.*");
    }

    public static String lineBeforeCaret(String wholeText, int rawOffset) {
        int offset = boundedOffset(wholeText, rawOffset);
        int lineStart = currentLineStart(wholeText, offset);
        if (lineStart > offset) {
            return "";
        }
        return wholeText.substring(lineStart, offset).replace("IntellijIdeaRulezzz", "");
    }

    public static int currentLineStart(String wholeText, int offset) {
        if (offset < 1) {
            return 0;
        }
        return wholeText.lastIndexOf('\n', offset - 1) + 1;
    }

    public static int boundedOffset(String wholeText, int rawOffset) {
        return Math.max(0, Math.min(rawOffset, wholeText.length()));
    }

    private static Optional<String> yamlKey(String content) {
        String normalized = content.startsWith("- ") ? content.substring(2).trim() : content;
        int separator = normalized.indexOf(':');
        if (separator <= 0) {
            return Optional.empty();
        }
        return Optional.of(stripYamlKeyQuotes(normalized.substring(0, separator).trim()))
            .filter(key -> !key.isBlank());
    }

    private static int leadingSpaces(String value) {
        int result = 0;
        while (result < value.length() && value.charAt(result) == ' ') {
            result++;
        }
        return result;
    }

    private static String stripYamlKeyQuotes(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Optional<YAMLKeyValue> keyValueOf(PsiElement element) {
        PsiElement current = element;
        while (current != null && current != element.getContainingFile()) {
            if (current instanceof YAMLKeyValue keyValue) {
                return Optional.of(keyValue);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static boolean isWorkflowFile(PsiElement element) {
        return WorkflowYaml.getWorkflowFile(element)
            .filter(WorkflowYaml::isWorkflowFile)
            .isPresent();
    }

    public static class RepositoryResolver {
        private static final Pattern HTTPS_REMOTE = Pattern.compile("https?://([^/]+)/([^/]+)/([^/]+?)(?:[.]git)?/?$");
        private static final Pattern SSH_REMOTE = Pattern.compile("(?:git@|ssh://git@)([^:/]+)[:/]([^/]+)/([^/]+?)(?:[.]git)?/?$");

        public Optional<Repository> resolve(@Nullable Project project) {
            return Optional.ofNullable(project)
                .map(Project::getBasePath)
                .map(Path::of)
                .flatMap(this::resolve);
        }

        public Optional<Repository> resolve(@Nullable Project project, @Nullable VirtualFile file) {
            return repositoryRoot(file)
                .flatMap(this::resolve)
                .or(() -> resolve(project));
        }

        public Optional<Repository> resolve(Path projectDir) {
            return readGitConfig(projectDir)
                .flatMap(RepositoryResolver::firstOriginUrl)
                .flatMap(RepositoryResolver::fromRemoteUrl);
        }

        public Optional<String> branch(@Nullable Project project) {
            return Optional.ofNullable(project)
                .map(Project::getBasePath)
                .map(Path::of)
                .flatMap(this::branch);
        }

        public Optional<String> branch(@Nullable Project project, @Nullable VirtualFile file) {
            return repositoryRoot(file)
                .flatMap(this::branch)
                .or(() -> branch(project));
        }

        public Optional<String> branch(Path projectDir) {
            return gitDir(projectDir)
                .map(dir -> dir.resolve("HEAD"))
                .flatMap(RepositoryResolver::readString)
                .flatMap(RepositoryResolver::branchName);
        }

        public static Optional<Repository> fromRemoteUrl(@Nullable String remoteUrl) {
            return match(HTTPS_REMOTE, remoteUrl).or(() -> match(SSH_REMOTE, remoteUrl));
        }

        public static Optional<String> branchName(@Nullable String head) {
            String prefix = "ref: refs/heads/";
            return Optional.ofNullable(head)
                .map(String::trim)
                .filter(value -> value.startsWith(prefix))
                .map(value -> value.substring(prefix.length()))
                .filter(value -> !value.isBlank());
        }

        private static Optional<Repository> match(Pattern pattern, @Nullable String remoteUrl) {
            Matcher matcher = pattern.matcher(Optional.ofNullable(remoteUrl).orElse("").trim());
            if (!matcher.matches()) {
                return Optional.empty();
            }
            String host = matcher.group(1);
            String owner = matcher.group(2);
            String repo = matcher.group(3);
            String webUrl = "https://" + host;
            String apiUrl = "github.com".equalsIgnoreCase(host)
                ? "https://api.github.com"
                : webUrl + "/api/v3";
            return Optional.of(new Repository(webUrl, apiUrl, owner, repo));
        }

        private static Optional<String> readGitConfig(Path projectDir) {
            Path config = projectDir.resolve(".git").resolve("config");
            if (!Files.isRegularFile(config)) {
                return Optional.empty();
            }
            return readString(config);
        }

        public static Optional<Path> gitDir(Path projectDir) {
            Path dotGit = projectDir.resolve(".git");
            if (Files.isDirectory(dotGit)) {
                return Optional.of(dotGit);
            }
            if (!Files.isRegularFile(dotGit)) {
                return Optional.empty();
            }
            return readString(dotGit)
                .map(String::trim)
                .filter(value -> value.startsWith("gitdir:"))
                .map(value -> value.substring("gitdir:".length()).trim())
                .filter(value -> !value.isBlank())
                .map(Path::of)
                .map(path -> path.isAbsolute() ? path : projectDir.resolve(path).normalize());
        }

        private static Optional<String> readString(Path path) {
            try {
                return Optional.of(Files.readString(path));
            }
            catch (IOException ignored) {
                return Optional.empty();
            }
        }

        private static Optional<Path> repositoryRoot(@Nullable VirtualFile file) {
            Path current = Optional.ofNullable(file)
                .map(VirtualFile::getPath)
                .map(Path::of)
                .map(Path::getParent)
                .orElse(null);
            while (current != null) {
                if (Files.isRegularFile(current.resolve(".git").resolve("config")) || Files.isRegularFile(current.resolve(".git"))) {
                    return Optional.of(current);
                }
                current = current.getParent();
            }
            return Optional.empty();
        }

        private static Optional<String> firstOriginUrl(String config) {
            boolean inOrigin = false;
            for (String line : config.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[remote ")) {
                    inOrigin = trimmed.equals("[remote \"origin\"]");
                    continue;
                }
                if (inOrigin && trimmed.startsWith("url =")) {
                    return Optional.of(trimmed.substring("url =".length()).trim()).filter(value -> !value.isBlank());
                }
            }
            return Optional.empty();
        }
    }

    public record KeyContext(List<String> path, String currentLine) {
    }

    public record Repository(String webUrl, String apiUrl, String owner, String repo) {
    }

    private record YamlAncestor(int indent, String key) {
    }
}
