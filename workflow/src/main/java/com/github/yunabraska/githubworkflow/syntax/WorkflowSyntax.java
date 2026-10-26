/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.syntax;

import com.github.yunabraska.githubworkflow.git.WorkflowLocation;
import com.github.yunabraska.githubworkflow.model.GitHubSchemaProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.localize.LocalizeKey;
import consulo.localize.LocalizeValue;
import org.jetbrains.yaml.psi.YAMLKeyValue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.isChildOf;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.pathEndsWith;
import static com.github.yunabraska.githubworkflow.git.WorkflowLocation.pathMatches;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_INPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_JOBS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_MATRIX;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_ON;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_OUTPUTS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_RUN;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_SECRETS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_SERVICES;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_STEPS;
import static com.github.yunabraska.githubworkflow.syntax.WorkflowContextCatalog.FIELD_STRATEGY;

/**
 * GitHub Actions workflow syntax completion tables from the public workflow syntax reference.
 */
public class WorkflowSyntax {
    private static final String WORKFLOW_SYNTAX_RESOURCE = "/github-workflow/docs/workflow-syntax.tsv";
    static final List<JsonSchemaFileProvider> SCHEMA_FILE_PROVIDERS = Stream.<JsonSchemaFileProvider>of(
            new GitHubSchemaProvider("dependabot-2.0", "Dependabot", WorkflowYaml::isDependabotFile),
            new GitHubSchemaProvider("github-action", "GitHub Action", WorkflowYaml::isActionFile),
            new GitHubSchemaProvider("github-funding", "GitHub Funding", WorkflowYaml::isFoundingFile),
            new GitHubSchemaProvider("github-workflow", "GitHub Workflow", WorkflowYaml::isGithubWorkflowFile),
            new GitHubSchemaProvider("github-discussion", "GitHub Discussion", WorkflowYaml::isDiscussionFile),
            new GitHubSchemaProvider("github-issue-forms", "GitHub Issue Forms", WorkflowYaml::isIssueForms),
            new GitHubSchemaProvider("github-issue-config", "GitHub Workflow Issue Template configuration", WorkflowYaml::isIssueConfigFile),
            new GitHubSchemaProvider("github-workflow-template-properties", "GitHub Workflow Template Properties", WorkflowYaml::isWorkflowTemplatePropertiesFile)
        )
        .distinct()
        .toList();
    private static final List<SyntaxRule> KEY_RULES = List.of(
        rule((path, completion) -> path.isEmpty(), "top", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntoplevelkey),
        rule((path, completion) -> pathMatches(path, FIELD_ON), "event", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowneventkey),
        rule((path, completion) -> pathMatches(path, FIELD_ON, "workflow_dispatch"), "trigger.workflow_dispatch", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntriggerkey),
        rule((path, completion) -> pathMatches(path, FIELD_ON, "workflow_call"), "trigger.workflow_call", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntriggerkey),
        rule(
            (path, completion) -> isChildOf(path, FIELD_ON, "workflow_dispatch", FIELD_INPUTS)
                || isChildOf(path, FIELD_ON, "workflow_call", FIELD_INPUTS),
            (ValueProvider) ignored -> workflowInputPropertyKeys(),
            GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntriggerkey
        ),
        rule(
            (path, completion) -> isChildOf(path, FIELD_ON, "workflow_call", FIELD_OUTPUTS),
            (ValueProvider) ignored -> workflowOutputPropertyKeys(),
            GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntriggerkey
        ),
        rule(
            (path, completion) -> isChildOf(path, FIELD_ON, "workflow_call", FIELD_SECRETS),
            (ValueProvider) ignored -> workflowSecretPropertyKeys(),
            GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntriggerkey
        ),
        rule(
            (path, completion) -> pathMatches(path, FIELD_ON, "*"),
            (ValueProvider) path -> eventFilterKeysFor(path.get(path.size() - 1)),
            GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntriggerfilter
        ),
        rule((path, completion) -> pathEndsWith(path, "permissions"), "permission", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknownpermission),
        rule(
            (path, completion) -> pathMatches(path, "defaults", FIELD_RUN)
                || pathMatches(path, FIELD_JOBS, "*", "defaults", FIELD_RUN),
            "defaultsRun",
            GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntoplevelkey
        ),
        rule(
            (path, completion) -> pathMatches(path, "concurrency")
                || pathMatches(path, FIELD_JOBS, "*", "concurrency"),
            "concurrency",
            GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntoplevelkey
        ),
        rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", "environment"), "environment", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntoplevelkey),
        rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*"), "job", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknownjobkey),
        rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", FIELD_STRATEGY), "strategy", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntoplevelkey),
        rule((path, completion) -> completion && pathMatches(path, FIELD_JOBS, "*", FIELD_STRATEGY, FIELD_MATRIX), "matrix", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntoplevelkey),
        rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", "container"), "container", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntoplevelkey),
        rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", "container", "credentials"), "credentials", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntoplevelkey),
        rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", FIELD_SERVICES, "*"), "service", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntoplevelkey),
        rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", FIELD_SERVICES, "*", "credentials"), "credentials", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknowntoplevelkey),
        rule((path, completion) -> pathMatches(path, FIELD_JOBS, "*", FIELD_STEPS), "step", GitHubWorkflowLocalize::inspectionWorkflowSyntaxUnknownstepkey)
    );

