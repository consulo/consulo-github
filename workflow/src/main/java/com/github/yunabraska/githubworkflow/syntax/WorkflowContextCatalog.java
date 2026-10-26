/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.localize.LocalizeValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class WorkflowContextCatalog {
    public static final Pattern PATTERN_GITHUB_OUTPUT = Pattern.compile("(?:echo\\s+)?[\"']([A-Za-z_][A-Za-z0-9_-]*)=(.*?)[\"']\\s*>>\\s*\"?\\$\\w*:?\\{?GITHUB_OUTPUT\\}?\"?");
    public static final Pattern PATTERN_GITHUB_OUTPUT_TEE = Pattern.compile("(?:echo\\s+)?[\"']([A-Za-z_][A-Za-z0-9_-]*)=(.*?)[\"']\\s*\\|\\s*tee\\s+(?:-[A-Za-z]+\\s+)*.*\\$\\w*:?\\{?GITHUB_OUTPUT\\}?");
    public static final Pattern PATTERN_GITHUB_OUTPUT_GROUP = Pattern.compile("(?m)^\\s*\\{\\s*\\R([\\s\\S]*?)^\\s*}\\s*>>\\s*\"?\\$\\w*:?\\{?GITHUB_OUTPUT\\}?\"?");
    public static final Pattern PATTERN_SHELL_OUTPUT_ASSIGNMENT = Pattern.compile("(?m)^\\s*(?:echo\\s+)?[\"']([A-Za-z_][A-Za-z0-9_-]*)=(.*?)[\"']\\s*;?\\s*$");
    public static final Pattern PATTERN_GITHUB_ENV = Pattern.compile("(?:echo\\s+)?[\"']([A-Za-z_][A-Za-z0-9_-]*)=(.*?)[\"']\\s*>>\\s*\"?\\$\\w*:?\\{?GITHUB_ENV\\}?\"?");
    public static final Pattern PATTERN_GITHUB_OUTPUT_MULTILINE = Pattern.compile("(?:echo\\s+)?[\"']?([A-Za-z_][A-Za-z0-9_-]*)<<[^\"'\\r\\n]+[\"']?");
    public static final Pattern PATTERN_GITHUB_ENV_MULTILINE = Pattern.compile("(?:echo\\s+)?[\"']?([A-Za-z_][A-Za-z0-9_-]*)<<[^\"'\\r\\n]+[\"']?");
    public static final long CACHE_ONE_DAY = 24L * 60 * 60 * 1000;
    public static final String FIELD_ON = "on";
    public static final String FIELD_IF = "if";
    public static final String FIELD_ID = "id";
    public static final String FIELD_ENVS = "env";
    public static final String FIELD_RUN = "run";
    public static final String FIELD_RUNS = "runs";
    public static final String FIELD_SHELL = "shell";
    public static final String FIELD_JOB = "job";
    public static final String FIELD_JOBS = "jobs";
    public static final String FIELD_MATRIX = "matrix";
    public static final String FIELD_STRATEGY = "strategy";
    public static final String FIELD_VARS = "vars";
    public static final String FIELD_WITH = "with";
    public static final String FIELD_USES = "uses";
    public static final String FIELD_NEEDS = "needs";
    public static final String FIELD_STEPS = "steps";
    public static final String FIELD_SERVICES = "services";
    public static final String FIELD_RUNNER = "runner";
    public static final String FIELD_GITHUB = "github";
    public static final String FIELD_DEFAULT = "${{}}";
    public static final String FIELD_INPUTS = "inputs";
    public static final String FIELD_OUTPUTS = "outputs";
    public static final String FIELD_SECRETS = "secrets";
    public static final String FIELD_PORTS = "ports";
    public static final String FIELD_RESULT = "result";
    public static final String FIELD_CONCLUSION = "conclusion";
    public static final String FIELD_OUTCOME = "outcome";
    public static final Map<String, Supplier<Map<String, String>>> DEFAULT_VALUE_MAP = initProcessorMap();
    private static final String DOCS_ROOT = "/github-workflow/docs/";
    private static final List<String> GITHUB_EXPRESSION_FUNCTIONS = List.of(
        "contains()",
        "startsWith()",
        "endsWith()",
        "format()",
        "join()",
        "toJSON()",
        "fromJSON()",
        "hashFiles()",
        "success()",
        "always()",
        "cancelled()",
        "failure()"
    );

    private static Map<String, Supplier<Map<String, String>>> initProcessorMap() {
        Map<String, Supplier<Map<String, String>>> result = new LinkedHashMap<>();
        result.put(FIELD_GITHUB, WorkflowContextCatalog::getGitHubContextEnvs);
        result.put(FIELD_JOB, WorkflowContextCatalog::getJobItems);
        result.put(FIELD_ENVS, WorkflowContextCatalog::getGitHubEnvs);
        result.put(FIELD_RUNNER, WorkflowContextCatalog::getRunnerItems);
        result.put(FIELD_STRATEGY, WorkflowContextCatalog::getStrategyItems);
        result.put(FIELD_DEFAULT, WorkflowContextCatalog::getCaretBracketItems);
        return result;
    }

    /**
     * Returns built-in runner environment variables.
     *
     * @return immutable map keyed by environment variable name with descriptions
     */
    public static Map<String, String> defaultEnvs() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(getGitHubEnvs()));
    }

    /**
     * Returns root expression contexts and functions.
     *
     * @return immutable map keyed by expression token with localized descriptions
     */
    public static Map<String, String> expressionRoots() {
        Map<String, String> result = new LinkedHashMap<>(getCaretBracketItems());
        String description = message(GitHubWorkflowLocalize.completionExpressionfunction());
        GITHUB_EXPRESSION_FUNCTIONS.forEach(function -> result.put(function, description));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns GitHub expression function names without parentheses.
     *
     * @return immutable list of known GitHub expression function names
     */
    public static List<String> githubExpressionFunctionNames() {
        return GITHUB_EXPRESSION_FUNCTIONS.stream()
            .map(function -> function.substring(0, function.indexOf('(')))
            .toList();
    }

    /**
     * Returns supported shell completion values with localized descriptions.
     *
     * @return immutable map keyed by shell name
     */
    public static Map<String, String> shells() {
        return initShells();
    }

    private static Map<String, String> initShells() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("bash", message(GitHubWorkflowLocalize.completionShellBash()));
        result.put("sh", message(GitHubWorkflowLocalize.completionShellSh()));
        result.put("pwsh", message(GitHubWorkflowLocalize.completionShellPwsh()));
        result.put("powershell", message(GitHubWorkflowLocalize.completionShellPowershell()));
        result.put("cmd", message(GitHubWorkflowLocalize.completionShellCmd()));
        result.put("python", message(GitHubWorkflowLocalize.completionShellPython()));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> getRunnerItems() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("name", message(GitHubWorkflowLocalize.completionRunnerName()));
        result.put("os", message(GitHubWorkflowLocalize.completionRunnerOs()));
        result.put("arch", message(GitHubWorkflowLocalize.completionRunnerArch()));
        result.put("temp", message(GitHubWorkflowLocalize.completionRunnerTemp()));
        result.put("tool_cache", message(GitHubWorkflowLocalize.completionRunnerToolcache()));
        result.put("debug", message(GitHubWorkflowLocalize.completionRunnerDebug()));
        result.put("environment", message(GitHubWorkflowLocalize.completionRunnerEnvironment()));
        return result;
    }

    private static Map<String, String> getJobItems() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("status", message(GitHubWorkflowLocalize.completionJobStatus()));
        result.put("check_run_id", message(GitHubWorkflowLocalize.completionJobCheckrunid()));
        result.put("container", message(GitHubWorkflowLocalize.completionJobContainer()));
        result.put("services", message(GitHubWorkflowLocalize.completionJobServices()));
        result.put("workflow_ref", message(GitHubWorkflowLocalize.completionJobWorkflowref()));
        result.put("workflow_sha", message(GitHubWorkflowLocalize.completionJobWorkflowsha()));
        result.put("workflow_repository", message(GitHubWorkflowLocalize.completionJobWorkflowrepository()));
        result.put("workflow_file_path", message(GitHubWorkflowLocalize.completionJobWorkflowfilepath()));
        return result;
    }

    private static Map<String, String> getStrategyItems() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("fail-fast", message(GitHubWorkflowLocalize.completionStrategyFailfast()));
        result.put("job-index", message(GitHubWorkflowLocalize.completionStrategyJobindex()));
        result.put("job-total", message(GitHubWorkflowLocalize.completionStrategyJobtotal()));
        result.put("max-parallel", message(GitHubWorkflowLocalize.completionStrategyMaxparallel()));
        return result;
    }

    private static Map<String, String> getCaretBracketItems() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(FIELD_INPUTS, message(GitHubWorkflowLocalize.completionContextInputs()));
        result.put(FIELD_SECRETS, message(GitHubWorkflowLocalize.completionContextSecrets()));
        result.put(FIELD_JOB, message(GitHubWorkflowLocalize.completionContextJob()));
        result.put(FIELD_JOBS, message(GitHubWorkflowLocalize.completionContextJobs()));
        result.put(FIELD_MATRIX, message(GitHubWorkflowLocalize.completionContextMatrix()));
        result.put(FIELD_STRATEGY, message(GitHubWorkflowLocalize.completionContextStrategy()));
        result.put(FIELD_STEPS, message(GitHubWorkflowLocalize.completionContextSteps()));
        result.put(FIELD_ENVS, message(GitHubWorkflowLocalize.completionContextEnv()));
        result.put(FIELD_VARS, message(GitHubWorkflowLocalize.completionContextVars()));
        result.put(FIELD_NEEDS, message(GitHubWorkflowLocalize.completionContextNeeds()));
        result.put(FIELD_GITHUB, message(GitHubWorkflowLocalize.completionContextGithub()));
        result.put(FIELD_RUNNER, message(GitHubWorkflowLocalize.completionContextRunner()));
        return result;
    }

    private static String message(LocalizeValue value) {
        return value.get();
    }

    private static Map<String, String> getGitHubContextEnvs() {
        return loadGeneratedItems(DOCS_ROOT + "github-context.tsv");
    }

    private static Map<String, String> getGitHubEnvs() {
        return loadGeneratedItems(DOCS_ROOT + "default-env.tsv");
    }

    private static Map<String, String> loadGeneratedItems(String resourcePath) {
        try (InputStream stream = WorkflowContextCatalog.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return Map.of();
            }
            return readGeneratedItems(stream);
        }
        catch (IOException ignored) {
            return Map.of();
        }
    }

    private static Map<String, String> readGeneratedItems(InputStream stream) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\t", 2);
                if (parts.length == 2 && !parts[0].isBlank()) {
                    result.put(parts[0], parts[1]);
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private WorkflowContextCatalog() {
    }
}
