/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.run;

import com.github.yunabraska.githubworkflow.git.RemoteActionProviders;
import com.github.yunabraska.githubworkflow.state.GitHubWorkflowNotificationGroupContributor;
import consulo.accountManagement.Account;
import consulo.application.concurrent.ApplicationConcurrency;
import consulo.container.boot.ContainerPathManager;
import consulo.execution.executor.Executor;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.process.BaseProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationService;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * IDE process facade that dispatches, polls, logs, and cancels a remote GitHub workflow run.
 */
public class WorkflowRunProcessHandler extends BaseProcessHandler {
    private static final Logger LOG = Logger.getInstance(WorkflowRunProcessHandler.class);

    private final WorkflowRun.Request myRequest;
    private final Project myProject;
    private final PollSettings myPollSettings;
    private JobConsole myJobConsole = JobConsole.none();
    private final AtomicReference<@Nullable WorkflowRun> myClient = new AtomicReference<>();
    private final AtomicBoolean myStopping = new AtomicBoolean(false);
    private final AtomicBoolean myTerminated = new AtomicBoolean(false);
    private final AtomicBoolean myDeleteRequested = new AtomicBoolean(false);
    private final AtomicBoolean myRerunAllRequested = new AtomicBoolean(false);
    private final AtomicBoolean myRerunFailedRequested = new AtomicBoolean(false);
    private final AtomicInteger myArtifactAvailability = new AtomicInteger(-1);
    private final AtomicLong myRunId = new AtomicLong(-1);
    private final AtomicReference<String> myRunHtmlUrl = new AtomicReference<>("");
    private final AtomicReference<@Nullable Future<?>> myTask = new AtomicReference<>();
    private final Semaphore myWakeUp = new Semaphore(0);

    WorkflowRunProcessHandler(Project project, WorkflowRun.Request request, @Nullable Executor executor) {
        this(project, request, PollSettings.defaults());
        myJobConsole = new WorkflowRunView(project, executor, this, request);
    }

    WorkflowRunProcessHandler(Project project, WorkflowRun.Request request, PollSettings pollSettings) {
        myProject = project;
        myRequest = request;
        myPollSettings = pollSettings;
    }

    WorkflowRunProcessHandler(Project project, WorkflowRun.Request request, PollSettings pollSettings, @Nullable JobConsole jobConsole) {
        this(project, request, pollSettings);
        myJobConsole = jobConsole == null ? JobConsole.none() : jobConsole;
    }

    @Override
    public void startNotify() {
        super.startNotify();
        WorkflowRunTracker.getInstance(myProject).register(myRequest.workflowPath(), this);
        myJobConsole.start();
        myTask.set(executorService().submit(this::runWorkflow));
    }

    @Override
    protected void destroyProcessImpl() {
        if (!myStopping.compareAndSet(false, true)) {
            return;
        }
        long id = myRunId.get();
        stdout(id > 0
            ? GitHubWorkflowLocalize.workflowRunCancelRequested(id).get() + "\n"
            : GitHubWorkflowLocalize.workflowRunStopBeforeId().get() + "\n");
        Future<?> runningTask = myTask.get();
        if (runningTask != null) {
            runningTask.cancel(true);
        }
        executorService().execute(() -> cancelRemoteRun(id));
    }