    private WorkflowSyntax() {
    }

    public static List<JsonSchemaFileProvider> schemaFileProviders() {
        return SCHEMA_FILE_PROVIDERS;
    }

    static Map<String, String> topLevelKeys() {
        return table("top");
    }

    static Map<String, String> eventKeys() {
        return table("event");
    }

    static Map<String, String> eventFilterKeys() {
        return table("eventFilter");
    }

    /**
     * Returns trigger filter keys valid for an event.
     *
     * @param event trigger event name
     * @return localized filter keys keyed by YAML key name
     */
    public static Map<String, String> eventFilterKeysFor(String event) {
        Map<String, String> result = table("eventFilter." + event);
        return result.isEmpty() ? eventFilterKeys() : result;
    }

    /**
     * Returns structure completion keys for a YAML path.
     *
     * @param path YAML key path before the caret
     * @return localized completion keys when this plugin owns the path
     */
    public static Optional<Map<String, String>> completionKeysForPath(List<String> path) {
        return knownKeysForPath(path, true).map(KnownKeys::values);
    }

    public static Optional<String> descriptionForKey(YAMLKeyValue keyValue) {
        return WorkflowLocation.from(keyValue)
            .filter(WorkflowLocation::workflowFile)
            .flatMap(location -> knownKeysForPath(location.path(), true)
                .map(KnownKeys::values)
                .map(values -> values.get(location.keyValue().getKeyText())))
            .filter(value -> !value.isBlank());
    }

    /**
     * Returns validation keys for a YAML path.
     *
     * @param path YAML key path at the inspected element
     * @return allowed keys and diagnostic message when this plugin owns the path
     */
    public static Optional<KnownKeys> validationKeysForPath(List<String> path) {
        return knownKeysForPath(path, false);
    }

    private static Optional<KnownKeys> knownKeysForPath(List<String> path, boolean completion) {
        if (pathEndsWith(path, FIELD_ON, "workflow_dispatch", FIELD_INPUTS)
            || pathEndsWith(path, FIELD_ON, "workflow_call", FIELD_INPUTS)
            || pathEndsWith(path, FIELD_ON, "workflow_call", FIELD_OUTPUTS)
            || pathEndsWith(path, FIELD_ON, "workflow_call", FIELD_SECRETS)) {
            return Optional.empty();
        }
        return KEY_RULES.stream()
            .flatMap(rule -> rule.known(path, completion).stream())
            .findFirst();
    }

    /**
     * Returns documented activity types for a trigger event.
     *
     * @param event trigger event name
     * @return localized activity values keyed by YAML value
     */
    public static Map<String, String> eventActivityTypesFor(String event) {
        return table("activity." + event);
    }

    static Map<String, String> permissionScopes() {
        return table("permission");
    }

    static Map<String, String> permissionValues() {
        return table("permissionValue");
    }

    /**
     * Returns permission access values for a permission scope.
     *
     * @param permission permission scope name
     * @return localized access values keyed by YAML value
     */
    public static Map<String, String> permissionValuesFor(String permission) {
        Map<String, String> result = table("permissionValue." + permission);
        return result.isEmpty() ? permissionValues() : result;
    }

    public static Map<String, String> permissionShorthandValues() {
        return table("permissionShorthand");
    }

    static Map<String, String> jobKeys() {
        return table("job");
    }

    static Map<String, String> defaultsRunKeys() {
        return table("defaultsRun");
    }

    static Map<String, String> concurrencyKeys() {
        return table("concurrency");
    }

    static Map<String, String> environmentKeys() {
        return table("environment");
    }

    static Map<String, String> strategyKeys() {
        return table("strategy");
    }

    static Map<String, String> matrixKeys() {
        return table("matrix");
    }

    static Map<String, String> stepKeys() {
        return table("step");
    }

    static Map<String, String> containerKeys() {
        return table("container");
    }

    static Map<String, String> serviceKeys() {
        return table("service");
    }

    static Map<String, String> credentialsKeys() {
        return table("credentials");
    }

    static Map<String, String> workflowInputTypes() {
        return table("inputType.workflow_dispatch");
    }

    static Map<String, String> reusableWorkflowInputTypes() {
        return table("inputType.workflow_call");
    }

