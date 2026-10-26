/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.run;

import consulo.application.progress.EmptyProgressIndicator;
import consulo.component.ProcessCanceledException;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.localize.LocalizeValue;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubRateLimitExceededException;
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Small GitHub Actions REST client for workflow dispatch, status polling, cancellation, and logs.
 */
public class WorkflowRun {
    private static final int MAX_INPUTS = 25;
    private static final int PAGE_SIZE = 100;
    private static final int DISCOVERY_PAGE_SIZE = 20;

    private final GithubApiRequestExecutor myExecutor;
    private final GithubServerPath myServer;

    public WorkflowRun(GithubApiRequestExecutor executor, GithubServerPath server) {
        myExecutor = executor;
        myServer = server;
    }

    public GithubServerPath server() {
        return myServer;
    }

    public DispatchResult dispatch(Request request) throws IOException, InterruptedException {
        JsonNode json = execute(
            WorkflowRunApiRequests.dispatch(myServer, request.owner(), request.repo(), workflowId(request.workflowPath()), dispatchBody(request)),
            GitHubWorkflowLocalize.workflowRunOperationDispatch()
        );
        return new DispatchResult(
            longValue(json, "workflow_run_id").orElse(-1L),
            stringValue(json, "run_url").orElse(""),
            stringValue(json, "html_url").orElse("")
        );
    }

    public RunStatus status(Request request, long runId) throws IOException, InterruptedException {
        JsonNode json = execute(
            WorkflowRunApiRequests.run(myServer, request.owner(), request.repo(), runId),
            GitHubWorkflowLocalize.workflowRunOperationStatus()
        );
        return runStatus(json, runId);
    }

    public boolean cancel(Request request, long runId) throws IOException, InterruptedException {
        execute(WorkflowRunApiRequests.cancel(myServer, request.owner(), request.repo(), runId), GitHubWorkflowLocalize.workflowRunOperationCancel());
        return true;
    }

    /**
     * Requests GitHub to re-run a completed workflow run.
     *
     * @param request    workflow repository context
     * @param runId      GitHub Actions run id
     * @param failedOnly whether only failed jobs should be re-run
     * @return whether GitHub accepted the re-run
     * @throws IOException          when GitHub rejects the request or the network call fails
     * @throws InterruptedException when the IDE cancels the remote call
     */
    public boolean rerun(Request request, long runId, boolean failedOnly) throws IOException, InterruptedException {
        execute(
            WorkflowRunApiRequests.rerun(myServer, request.owner(), request.repo(), runId, failedOnly),
            failedOnly ? GitHubWorkflowLocalize.workflowRunOperationRerunfailed() : GitHubWorkflowLocalize.workflowRunOperationRerun()
        );
        return true;
    }

    /**
     * Deletes one completed workflow run from the remote repository.
     *
     * @param request workflow repository context
     * @param runId   GitHub Actions run id
     * @return whether GitHub accepted the deletion
     * @throws IOException          when GitHub rejects the request or the network call fails
     * @throws InterruptedException when the IDE cancels the remote call
     */
    public boolean delete(Request request, long runId) throws IOException, InterruptedException {
        execute(WorkflowRunApiRequests.delete(myServer, request.owner(), request.repo(), runId), GitHubWorkflowLocalize.workflowRunOperationDelete());
        return true;
    }

    /**
     * Finds the most likely run created by a workflow dispatch when the dispatch response did not include a run id.
     *
     * @param request workflow repository and branch context
     * @return the closest matching workflow dispatch run, or an empty result when no safe candidate exists
     * @throws IOException          when the remote API rejects the request or the network call fails
     * @throws InterruptedException when the IDE cancels the remote call
     */
    public Optional<RunStatus> latestRun(Request request) throws IOException, InterruptedException {
        return latestRun(request, Instant.now());
    }