    private void cancelRemoteRun(long id) {
        WorkflowRun client = myClient.get();
        if (id > 0 && client != null) {
            try {
                client.cancel(myRequest, id);
                stdout(GitHubWorkflowLocalize.workflowRunCancelDone(id).get() + "\n");
            }
            catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                stderr(GitHubWorkflowLocalize.workflowRunCancelFailed(exception.getMessage()).get() + "\n");
            }
        }
        terminate(1, "cancelled");
    }

    @Override
    protected void detachProcessImpl() {
        myStopping.set(true);
        if (myTerminated.compareAndSet(false, true)) {
            WorkflowRunTracker.getInstance(myProject).unregister(myRequest.workflowPath(), this);
            myJobConsole.close();
            notifyProcessDetached();
        }
    }

    @Override
    public boolean detachIsDefault() {
        return false;
    }

    @Override
    public @Nullable OutputStream getProcessInput() {
        return null;
    }

    private void runWorkflow() {
        try {
            WorkflowRun client = connect();
            if (client == null) {
                terminate(1, "failure");
                return;
            }
            terminate(runFromTrigger(client));
        }
        catch (IOException | RuntimeException exception) {
            if (exception instanceof WorkflowRun.WorkflowRunHttpException httpException && httpException.accountActionRecommended()) {
                WorkflowRun client = myClient.get();
                notifyAuthenticationHelp(client == null ? null : client.server());
            }
            if (!(exception instanceof IOException)) {
                LOG.error(exception);
            }
            stderr(exception.getMessage() + "\n");
            terminate(1, "failure");
        }
        catch (InterruptedException exception) {
            if (!myStopping.get()) {
                stderr(GitHubWorkflowLocalize.workflowRunInterrupted().get() + "\n");
            }
            terminate(1, myStopping.get() ? "cancelled" : "failure");
        }
    }

    private @Nullable WorkflowRun connect() {
        GithubServerPath server = WorkflowRunRepositories.server(myRequest.serverUrl()).orElse(null);
        if (server == null) {
            stderr(GitHubWorkflowLocalize.workflowRunErrorServer().get() + "\n");
            return null;
        }
        if (!hasText(myRequest.owner()) || !hasText(myRequest.repo())) {
            stderr(GitHubWorkflowLocalize.workflowRunRepositoryMissing(myRequest.workflowPath()).get() + "\n");
            return null;
        }
        Account account = WorkflowRunRepositories.findAccount(myProject, server).orElse(null);
        if (account == null) {
            stderr(GitHubWorkflowLocalize.workflowRunAuthMissing(server.toUrl(), myRequest.workflowPath()).get() + "\n");
            notifyAuthenticationHelp(server);
            return null;
        }
        WorkflowRun client = new WorkflowRun(GithubApiRequestExecutorFactory.getInstance().create(account, server), server);
        myClient.set(client);
        return client;
    }

    private RunOutcome runFromTrigger(WorkflowRun client) throws IOException, InterruptedException {
        long id = dispatchFromTrigger(client);
        if (id > 0) {
            myRunId.set(id);
        }
        return RunOutcome.from(poll(client, id), myStopping.get());
    }

    private long dispatchFromTrigger(WorkflowRun client) throws IOException, InterruptedException {
        stdout(dispatchMessage(client.server()) + "\n");
        Instant dispatchTime = Instant.now();
        return resolveRunId(client, client.dispatch(myRequest), dispatchTime);
    }

    private long resolveRunId(WorkflowRun client, WorkflowRun.DispatchResult dispatch, Instant dispatchTime) throws IOException, InterruptedException {
        if (hasText(dispatch.htmlUrl())) {
            myRunHtmlUrl.set(dispatch.htmlUrl());
            stdout(GitHubWorkflowLocalize.workflowRunLink(dispatch.htmlUrl()).get() + "\n");
        }
        if (dispatch.runId() > 0) {
            return dispatch.runId();
        }
        stdout(GitHubWorkflowLocalize.workflowRunDiscovery().get() + "\n");
        for (int attempt = 0; attempt < 12 && !myStopping.get(); attempt++) {
            Optional<WorkflowRun.RunStatus> latest = client.latestRun(myRequest, dispatchTime);
            if (latest.isPresent()) {
                WorkflowRun.RunStatus run = latest.get();
                if (hasText(run.htmlUrl())) {
                    myRunHtmlUrl.set(run.htmlUrl());
                    stdout(GitHubWorkflowLocalize.workflowRunLink(run.htmlUrl()).get() + "\n");
                }
                return run.runId();
            }
            TimeUnit.MILLISECONDS.sleep(myPollSettings.runDiscoveryMillis());
        }
        stdout(GitHubWorkflowLocalize.workflowRunDiscoveryNone().get() + "\n");
        return -1;
    }

    private String poll(WorkflowRun client, long id) throws IOException, InterruptedException {
        if (id <= 0) {
            return "";
        }
        WorkflowRun.RunStatus previous = new WorkflowRun.RunStatus(id, "", "", "");
        Map<Long, JobLogState> jobLogs = new LinkedHashMap<>();
        while (!myStopping.get()) {
            WorkflowRun.RunStatus status = client.status(myRequest, id);
            if (hasText(status.htmlUrl())) {
                myRunHtmlUrl.set(status.htmlUrl());
            }
            if (!status.status().equals(previous.status()) || !status.conclusion().equals(previous.conclusion())) {
                stdout(GitHubWorkflowLocalize.workflowRunStatus(status.status(), suffix(status.conclusion())).get() + "\n");
                previous = status;
            }
            if (status.completed()) {
                streamJobLogs(client, id, jobLogs, true);
                return hasText(status.conclusion()) ? status.conclusion() : "success";
            }
            streamJobLogs(client, id, jobLogs, false);
            myWakeUp.tryAcquire(myPollSettings.statusPollMillis(), TimeUnit.MILLISECONDS);
            myWakeUp.drainPermits();
        }
        return "cancelled";
    }

    private void streamJobLogs(WorkflowRun client, long id, Map<Long, JobLogState> jobLogs, boolean finalPass) throws IOException, InterruptedException {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (WorkflowRun.JobStatus job : client.jobs(myRequest, id)) {
            JobLogState state = jobLogs.computeIfAbsent(job.id(), ignored -> new JobLogState());
            if (state.changed(job)) {
                printJobHeader(job, state);
                state.seen(job, now);
                String status = GitHubWorkflowLocalize.workflowRunJobMain(
                    statePrefix(job),
                    job.name(),
                    job.status(),
                    suffix(job.conclusion()),
                    state.durationSuffix(now)
                ).get() + "\n";
                stdout(status);
                myJobConsole.jobStatus(job, GitHubWorkflowLocalize.workflowRunJobStatus(
                    statePrefix(job),
                    job.status(),
                    suffix(job.conclusion()),
                    state.durationSuffix(now)
                ).get() + "\n");
                state.status(job);
                changed = true;
            }
            if (shouldFetchLog(job, state, now, finalPass)) {
                fetchJobLog(client, job, state, now, finalPass);
            }
        }
        if (changed) {
            stdout(overview(jobLogs.values(), now));
        }
    }

    private boolean shouldFetchLog(WorkflowRun.JobStatus job, JobLogState state, long now, boolean finalPass) {
        return state.shouldFetchLog(job, now, finalPass, myPollSettings.logPollMillis());
    }

    private void fetchJobLog(WorkflowRun client, WorkflowRun.JobStatus job, JobLogState state, long now, boolean finalPass) throws InterruptedException {
        state.lastLogFetchMillis(now);
        try {
            String logs = client.jobLogs(myRequest, job.id());
            if (hasText(logs)) {
                printLogDelta(job, state, logs);
            }
            if (finalPass || "completed".equals(job.status())) {
                state.finalLogFetched(true);
            }
        }
        catch (IOException exception) {
            if (shouldDeferLiveLogFailure(exception, finalPass)) {
                if (!state.liveLogNoticeShown()) {
                    String notice = GitHubWorkflowLocalize.workflowRunLogsLater().get() + "\n";
                    if (!myJobConsole.jobStatus(job, notice)) {
                        stdout(GitHubWorkflowLocalize.workflowRunJobLogsLater(job.name(), notice).get());
                    }
                    state.liveLogNoticeShown(true);
                }
                state.nextLiveLogFetchMillis(now + myPollSettings.liveLogFailureRetryMillis());
                return;
            }
            if (finalPass || !state.logErrorShown()) {
                String message = GitHubWorkflowLocalize.workflowRunLogFailed(exception.getMessage()).get() + "\n";
                if (!myJobConsole.jobStderr(job, message)) {
                    stderr(GitHubWorkflowLocalize.workflowRunLogFailedJob(job.name(), exception.getMessage()).get() + "\n");
                }
                state.logErrorShown(true);
            }
        }
    }

    private void printLogDelta(WorkflowRun.JobStatus job, JobLogState state, String logs) {
        String delta = state.delta(logs);
        if (hasText(delta)) {
            if (!myJobConsole.jobLog(job, delta + "\n")) {
                String rendered = state.plain(delta + "\n");
                String fallbackText = "\n== " + job.name() + " ==\n" + rendered;
                stdout(fallbackText);
            }
        }
    }

    private void printJobHeader(WorkflowRun.JobStatus job, JobLogState state) {
        if (state.headerPrinted()) {
            return;
        }
        String url = hasText(job.htmlUrl()) ? GitHubWorkflowLocalize.workflowRunJobUrl(job.htmlUrl()).get() + "\n" : "";
        String header = GitHubWorkflowLocalize.workflowRunJobHeader(job.name()).get() + "\n" + url;
        stdout(header);
        myJobConsole.jobStatus(job, header);
        state.headerPrinted(true);
    }

    private String dispatchMessage(GithubServerPath server) {
        String[] verbs = GitHubWorkflowLocalize.workflowRunDispatchVerbs().get().split("\\|");
        String[] objects = GitHubWorkflowLocalize.workflowRunDispatchObjects().get().split("\\|");
        String verb = verbs[ThreadLocalRandom.current().nextInt(verbs.length)];
        String object = objects[ThreadLocalRandom.current().nextInt(objects.length)];
        return GitHubWorkflowLocalize.workflowRunDispatch(
            verb,
            object,
            myRequest.workflowPath(),
            workflowUrl(server),
            myRequest.repositorySlug(),
            myRequest.ref()
        ).get();
    }

    private String workflowUrl(GithubServerPath server) {
        return " (" + server.toUrl() + "/" + myRequest.owner() + "/" + myRequest.repo() + "/blob/" + myRequest.ref() + "/" + myRequest.workflowPath() + ")";
    }

    private static boolean shouldDeferLiveLogFailure(IOException exception, boolean finalPass) {
        return !finalPass && exception instanceof WorkflowRun.WorkflowRunHttpException;
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private ExecutorService executorService() {
        return myProject.getApplication().getInstance(ApplicationConcurrency.class).getExecutorService();
    }

    long runId() {
        return myRunId.get();
    }

    String runHtmlUrl() {
        return myRunHtmlUrl.get();
    }

    String repositorySlug() {
        return myRequest.repositorySlug();
    }

    void refreshNow() {
        myWakeUp.release();
    }

    void deleteRemoteRun() {
        long id = myRunId.get();
        WorkflowRun client = myClient.get();
        if (id <= 0 || client == null) {
            workflowStatus(GitHubWorkflowLocalize.workflowRunDeleteNorun().get() + "\n", true);
            return;
        }
        if (!myDeleteRequested.compareAndSet(false, true)) {
            return;
        }
        workflowStatus(GitHubWorkflowLocalize.workflowRunDeleteRequested(id).get() + "\n", false);
        inBackground(GitHubWorkflowLocalize::workflowRunDeleteFailed, exception -> {
            myJobConsole.runDeleteFailed(id);
            myDeleteRequested.set(false);
        }, () -> {
            client.delete(myRequest, id);
            workflowStatus(GitHubWorkflowLocalize.workflowRunDeleteDone(id).get() + "\n", false);
            myJobConsole.runDeleted(id);
        });
    }

    void rerunRemoteRun(boolean failedOnly) {
        long id = myRunId.get();
        WorkflowRun client = myClient.get();
        if (id <= 0 || client == null) {
            workflowStatus(GitHubWorkflowLocalize.workflowRunRerunNorun().get() + "\n", true);
            return;
        }
        AtomicBoolean gate = failedOnly ? myRerunFailedRequested : myRerunAllRequested;
        if (!gate.compareAndSet(false, true)) {
            return;
        }
        workflowStatus((failedOnly
            ? GitHubWorkflowLocalize.workflowRunRerunFailedRequested(id)
            : GitHubWorkflowLocalize.workflowRunRerunAllRequested(id)).get() + "\n", false);
        inBackground(GitHubWorkflowLocalize::workflowRunRerunFailed, ignored -> gate.set(false), () -> {
            client.rerun(myRequest, id, failedOnly);
            workflowStatus((failedOnly
                ? GitHubWorkflowLocalize.workflowRunRerunFailedDone(id)
                : GitHubWorkflowLocalize.workflowRunRerunAllDone(id)).get() + "\n", false);
            gate.set(false);
        });
    }

    int artifactAvailability() {
        return myArtifactAvailability.get();
    }

    void refreshArtifactAvailability(Consumer<Boolean> callback) {
        long id = myRunId.get();
        WorkflowRun client = myClient.get();
        if (id <= 0 || client == null) {
            myArtifactAvailability.set(0);
            callback.accept(false);
            return;
        }
        int known = myArtifactAvailability.get();
        if (known >= 0) {
            callback.accept(known == 1);
            return;
        }
        executorService().execute(() -> {
            boolean available = false;
            try {
                available = client.artifacts(myRequest, id).stream().anyMatch(artifact -> !artifact.expired());
            }
            catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
            myArtifactAvailability.set(available ? 1 : 0);
            callback.accept(available);
        });
    }

    void downloadJobLog(long jobId, String jobName) {
        long id = myRunId.get();
        WorkflowRun client = myClient.get();
        if (id <= 0 || jobId <= 0 || client == null) {
            workflowStatus(GitHubWorkflowLocalize.workflowRunDownloadNorun().get() + "\n", true);
            return;
        }
        workflowStatus(GitHubWorkflowLocalize.workflowRunDownloadLogRequested(jobName).get() + "\n", false);
        inBackground(GitHubWorkflowLocalize::workflowRunDownloadFailed, () -> {
            String log = client.jobLogs(myRequest, jobId);
            Path file = writeJobLog(id, jobId, jobName, log);
            workflowStatus(GitHubWorkflowLocalize.workflowRunDownloadLogDone(file).get() + "\n", false);
            revealFile(file);
        });
    }

    void downloadArtifacts() {
        long id = myRunId.get();
        WorkflowRun client = myClient.get();
        if (id <= 0 || client == null) {
            workflowStatus(GitHubWorkflowLocalize.workflowRunDownloadNorun().get() + "\n", true);
            return;
        }
        workflowStatus(GitHubWorkflowLocalize.workflowRunDownloadArtifactsRequested().get() + "\n", false);
        inBackground(GitHubWorkflowLocalize::workflowRunDownloadFailed, () -> {
            List<WorkflowRun.ArtifactStatus> artifacts = client.artifacts(myRequest, id);
            if (artifacts.isEmpty()) {
                myArtifactAvailability.set(0);
                workflowStatus(GitHubWorkflowLocalize.workflowRunDownloadArtifactsEmpty().get() + "\n", false);
                return;
            }
            Path lastFile = null;
            int downloaded = 0;
            for (WorkflowRun.ArtifactStatus artifact : artifacts) {
                if (artifact.expired()) {
                    workflowStatus(GitHubWorkflowLocalize.workflowRunDownloadArtifactExpired(artifact.name()).get() + "\n", false);
                    continue;
                }
                byte[] zip = client.artifactZip(myRequest, artifact.id());
                lastFile = writeArtifact(id, artifact, zip);
                downloaded++;
                workflowStatus(GitHubWorkflowLocalize.workflowRunDownloadArtifactDone(artifact.name(), lastFile).get() + "\n", false);
            }
            if (downloaded == 0) {
                myArtifactAvailability.set(0);
                workflowStatus(GitHubWorkflowLocalize.workflowRunDownloadArtifactsEmpty().get() + "\n", false);
                return;
            }
            myArtifactAvailability.set(1);
            if (lastFile != null) {
                revealDirectory(lastFile.getParent());
            }
        });
    }

    private void inBackground(FailureMessage failureMessage, RemoteWork work) {
        inBackground(failureMessage, ignored -> {
        }, work);
    }

    private void inBackground(FailureMessage failureMessage, Consumer<Exception> onFailure, RemoteWork work) {
        executorService().execute(() -> {
            try {
                work.run();
            }
            catch (IOException | InterruptedException exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (exception instanceof WorkflowRun.WorkflowRunHttpException httpException && httpException.accountActionRecommended()) {
                    WorkflowRun client = myClient.get();
                    notifyAuthenticationHelp(client == null ? null : client.server());
                }
                workflowStatus(failureMessage.message(StringUtil.notNullize(exception.getMessage())).get() + "\n", true);
                onFailure.accept(exception);
            }
        });
    }

    private void workflowStatus(String text, boolean error) {
        myJobConsole.workflowStatus(text, error);
        if (!isProcessTerminated()) {
            notifyTextAvailable(text, error ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT);
        }
    }

    private void terminate(int exitCode, String conclusion) {
        if (myTerminated.compareAndSet(false, true)) {
            WorkflowRunTracker.getInstance(myProject).unregister(myRequest.workflowPath(), this);
            myJobConsole.runFinished(myRunId.get(), conclusion);
            myJobConsole.close();
            notifyProcessTerminated(exitCode);
        }
    }

    private void terminate(RunOutcome outcome) {
        terminate(outcome.exitCode(), outcome.conclusion());
    }

    private void notifyAuthenticationHelp(@Nullable GithubServerPath server) {
        if (myProject.isDisposed()) {
            return;
        }
        String serverUrl = server == null ? myRequest.serverUrl() : server.toUrl();
        myProject.getApplication().getInstance(NotificationService.class)
            .newWarn(GitHubWorkflowNotificationGroupContributor.GROUP)
            .content(GitHubWorkflowLocalize.workflowRunNotificationAuth(serverUrl))
            .addClosingAction(GitHubWorkflowLocalize.workflowRunNotificationLogin(), () -> {
                if (!myProject.isDisposed()) {
                    RemoteActionProviders.requestLogin(myProject);
                }
            })
            .notify(myProject);
    }

    private void stdout(String text) {
        notifyTextAvailable(text, ProcessOutputTypes.STDOUT);
    }

    private void stderr(String text) {
        notifyTextAvailable(text, ProcessOutputTypes.STDERR);
    }

    private Path writeJobLog(long id, long jobId, String jobName, @Nullable String log) throws IOException {
        Path file = runDirectory(id).resolve(safeName(jobName) + "-" + jobId + ".log");
        Files.writeString(file, Optional.ofNullable(log).orElse(""), StandardCharsets.UTF_8);
        return file;
    }

    private Path writeArtifact(long id, WorkflowRun.ArtifactStatus artifact, byte @Nullable [] zip) throws IOException {
        Path file = runDirectory(id).resolve(safeName(artifact.name()) + "-" + artifact.id() + ".zip");
        Files.write(file, zip == null ? new byte[0] : zip);
        return file;
    }

    private Path runDirectory(long id) throws IOException {
        Path directory = ContainerPathManager.get().getSystemDir()
            .resolve("github-workflow-plugin")
            .resolve("downloads")
            .resolve(safeName(myRequest.repositorySlug()))
            .resolve("run-" + id);
        Files.createDirectories(directory);
        return directory;
    }

    private void revealFile(Path path) {
        if (!myProject.isDisposed()) {
            Platform.current().openFileInFileManager(path, myProject.getUIAccess());
        }
    }

    private void revealDirectory(@Nullable Path path) {
        if (path != null && !myProject.isDisposed()) {
            Platform.current().openDirectoryInFileManager(path, myProject.getUIAccess());
        }
    }

    private static String safeName(@Nullable String value) {
        String normalized = Optional.ofNullable(value)
            .filter(text -> !text.isBlank())
            .orElse("download")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]+", "-")
            .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "download" : normalized;
    }

    private static String overview(Collection<JobLogState> states, long now) {
        long total = states.size();
        long done = states.stream().filter(JobLogState::completed).count();
        long running = states.stream().filter(JobLogState::running).count();
        StringBuilder result = new StringBuilder()
            .append(GitHubWorkflowLocalize.workflowRunOverview(progressBar(done, total), done, total, running).get())
            .append("\n");
        int index = 0;
        for (JobLogState state : states) {
            boolean last = ++index == states.size();
            result.append(last ? "`-- " : "|-- ")
                .append(statePrefix(state))
                .append(" ")
                .append(state.name())
                .append(state.durationSuffix(now))
                .append("\n");
        }
        return result.toString();
    }

    private static String progressBar(long done, long total) {
        if (total <= 0) {
            return "[----------]";
        }
        int width = 10;
        int filled = (int) Math.min(width, Math.max(0, done * width / total));
        return "[" + "#".repeat(filled) + "-".repeat(width - filled) + "]";
    }

    private static String statePrefix(WorkflowRun.JobStatus job) {
        return statePrefix(job.status(), job.conclusion());
    }

    private static String statePrefix(JobLogState state) {
        return statePrefix(state.status(), state.conclusion());
    }

    private static String statePrefix(String status, String conclusion) {
        if ("completed".equals(status)) {
            return successful(conclusion)
                ? GitHubWorkflowLocalize.workflowRunStateOk().get()
                : GitHubWorkflowLocalize.workflowRunStateFail().get();
        }
        if ("in_progress".equals(status)) {
            return GitHubWorkflowLocalize.workflowRunStateRunning().get();
        }
        return GitHubWorkflowLocalize.workflowRunStateWaiting().get();
    }

    private static String suffix(@Nullable String conclusion) {
        return conclusion == null || conclusion.isBlank() ? "" : "/" + conclusion;
    }

    static boolean successful(@Nullable String conclusion) {
        return "success".equals(conclusion) || "skipped".equals(conclusion) || "neutral".equals(conclusion);
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0, TimeUnit.MILLISECONDS.toSeconds(millis));
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private static class JobLogState {
        private String myName = "job";
        private String myStatus = "";
        private String myConclusion = "";
        private long myFirstSeenMillis = 0;
        private long myStartedMillis = 0;
        private long myCompletedMillis = 0;
        private int myPrintedLength = 0;
        private long myLastLogFetchMillis = 0;
        private long myNextLiveLogFetchMillis = 0;
        private final WorkflowRunView.LogRenderer myFallbackRenderer = new WorkflowRunView.LogRenderer();
        private boolean myFinalLogFetched = false;
        private boolean myLogErrorShown = false;
        private boolean myHeaderPrinted = false;
        private boolean myLiveLogNoticeShown = false;

        private boolean changed(WorkflowRun.JobStatus job) {
            return !job.status().equals(myStatus) || !job.conclusion().equals(myConclusion);
        }

        private JobLogState seen(WorkflowRun.JobStatus job, long now) {
            if (myFirstSeenMillis == 0) {
                myFirstSeenMillis = now;
            }
            if ("in_progress".equals(job.status()) && myStartedMillis == 0) {
                myStartedMillis = now;
            }
            if ("completed".equals(job.status()) && myCompletedMillis == 0) {
                myCompletedMillis = now;
            }
            return this;
        }

        private JobLogState status(WorkflowRun.JobStatus job) {
            myStatus = job.status();
            myConclusion = job.conclusion();
            myName = job.name();
            return this;
        }

        private boolean shouldFetchLog(WorkflowRun.JobStatus job, long now, boolean finalPass, long logPollMillis) {
            if (!"in_progress".equals(job.status()) && !"completed".equals(job.status())) {
                return false;
            }
            if (finalPass || "completed".equals(job.status())) {
                return !myFinalLogFetched;
            }
            if (now < myNextLiveLogFetchMillis) {
                return false;
            }
            return now - myLastLogFetchMillis >= logPollMillis;
        }

        private String delta(String logs) {
            String text = logs.stripTrailing();
            if (text.length() <= myPrintedLength) {
                return "";
            }
            String result = text.substring(myPrintedLength).stripLeading();
            myPrintedLength = text.length();
            return result;
        }

        private String plain(String text) {
            return myFallbackRenderer.renderPlain(text);
        }

        private String durationSuffix(long now) {
            long start = myStartedMillis > 0 ? myStartedMillis : myFirstSeenMillis;
            long end = myCompletedMillis > 0 ? myCompletedMillis : now;
            if (start <= 0 || end < start) {
                return "";
            }
            return " " + formatDuration(end - start);
        }

        private boolean completed() {
            return "completed".equals(myStatus);
        }

        private boolean running() {
            return "in_progress".equals(myStatus);
        }

        private String name() {
            return myName;
        }

        private String status() {
            return myStatus;
        }

        private String conclusion() {
            return myConclusion;
        }

        private JobLogState lastLogFetchMillis(long value) {
            myLastLogFetchMillis = value;
            return this;
        }

        private JobLogState finalLogFetched(boolean value) {
            myFinalLogFetched = value;
            return this;
        }

        private boolean logErrorShown() {
            return myLogErrorShown;
        }

        private JobLogState logErrorShown(boolean value) {
            myLogErrorShown = value;
            return this;
        }

        private boolean headerPrinted() {
            return myHeaderPrinted;
        }

        private JobLogState headerPrinted(boolean value) {
            myHeaderPrinted = value;
            return this;
        }

        private boolean liveLogNoticeShown() {
            return myLiveLogNoticeShown;
        }

        private JobLogState liveLogNoticeShown(boolean value) {
            myLiveLogNoticeShown = value;
            return this;
        }

        private JobLogState nextLiveLogFetchMillis(long value) {
            myNextLiveLogFetchMillis = value;
            return this;
        }
    }

    record PollSettings(long statusPollMillis, long logPollMillis, long runDiscoveryMillis, long liveLogFailureRetryMillis) {
        PollSettings(long statusPollMillis, long logPollMillis, long runDiscoveryMillis) {
            this(statusPollMillis, logPollMillis, runDiscoveryMillis, Math.max(logPollMillis, 60_000));
        }

        private static PollSettings defaults() {
            return new PollSettings(10_000, 30_000, 2_000, 60_000);
        }
    }

    /**
     * Receives workflow job status and logs for the workflow build view.
     */
    interface JobConsole {
        boolean jobStatus(WorkflowRun.JobStatus job, String text);

        boolean jobStdout(WorkflowRun.JobStatus job, String text);

        boolean jobStderr(WorkflowRun.JobStatus job, String text);

        default boolean jobLog(WorkflowRun.JobStatus job, String text) {
            return jobStdout(job, text);
        }

        default void start() {
        }

        default void workflowStatus(String text, boolean error) {
        }

        default void runFinished(long runId, String conclusion) {
        }

        default void runDeleted(long runId) {
        }

        default void runDeleteFailed(long runId) {
        }

        default void close() {
        }

        static JobConsole none() {
            return new JobConsole() {
                @Override
                public boolean jobStatus(WorkflowRun.JobStatus job, String text) {
                    return false;
                }

                @Override
                public boolean jobStdout(WorkflowRun.JobStatus job, String text) {
                    return false;
                }

                @Override
                public boolean jobStderr(WorkflowRun.JobStatus job, String text) {
                    return false;
                }
            };
        }
    }

    private record RunOutcome(int exitCode, String conclusion) {
        private static RunOutcome from(String conclusion, boolean stopping) {
            String terminalConclusion = stopping
                ? "cancelled"
                : hasText(conclusion) ? conclusion : "success";
            return new RunOutcome(successful(terminalConclusion) ? 0 : 1, terminalConclusion);
        }
    }

    private interface RemoteWork {
        void run() throws IOException, InterruptedException;
    }

    private interface FailureMessage {
        LocalizeValue message(Object detail);
    }
}