    public static Map<String, String> workflowInputTypesFor(String trigger) {
        return "workflow_call".equals(trigger) ? reusableWorkflowInputTypes() : workflowInputTypes();
    }

    static Map<String, String> workflowDispatchTriggerKeys() {
        return table("trigger.workflow_dispatch");
    }

    static Map<String, String> workflowCallTriggerKeys() {
        return table("trigger.workflow_call");
    }

    static Map<String, String> workflowInputPropertyKeys() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("description", GitHubWorkflowLocalize.documentationDescriptionLabel().get());
        result.put("type", GitHubWorkflowLocalize.documentationType("string | boolean | choice | number | environment").get());
        result.put("required", GitHubWorkflowLocalize.documentationRequired(true).get());
        result.put("default", GitHubWorkflowLocalize.documentationDefault("").get());
        result.put("options", GitHubWorkflowLocalize.documentationValueLabel().get());
        return Collections.unmodifiableMap(result);
    }

    static Map<String, String> workflowOutputPropertyKeys() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("description", GitHubWorkflowLocalize.documentationDescriptionLabel().get());
        result.put("value", GitHubWorkflowLocalize.documentationValueLabel().get());
        return Collections.unmodifiableMap(result);
    }

    static Map<String, String> workflowSecretPropertyKeys() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("description", GitHubWorkflowLocalize.documentationDescriptionLabel().get());
        result.put("required", GitHubWorkflowLocalize.documentationRequired(true).get());
        return Collections.unmodifiableMap(result);
    }

    public static Map<String, String> booleanValues() {
        return table("boolean");
    }

    public static Map<String, String> runnerLabels() {
        return table("runner");
    }

    /**
     * Returns schedule cron value completions.
     *
     * @return localized cron values keyed by YAML value
     */
    public static Map<String, String> cronValues() {
        return table("cron");
    }

    private static Map<String, String> table(String group) {
        Map<String, String> keys = Tables.DATA.getOrDefault(group, Collections.emptyMap());
        if (keys.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        keys.forEach((key, bundleKey) -> result.put(key, LocalizeKey.of(GitHubWorkflowLocalize.ID, bundleKey).getValue().get()));
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, Map<String, String>> loadTables() {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        try (BufferedReader reader = syntaxReader()) {
            String line = reader.readLine();
            int lineNumber = 1;
            while (line != null) {
                loadTableLine(result, line, lineNumber);
                line = reader.readLine();
                lineNumber++;
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + WORKFLOW_SYNTAX_RESOURCE, exception);
        }
        return immutableTables(result);
    }

    private static BufferedReader syntaxReader() {
        InputStream stream = WorkflowSyntax.class.getResourceAsStream(WORKFLOW_SYNTAX_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Missing " + WORKFLOW_SYNTAX_RESOURCE);
        }
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static void loadTableLine(Map<String, Map<String, String>> result, String rawLine, int lineNumber) {
        String line = rawLine.strip();
        if (line.isBlank() || line.startsWith("#")) {
            return;
        }
        String[] parts = rawLine.split("\t", 3);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new IllegalStateException("Invalid " + WORKFLOW_SYNTAX_RESOURCE + " line " + lineNumber);
        }
        result.computeIfAbsent(parts[0], ignored -> new LinkedHashMap<>())
            .put(parts[1], parts[2].strip());
    }

    private static Map<String, Map<String, String>> immutableTables(Map<String, Map<String, String>> source) {
        Map<String, Map<String, String>> tables = new LinkedHashMap<>();
        source.forEach((group, values) -> tables.put(group, Collections.unmodifiableMap(new LinkedHashMap<>(values))));
        return Collections.unmodifiableMap(tables);
    }

    private static class Tables {
        private static final Map<String, Map<String, String>> DATA = loadTables();

        private Tables() {
        }
    }

    private static SyntaxRule rule(PathPredicate predicate, String table, Function<Object, LocalizeValue> message) {
        return rule(predicate, (ValueProvider) ignored -> table(table), message);
    }

    private static SyntaxRule rule(PathPredicate predicate, ValueProvider values, Function<Object, LocalizeValue> message) {
        return new SyntaxRule(predicate, values, message);
    }

    @FunctionalInterface
    private interface PathPredicate {
        boolean matches(List<String> path, boolean completion);
    }

    @FunctionalInterface
    private interface ValueProvider {
        Map<String, String> values(List<String> path);
    }

    private record SyntaxRule(PathPredicate predicate, ValueProvider values, Function<Object, LocalizeValue> message) {
        Optional<KnownKeys> known(List<String> path, boolean completion) {
            return predicate.matches(path, completion)
                ? Optional.of(new KnownKeys(values.values(path), message))
                : Optional.empty();
        }
    }

    public record KnownKeys(Map<String, String> values, Function<Object, LocalizeValue> message) {
    }
}