    /**
     * Finds the most likely run created near one dispatch timestamp.
     *
     * @param request      workflow repository and branch context
     * @param dispatchTime local timestamp captured immediately before dispatching the workflow
     * @return the closest same-workflow run near the dispatch timestamp, or an empty result when no candidate fits
     * @throws IOException          when the remote API rejects the request or the network call fails
     * @throws InterruptedException when the IDE cancels the remote call
     */
    public Optional<RunStatus> latestRun(Request request, @Nullable Instant dispatchTime) throws IOException, InterruptedException {
        JsonNode json = execute(
            WorkflowRunApiRequests.workflowRuns(myServer, request.owner(), request.repo(), workflowId(request.workflowPath()), request.ref(), DISCOVERY_PAGE_SIZE),
            GitHubWorkflowLocalize.workflowRunOperationDiscovery()
        );
        Instant baseTime = Optional.ofNullable(dispatchTime).orElse(Instant.EPOCH);
        Instant earliest = baseTime.minusSeconds(30);
        return objects(json, "workflow_runs")
            .map(WorkflowRun::runCandidate)
            .filter(candidate -> candidate.status().runId() >= 0)
            .filter(candidate -> candidate.timestamp().map(timestamp -> !timestamp.isBefore(earliest)).orElse(true))
            .min(runCandidateComparator(baseTime))
            .map(RunCandidate::status);
    }

    public String logs(Request request, long runId) throws IOException, InterruptedException {
        StringBuilder result = new StringBuilder();
        for (JobStatus job : jobs(request, runId)) {
            result.append("== ").append(job.name()).append(" [").append(job.status()).append(resultSuffix(job.conclusion())).append("]\n");
            String logs = jobLogs(request, job.id());
            if (hasText(logs)) {
                result.append(logs.stripTrailing()).append("\n");
            }
        }
        return result.toString();
    }

    /**
     * Lists the artifacts produced by one workflow run.
     *
     * @param request workflow repository context
     * @param runId   GitHub Actions run id
     * @return immutable list of artifacts known to GitHub for the run
     * @throws IOException          when GitHub rejects the request or the network call fails
     * @throws InterruptedException when the IDE cancels the remote call
     */
    public List<ArtifactStatus> artifacts(Request request, long runId) throws IOException, InterruptedException {
        JsonNode json = execute(
            WorkflowRunApiRequests.artifacts(myServer, request.owner(), request.repo(), runId, PAGE_SIZE),
            GitHubWorkflowLocalize.workflowRunOperationArtifacts()
        );
        return objects(json, "artifacts")
            .map(WorkflowRun::artifactStatus)
            .filter(artifact -> artifact.id() >= 0)
            .toList();
    }

    /**
     * Downloads one workflow artifact archive as bytes.
     *
     * @param request    workflow repository context
     * @param artifactId GitHub Actions artifact id
     * @return zip archive bytes
     * @throws IOException          when GitHub rejects the request or the network call fails
     * @throws InterruptedException when the IDE cancels the remote call
     */
    public byte[] artifactZip(Request request, long artifactId) throws IOException, InterruptedException {
        return execute(
            WorkflowRunApiRequests.artifactZip(myServer, request.owner(), request.repo(), artifactId),
            GitHubWorkflowLocalize.workflowRunOperationArtifactdownload()
        );
    }

    public List<JobStatus> jobs(Request request, long runId) throws IOException, InterruptedException {
        JsonNode json = execute(
            WorkflowRunApiRequests.jobs(myServer, request.owner(), request.repo(), runId, PAGE_SIZE),
            GitHubWorkflowLocalize.workflowRunOperationJobs()
        );
        return objects(json, "jobs")
            .map(WorkflowRun::jobStatus)
            .filter(job -> job.id() >= 0)
            .toList();
    }

    public String jobLogs(Request request, long jobId) throws IOException, InterruptedException {
        return execute(
            WorkflowRunApiRequests.jobLogs(myServer, request.owner(), request.repo(), jobId),
            GitHubWorkflowLocalize.workflowRunOperationJoblogs()
        );
    }

    private <T> T execute(GithubApiRequest<T> request, LocalizeValue operation) throws IOException, InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        try {
            return myExecutor.execute(new EmptyProgressIndicator(), request);
        }
        catch (ProcessCanceledException e) {
            throw new InterruptedException();
        }
        catch (GithubRateLimitExceededException e) {
            throw failure(operation, e, 429, true);
        }
        catch (GithubAuthenticationException e) {
            throw failure(operation, e, 401, !mustHaveAdminRights(e.getMessage()));
        }
        catch (GithubStatusCodeException e) {
            throw failure(operation, e, e.getStatusCode(), e.getStatusCode() == 429);
        }
    }

    private static WorkflowRunHttpException failure(LocalizeValue operation, IOException cause, int statusCode, boolean accountActionRecommended) {
        String message = Optional.ofNullable(cause.getMessage()).orElse(String.valueOf(statusCode));
        return new WorkflowRunHttpException(
            GitHubWorkflowLocalize.workflowRunHttpFailed(operation.get(), message).get(),
            statusCode,
            message,
            accountActionRecommended,
            cause
        );
    }

    private static boolean mustHaveAdminRights(@Nullable String body) {
        return Optional.ofNullable(body)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .filter(value -> value.contains("must have admin rights"))
            .isPresent();
    }

    private static String workflowId(@Nullable String workflowPath) {
        String normalized = Optional.ofNullable(workflowPath).orElse("").replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private static Map<String, Object> dispatchBody(Request request) {
        Map<String, String> inputs = new LinkedHashMap<>();
        request.inputs().entrySet().stream()
            .filter(entry -> hasText(entry.getKey()))
            .limit(MAX_INPUTS)
            .forEach(entry -> inputs.put(entry.getKey(), Optional.ofNullable(entry.getValue()).orElse("")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ref", request.ref());
        if (!inputs.isEmpty()) {
            body.put("inputs", inputs);
        }
        return body;
    }

    private static String resultSuffix(String conclusion) {
        return hasText(conclusion) ? "/" + conclusion : "";
    }

    private static Stream<JsonNode> objects(JsonNode object, String name) {
        JsonNode array = object.get(name);
        if (array == null || !array.isArray()) {
            return Stream.empty();
        }
        List<JsonNode> result = new ArrayList<>();
        for (JsonNode element : array.values()) {
            if (element.isObject()) {
                result.add(element);
            }
        }
        return result.stream();
    }

    private static RunStatus runStatus(JsonNode object, long fallbackRunId) {
        return new RunStatus(
            longValue(object, "id").orElse(fallbackRunId),
            stringValue(object, "status").orElse("unknown"),
            stringValue(object, "conclusion").orElse(""),
            stringValue(object, "html_url").orElse("")
        );
    }

    private static RunCandidate runCandidate(JsonNode object) {
        return new RunCandidate(runStatus(object, -1L), runTimestamp(object));
    }

    private static Comparator<RunCandidate> runCandidateComparator(Instant dispatchTime) {
        return Comparator
            .comparingInt((RunCandidate candidate) -> candidate.timestamp().isPresent() ? 0 : 1)
            .thenComparingLong(candidate -> candidate.timestamp()
                .map(timestamp -> instantDistanceMillis(timestamp, dispatchTime))
                .orElse(Long.MAX_VALUE));
    }

    private static long instantDistanceMillis(Instant left, Instant right) {
        try {
            return Math.abs(Duration.between(left, right).toMillis());
        }
        catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static Optional<Instant> runTimestamp(JsonNode object) {
        return Stream.of("created_at", "run_started_at", "started_at", "updated_at")
            .flatMap(name -> stringValue(object, name).stream())
            .flatMap(value -> parseInstant(value).stream())
            .filter(timestamp -> timestamp.isAfter(Instant.parse("2000-01-01T00:00:00Z")))
            .findFirst();
    }

    private static Optional<Instant> parseInstant(String value) {
        try {
            return Optional.of(Instant.parse(value));
        }
        catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private static JobStatus jobStatus(JsonNode object) {
        return new JobStatus(
            longValue(object, "id").orElse(-1L),
            stringValue(object, "name").orElse("job"),
            stringValue(object, "status").orElse("unknown"),
            stringValue(object, "conclusion").orElse(""),
            stringValue(object, "html_url").orElse("")
        );
    }

    private static ArtifactStatus artifactStatus(JsonNode object) {
        return new ArtifactStatus(
            longValue(object, "id").orElse(-1L),
            stringValue(object, "name").orElse("artifact"),
            longValue(object, "size_in_bytes").orElse(0L),
            booleanValue(object, "expired").orElse(false),
            stringValue(object, "archive_download_url").orElse("")
        );
    }

    private static Optional<String> stringValue(JsonNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null || value.isNull() || value.isObject() || value.isArray()) {
            return Optional.empty();
        }
        return Optional.of(value.asString()).filter(WorkflowRun::hasText);
    }

    private static Optional<Long> longValue(JsonNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null || value.isNull() || value.isObject() || value.isArray()) {
            return Optional.empty();
        }
        long result = value.asLong(-1L);
        return result >= 0 ? Optional.of(result) : Optional.empty();
    }

    private static Optional<Boolean> booleanValue(JsonNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null || value.isNull() || value.isObject() || value.isArray()) {
            return Optional.empty();
        }
        return Optional.of(value.asBoolean(false));
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Request data needed to dispatch and observe one GitHub Actions workflow run.
     *
     * @param serverUrl    GitHub server URL
     * @param owner        repository owner
     * @param repo         repository name
     * @param workflowPath workflow file path or file name
     * @param ref          branch or tag used for workflow dispatch
     * @param inputs       workflow_dispatch input values
     */
    public record Request(
        String serverUrl,
        String owner,
        String repo,
        String workflowPath,
        String ref,
        Map<String, String> inputs
    ) {
        public Request {
            inputs = inputs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        }

        public String repositorySlug() {
            return owner + "/" + repo;
        }
    }

    public static class DispatchInputs {
        public List<Input> parse(@Nullable String yaml) {
            List<Line> lines = lines(yaml);
            Optional<Integer> workflowDispatchIndex = workflowDispatchIndex(lines);
            if (workflowDispatchIndex.isEmpty()) {
                return List.of();
            }
            int workflowDispatchIndent = lines.get(workflowDispatchIndex.get()).indent();
            Optional<Integer> inputsIndex = childIndex(lines, workflowDispatchIndex.get() + 1, workflowDispatchIndent, "inputs");
            if (inputsIndex.isEmpty()) {
                return List.of();
            }
            int inputsIndent = lines.get(inputsIndex.get()).indent();
            List<Input> result = new ArrayList<>();
            for (int index = inputsIndex.get() + 1; index < lines.size(); index++) {
                Line line = lines.get(index);
                if (line.indent() <= inputsIndent) {
                    break;
                }
                if (line.indent() == inputsIndent + 2 && line.keyValue().isPresent()) {
                    result.add(readInput(lines, index, inputsIndent + 2));
                }
            }
            return List.copyOf(result);
        }

        public boolean hasWorkflowDispatch(@Nullable String yaml) {
            return workflowDispatchIndex(lines(yaml)).isPresent();
        }

        public String defaultsText(@Nullable String yaml) {
            StringBuilder result = new StringBuilder();
            for (Input input : parse(yaml)) {
                result.append(input.name()).append("=").append(input.defaultValue()).append("\n");
            }
            return result.toString();
        }

        public Map<String, String> defaults(@Nullable String yaml) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Input input : parse(yaml)) {
                result.put(input.name(), input.defaultValue());
            }
            return result;
        }

        public static Map<String, String> parseKeyValueText(@Nullable String text) {
            LinkedHashMap<String, String> result = new LinkedHashMap<>();
            Optional.ofNullable(text).orElse("").lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .filter(line -> !line.startsWith("#"))
                .forEach(line -> {
                    int separator = line.indexOf('=');
                    if (separator > 0) {
                        result.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
                    }
                });
            return Collections.unmodifiableMap(result);
        }

        private static Input readInput(List<Line> lines, int inputIndex, int inputIndent) {
            String name = lines.get(inputIndex).keyValue().orElse("");
            String type = "string";
            String required = "false";
            String defaultValue = "";
            String description = "";
            List<String> options = new ArrayList<>();
            for (int index = inputIndex + 1; index < lines.size(); index++) {
                Line line = lines.get(index);
                if (line.indent() <= inputIndent) {
                    break;
                }
                if (line.indent() == inputIndent + 2) {
                    if ("type".equals(line.keyValue().orElse(""))) {
                        type = line.value();
                    }
                    else if ("required".equals(line.keyValue().orElse(""))) {
                        required = line.value();
                    }
                    else if ("default".equals(line.keyValue().orElse(""))) {
                        defaultValue = line.value();
                    }
                    else if ("description".equals(line.keyValue().orElse(""))) {
                        description = line.value();
                    }
                    else if ("options".equals(line.keyValue().orElse(""))) {
                        options.addAll(readOptions(lines, index, inputIndent + 2));
                    }
                }
            }
            return new Input(name, type, Boolean.parseBoolean(required), defaultValue, description, List.copyOf(options));
        }

        private static List<String> readOptions(List<Line> lines, int optionsIndex, int optionsIndent) {
            List<String> result = new ArrayList<>(inlineOptions(lines.get(optionsIndex).value()));
            for (int index = optionsIndex + 1; index < lines.size(); index++) {
                Line line = lines.get(index);
                if (line.indent() <= optionsIndent) {
                    break;
                }
                if (line.content().startsWith("- ")) {
                    result.add(stripQuotes(line.content().substring(2).trim()));
                }
            }
            return List.copyOf(result);
        }

        private static List<String> inlineOptions(@Nullable String value) {
            String trimmed = value == null ? "" : value.trim();
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
                return List.of();
            }
            String body = trimmed.substring(1, trimmed.length() - 1);
            if (body.isBlank()) {
                return List.of();
            }
            return splitInlineList(body).stream()
                .filter(option -> !option.isBlank())
                .map(DispatchInputs::stripQuotes)
                .toList();
        }

        private static List<String> splitInlineList(String body) {
            List<String> result = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            char quote = 0;
            for (int index = 0; index < body.length(); index++) {
                char character = body.charAt(index);
                if (quote != 0) {
                    current.append(character);
                    if (character == quote) {
                        quote = 0;
                    }
                }
                else if (character == '\'' || character == '"') {
                    quote = character;
                    current.append(character);
                }
                else if (character == ',') {
                    result.add(current.toString().trim());
                    current.setLength(0);
                }
                else {
                    current.append(character);
                }
            }
            result.add(current.toString().trim());
            return List.copyOf(result);
        }

        private static Optional<Integer> workflowDispatchIndex(List<Line> lines) {
            for (int index = 0; index < lines.size(); index++) {
                Line line = lines.get(index);
                if ("workflow_dispatch".equals(line.keyValue().orElse(""))
                    || "on".equals(line.keyValue().orElse("")) && "workflow_dispatch".equals(line.value())) {
                    return Optional.of(index);
                }
                if (line.content().equals("- workflow_dispatch")) {
                    return Optional.of(index);
                }
            }
            return Optional.empty();
        }

        private static Optional<Integer> childIndex(List<Line> lines, int start, int parentIndent, String key) {
            for (int index = start; index < lines.size(); index++) {
                Line line = lines.get(index);
                if (line.indent() <= parentIndent) {
                    break;
                }
                if (key.equals(line.keyValue().orElse(""))) {
                    return Optional.of(index);
                }
            }
            return Optional.empty();
        }

        private static List<Line> lines(@Nullable String yaml) {
            List<Line> result = new ArrayList<>();
            Optional.ofNullable(yaml).orElse("").lines()
                .map(DispatchInputs::line)
                .filter(line -> !line.content().isBlank())
                .filter(line -> !line.content().startsWith("#"))
                .forEach(result::add);
            return result;
        }

        private static Line line(String raw) {
            int indent = 0;
            while (indent < raw.length() && raw.charAt(indent) == ' ') {
                indent++;
            }
            String content = raw.substring(indent).trim();
            int separator = content.indexOf(':');
            if (separator < 0) {
                return new Line(indent, content, "", "");
            }
            String key = content.substring(0, separator).trim();
            String value = stripQuotes(content.substring(separator + 1).trim());
            return new Line(indent, content, key, value);
        }

        private static String stripQuotes(String value) {
            if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }

        public record Input(String name, String type, boolean required, String defaultValue, String description, List<String> options) {
            public Input(String name, String type, boolean required, String defaultValue, String description) {
                this(name, type, required, defaultValue, description, List.of());
            }

            public Input {
                options = options == null ? List.of() : List.copyOf(options);
            }
        }

        private record Line(int indent, String content, String key, String value) {
            Optional<String> keyValue() {
                return key.isBlank() ? Optional.empty() : Optional.of(key);
            }
        }
    }

    public record DispatchResult(long runId, String runUrl, String htmlUrl) {
    }

    public record RunStatus(long runId, String status, String conclusion, String htmlUrl) {
        public boolean completed() {
            return "completed".equals(status);
        }
    }

    private record RunCandidate(RunStatus status, Optional<Instant> timestamp) {
    }

    public record JobStatus(long id, String name, String status, String conclusion, String htmlUrl) {
    }

    public record ArtifactStatus(long id, String name, long sizeInBytes, boolean expired, String archiveDownloadUrl) {
    }

    public static class WorkflowRunHttpException extends IOException {
        private final int myStatusCode;
        private final String myBody;
        private final boolean myAccountActionRecommended;

        public WorkflowRunHttpException(String message, int statusCode, String body, boolean accountActionRecommended, @Nullable Throwable cause) {
            super(message, cause);
            myStatusCode = statusCode;
            myBody = body;
            myAccountActionRecommended = accountActionRecommended;
        }

        public int statusCode() {
            return myStatusCode;
        }

        public String body() {
            return myBody;
        }

        public boolean accountActionRecommended() {
            return myAccountActionRecommended;
        }
    }
}
