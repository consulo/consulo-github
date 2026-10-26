/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.run;

import consulo.build.ui.BuildViewManager;
import consulo.build.ui.DefaultBuildDescriptor;
import consulo.build.ui.ExecutionNode;
import consulo.build.ui.progress.BuildProgress;
import consulo.build.ui.progress.BuildProgressDescriptor;
import consulo.execution.executor.Executor;
import consulo.execution.ui.RunContentDescriptor;
import consulo.execution.ui.RunContentManager;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.Platform;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.process.ProcessOutputTypes;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.Presentation;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shows a workflow run in the build view: workflow, job groups and jobs, each job with its own log.
 */
class WorkflowRunView implements WorkflowRunProcessHandler.JobConsole {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_GRAY = "\u001B[90m";

    private final Project myProject;
    private final @Nullable Executor myExecutor;
    private final WorkflowRunProcessHandler myProcessHandler;
    private final WorkflowRun.Request myRequest;
    private final Object myLock = new Object();
    private final Map<Long, JobNode> myJobs = new LinkedHashMap<>();
    private final Map<String, GroupNode> myGroups = new LinkedHashMap<>();
    private final AtomicBoolean myClosed = new AtomicBoolean(false);
    private final AtomicBoolean myDeleteInProgress = new AtomicBoolean(false);
    private final ProcessListener myProcessListener = new ProcessListener() {
        @Override
        public void onTextAvailable(ProcessEvent event, Key outputType) {
            rootOutput(event.getText(), !ProcessOutputTypes.STDERR.equals(outputType));
        }
    };
    private @Nullable BuildProgress<BuildProgressDescriptor> myRoot;
    private volatile long myTerminalRunId = -1;
    private volatile String myTerminalConclusion = "";
    private volatile boolean myAnyFailed;

    WorkflowRunView(Project project, @Nullable Executor executor, WorkflowRunProcessHandler processHandler, WorkflowRun.Request request) {
        myProject = project;
        myExecutor = executor;
        myProcessHandler = processHandler;
        myRequest = request;
    }

    @Override
    public void start() {
        if (myExecutor == null || myProject.isDisposed()) {
            return;
        }
        String fileName = Path.of(myRequest.workflowPath()).getFileName().toString();
        DefaultBuildDescriptor descriptor = new DefaultBuildDescriptor(
            new Object(),
            GitHubWorkflowLocalize.workflowRunConfigurationName(fileName),
            Objects.requireNonNullElse(myProject.getBasePath(), ""),
            System.currentTimeMillis()
        );
        descriptor.setActivateToolWindowWhenAdded(true);
        descriptor.withActions(runToolbarActions());
        descriptor.withContextAction(SaveJobLogAction::new);
        synchronized (myLock) {
            BuildProgress<BuildProgressDescriptor> root = BuildViewManager.getInstance(myProject).createBuildProgress();
            root.start(BuildProgressDescriptor.of(descriptor));
            myRoot = root;
        }
        myProcessHandler.addProcessListener(myProcessListener);
    }

    @Override
    public boolean jobStatus(WorkflowRun.JobStatus job, String text) {
        return print(job, text, LogRenderer.Kind.SYSTEM);
    }

    @Override
    public boolean jobStdout(WorkflowRun.JobStatus job, String text) {
        return print(job, text, LogRenderer.Kind.NORMAL);
    }

    @Override
    public boolean jobStderr(WorkflowRun.JobStatus job, String text) {
        return print(job, text, LogRenderer.Kind.ERROR);
    }

    @Override
    public boolean jobLog(WorkflowRun.JobStatus job, String text) {
        if (job.id() < 0) {
            return false;
        }
        synchronized (myLock) {
            JobNode node = jobNode(job);
            if (node == null) {
                return false;
            }
            for (LogRenderer.Segment segment : node.myLogRenderer.render(text)) {
                output(node.myProgress, segment.text(), segment.kind());
            }
            return true;
        }
    }

    @Override
    public void workflowStatus(String text, boolean error) {
        if (myProcessHandler.isProcessTerminated()) {
            rootOutput(text, !error);
        }
    }

    @Override
    public void runFinished(long runId, String conclusion) {
        String terminalConclusion = normalizeConclusion(conclusion);
        synchronized (myLock) {
            for (JobNode node : myJobs.values()) {
                node.finish(terminalConclusion);
            }
            for (GroupNode group : myGroups.values()) {
                group.finish();
            }
            BuildProgress<BuildProgressDescriptor> root = myRoot;
            if (root != null) {
                LocalizeValue title = GitHubWorkflowLocalize.workflowRunJobsRoot();
                long now = System.currentTimeMillis();
                if (cancelled(terminalConclusion)) {
                    root.cancel(now, title);
                }
                else if (WorkflowRunProcessHandler.successful(terminalConclusion)) {
                    root.finish(now, false, title);
                }
                else {
                    root.fail(now, title);
                }
            }
        }
        myTerminalRunId = runId;
        myTerminalConclusion = terminalConclusion;
        myProcessHandler.refreshArtifactAvailability(ignored -> {
        });
    }

    @Override
    public void runDeleted(long runId) {
        Executor executor = myExecutor;
        if (executor == null || myProject.isDisposed()) {
            return;
        }
        myProject.getUIAccess().execute(() -> {
            if (myProject.isDisposed()) {
                return;
            }
            RunContentManager contentManager = myProject.getInstance(RunContentManager.class);
            RunContentDescriptor descriptor = contentManager.findContentDescriptor(executor, myProcessHandler);
            if (descriptor != null) {
                contentManager.removeRunContent(executor, descriptor);
            }
        });
    }

    @Override
    public void runDeleteFailed(long runId) {
        if (myTerminalRunId == runId) {
            myDeleteInProgress.set(false);
        }
    }

    @Override
    public void close() {
        if (myClosed.compareAndSet(false, true)) {
            myProcessHandler.removeProcessListener(myProcessListener);
        }
    }

    private boolean print(WorkflowRun.JobStatus job, String text, LogRenderer.Kind kind) {
        if (job.id() < 0) {
            return false;
        }
        synchronized (myLock) {
            JobNode node = jobNode(job);
            if (node == null) {
                return false;
            }
            output(node.myProgress, text, kind);
            node.update(job);
            return true;
        }
    }

    private void rootOutput(String text, boolean stdOut) {
        synchronized (myLock) {
            BuildProgress<BuildProgressDescriptor> root = myRoot;
            if (root != null) {
                root.output(stdOut ? text : ANSI_RED + text + ANSI_RESET, stdOut);
            }
        }
    }

    private @Nullable JobNode jobNode(WorkflowRun.JobStatus job) {
        BuildProgress<BuildProgressDescriptor> root = myRoot;
        if (root == null) {
            return null;
        }
        JobNode existing = myJobs.get(job.id());
        if (existing != null) {
            return existing;
        }
        JobDisplayName name = splitJobName(displayBaseName(job));
        GroupNode group = null;
        BuildProgress<BuildProgressDescriptor> parent = root;
        if (!name.group().isBlank()) {
            group = myGroups.computeIfAbsent(name.group(), groupName -> new GroupNode(groupName, root.startChildProgress(LocalizeValue.of(groupName))));
            parent = group.myProgress;
        }
        JobNode node = new JobNode(job.id(), name, group, parent.startChildProgress(LocalizeValue.of(name.name())));
        myJobs.put(job.id(), node);
        if (group != null) {
            group.myChildren.add(node);
        }
        return node;
    }

    private @Nullable JobNode findJob(ExecutionNode<?> node) {
        String name = node.getName();
        if (name == null) {
            return null;
        }
        NodeDescriptor<?> parent = node.getParentDescriptor();
        String parentName = parent == null ? null : parent.getName();
        synchronized (myLock) {
            JobNode fallback = null;
            for (JobNode job : myJobs.values()) {
                if (!name.equals(job.myName.name())) {
                    continue;
                }
                if (job.myName.group().isBlank() || job.myName.group().equals(parentName)) {
                    return job;
                }
                if (fallback == null) {
                    fallback = job;
                }
            }
            return fallback;
        }
    }

    private static void output(BuildProgress<BuildProgressDescriptor> progress, String text, LogRenderer.Kind kind) {
        if (text.isEmpty()) {
            return;
        }
        switch (kind) {
            case ERROR -> progress.output(ANSI_RED + text + ANSI_RESET, false);
            case WARNING -> progress.output(ANSI_YELLOW + text + ANSI_RESET, true);
            case SYSTEM -> progress.output(ANSI_GRAY + text + ANSI_RESET, true);
            case NORMAL -> progress.output(text, true);
        }
    }

    private AnAction[] runToolbarActions() {
        return new AnAction[]{
            new ToolbarAction(
                GitHubWorkflowLocalize.workflowRunGutterStopText(),
                PlatformIconGroup.actionsSuspend(),
                () -> !myProcessHandler.isProcessTerminated() && !myProcessHandler.isProcessTerminating(),
                myProcessHandler::destroyProcess
            ),
            new ToolbarAction(
                GitHubWorkflowLocalize.workflowRunRefreshTooltip(),
                PlatformIconGroup.actionsRefresh(),
                () -> !myProcessHandler.isProcessTerminated() && myProcessHandler.runId() > 0,
                myProcessHandler::refreshNow
            ),
            new ToolbarAction(
                GitHubWorkflowLocalize.workflowRunRerunAllTooltip(),
                PlatformIconGroup.actionsRerun(),
                this::canRerunAll,
                () -> myProcessHandler.rerunRemoteRun(false)
            ),
            new ToolbarAction(
                GitHubWorkflowLocalize.workflowRunRerunFailedTooltip(),
                PlatformIconGroup.actionsRestart(),
                this::canRerunFailed,
                () -> myProcessHandler.rerunRemoteRun(true)
            ),
            new ToolbarAction(
                GitHubWorkflowLocalize.workflowRunDownloadArtifactsTooltip(),
                PlatformIconGroup.actionsDownload(),
                this::canDownloadArtifacts,
                myProcessHandler::downloadArtifacts
            ),
            new ToolbarAction(
                GitHubWorkflowLocalize.workflowRunBrowseTooltip(),
                PlatformIconGroup.generalWeb(),
                () -> !myProcessHandler.runHtmlUrl().isBlank(),
                () -> Platform.current().openInBrowser(myProcessHandler.runHtmlUrl())
            ),
            new ToolbarAction(
                GitHubWorkflowLocalize.workflowRunDeleteTooltip(),
                PlatformIconGroup.generalDelete(),
                this::canDeleteRun,
                () -> {
                    if (myDeleteInProgress.compareAndSet(false, true)) {
                        myProcessHandler.deleteRemoteRun();
                    }
                }
            )
        };
    }

    private boolean canRerunAll() {
        return myTerminalRunId > 0 && terminal();
    }

    private boolean canRerunFailed() {
        return myTerminalRunId > 0 && terminal() && myAnyFailed;
    }

    private boolean canDownloadArtifacts() {
        return myTerminalRunId > 0 && myProcessHandler.artifactAvailability() == 1;
    }

    private boolean canDeleteRun() {
        return myTerminalRunId > 0 && terminal() && !myDeleteInProgress.get();
    }

    private boolean terminal() {
        return !myTerminalConclusion.isBlank();
    }

    static JobDisplayName splitJobName(@Nullable String name) {
        String normalized = name == null || name.isBlank()
            ? GitHubWorkflowLocalize.workflowRunJobFallbackname(-1).get()
            : name;
        int separator = normalized.indexOf(" / ");
        if (separator <= 0 || separator + 3 >= normalized.length()) {
            return new JobDisplayName("", normalized);
        }
        return new JobDisplayName(normalized.substring(0, separator), normalized.substring(separator + 3));
    }

    private static String displayBaseName(WorkflowRun.JobStatus job) {
        return job.name() == null || job.name().isBlank()
            ? GitHubWorkflowLocalize.workflowRunJobFallbackname(job.id()).get()
            : job.name();
    }

    private static boolean cancelled(@Nullable String conclusion) {
        return "cancelled".equals(conclusion) || "canceled".equals(conclusion);
    }

    private static String normalizeConclusion(@Nullable String conclusion) {
        return conclusion == null || conclusion.isBlank()
            ? "failure"
            : conclusion.toLowerCase(Locale.ROOT);
    }

    static class LogRenderer {
        private static final Pattern TIMESTAMP = Pattern.compile("^\\x{FEFF}?\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z\\s+");
        private static final Pattern GITHUB_COMMAND = Pattern.compile("^##\\[([^]]+)](.*)$");
        private static final Pattern WORKFLOW_COMMAND = Pattern.compile("^::([^: ]+)(?: [^:]*)?::(.*)$");
        private static final Pattern ANSI_SGR = Pattern.compile("\\x1B\\[([0-9;]*)m");
        private static final Pattern ANSI_CONTROL = Pattern.compile("\\x1B\\[[0-?]*[ -/]*[@-~]");

        private int myLineNumber = 0;
        private boolean myPrintedAny = false;

        static List<Segment> renderOnce(String text) {
            return new LogRenderer().render(text);
        }

        static String renderPlainOnce(String text) {
            return new LogRenderer().renderPlain(text);
        }

        List<Segment> render(@Nullable String text) {
            if (text == null || text.isEmpty()) {
                return List.of();
            }
            List<Segment> result = new ArrayList<>();
            int start = 0;
            while (start < text.length()) {
                int next = nextLineEnd(text, start);
                appendLine(result, text.substring(start, next));
                start = next;
            }
            return List.copyOf(result);
        }

        String renderPlain(String text) {
            StringBuilder result = new StringBuilder();
            for (Segment segment : render(text)) {
                result.append(segment.text());
            }
            return result.toString();
        }

        private void appendLine(List<Segment> result, String rawLine) {
            LineParts parts = splitLine(rawLine);
            AnsiLine ansiLine = stripAnsi(TIMESTAMP.matcher(parts.text()).replaceFirst(""));
            String line = ansiLine.text();
            Matcher githubCommand = GITHUB_COMMAND.matcher(line);
            if (githubCommand.matches()) {
                appendGitHubCommand(result, githubCommand.group(1), githubCommand.group(2), parts.separator());
                return;
            }
            Matcher workflowCommand = WORKFLOW_COMMAND.matcher(line);
            if (workflowCommand.matches()) {
                appendWorkflowCommand(result, workflowCommand.group(1), workflowCommand.group(2), parts.separator());
                return;
            }
            appendNumbered(result, line, ansiLine.kind() == Kind.NORMAL ? inferredKind(line) : ansiLine.kind(), parts.separator());
        }

        private void appendGitHubCommand(List<Segment> result, String command, String value, String separator) {
            String name = commandName(command);
            switch (name) {
                case "group" -> appendBlockHeader(result, value);
                case "endgroup", "/group" -> appendBlockEnd();
                case "command" -> appendNumbered(result, GitHubWorkflowLocalize.workflowLogCommand().get() + " " + value, Kind.SYSTEM, separator);
                case "warning" -> appendNumbered(result, GitHubWorkflowLocalize.workflowLogWarning().get() + " " + value, Kind.WARNING, separator);
                case "error" -> appendNumbered(result, GitHubWorkflowLocalize.workflowLogError().get() + " " + value, Kind.ERROR, separator);
                default -> appendNumbered(result, value.isBlank() ? "[" + name + "]" : value, Kind.SYSTEM, separator);
            }
        }

        private void appendWorkflowCommand(List<Segment> result, String command, String value, String separator) {
            String name = commandName(command);
            switch (name) {
                case "warning" -> appendNumbered(result, GitHubWorkflowLocalize.workflowLogWarning().get() + " " + value, Kind.WARNING, separator);
                case "error" -> appendNumbered(result, GitHubWorkflowLocalize.workflowLogError().get() + " " + value, Kind.ERROR, separator);
                case "group" -> appendBlockHeader(result, value);
                case "endgroup", "/group" -> appendBlockEnd();
                default -> appendNumbered(result, value, Kind.SYSTEM, separator);
            }
        }

        private void appendBlockHeader(List<Segment> result, String title) {
            String prefix = myPrintedAny ? "\n" : "";
            result.add(new Segment(prefix + "== " + title.strip() + " ==\n", Kind.SYSTEM));
            myLineNumber = 0;
            myPrintedAny = true;
        }

        private void appendBlockEnd() {
            myLineNumber = 0;
        }

        private void appendNumbered(List<Segment> result, String line, Kind kind, String separator) {
            myPrintedAny = true;
            if (line.isBlank()) {
                result.add(new Segment(separator, kind));
                return;
            }
            myLineNumber++;
            result.add(new Segment(String.format(Locale.ROOT, "%04d | %s%s", myLineNumber, line, separator), kind));
        }

        private static AnsiLine stripAnsi(String line) {
            Kind kind = Kind.NORMAL;
            Matcher matcher = ANSI_SGR.matcher(line);
            while (matcher.find()) {
                kind = strongest(kind, kindForAnsi(matcher.group(1)));
            }
            return new AnsiLine(ANSI_CONTROL.matcher(line).replaceAll(""), kind);
        }

        private static Kind kindForAnsi(String value) {
            String[] codes = value.isBlank() ? new String[]{"0"} : value.split(";");
            Kind result = Kind.NORMAL;
            for (String code : codes) {
                result = strongest(result, switch (code) {
                    case "31", "91" -> Kind.ERROR;
                    case "33", "93" -> Kind.WARNING;
                    case "34", "35", "36", "90", "94", "95", "96" -> Kind.SYSTEM;
                    default -> Kind.NORMAL;
                });
            }
            return result;
        }

        private static Kind strongest(Kind left, Kind right) {
            return weight(right) > weight(left) ? right : left;
        }

        private static int weight(Kind kind) {
            return switch (kind) {
                case ERROR -> 3;
                case WARNING -> 2;
                case SYSTEM -> 1;
                case NORMAL -> 0;
            };
        }

        private static Kind inferredKind(String line) {
            String normalized = line.stripLeading().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("error:") || normalized.startsWith("fatal:")) {
                return Kind.ERROR;
            }
            if (normalized.startsWith("warning:") || normalized.startsWith("npm warn ")) {
                return Kind.WARNING;
            }
            return Kind.NORMAL;
        }

        private static String commandName(String command) {
            int space = command.indexOf(' ');
            return (space >= 0 ? command.substring(0, space) : command).toLowerCase(Locale.ROOT);
        }

        private static LineParts splitLine(String line) {
            if (line.endsWith("\r\n")) {
                return new LineParts(line.substring(0, line.length() - 2), "\r\n");
            }
            if (line.endsWith("\n") || line.endsWith("\r")) {
                return new LineParts(line.substring(0, line.length() - 1), line.substring(line.length() - 1));
            }
            return new LineParts(line, "");
        }

        private static int nextLineEnd(String text, int start) {
            int index = start;
            while (index < text.length() && text.charAt(index) != '\n' && text.charAt(index) != '\r') {
                index++;
            }
            if (index >= text.length()) {
                return index;
            }
            if (text.charAt(index) == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') {
                return index + 2;
            }
            return index + 1;
        }

        enum Kind {
            NORMAL,
            SYSTEM,
            WARNING,
            ERROR
        }

        record Segment(String text, Kind kind) {
        }

        private record AnsiLine(String text, Kind kind) {
        }

        private record LineParts(String text, String separator) {
        }
    }

    record JobDisplayName(String group, String name) {
    }

    private class GroupNode {
        private final String myName;
        private final BuildProgress<BuildProgressDescriptor> myProgress;
        private final List<JobNode> myChildren = new ArrayList<>();
        private boolean myFinished;

        private GroupNode(String name, BuildProgress<BuildProgressDescriptor> progress) {
            myName = name;
            myProgress = progress;
        }

        private void finishIfDone() {
            if (!myChildren.isEmpty() && myChildren.stream().allMatch(child -> child.myFinished)) {
                finish();
            }
        }

        private void finish() {
            if (myFinished) {
                return;
            }
            myFinished = true;
            long now = System.currentTimeMillis();
            LocalizeValue title = LocalizeValue.of(myName);
            if (myChildren.stream().anyMatch(JobNode::failed)) {
                myProgress.fail(now, title);
            }
            else if (!myChildren.isEmpty() && myChildren.stream().allMatch(JobNode::skipped)) {
                myProgress.cancel(now, title);
            }
            else {
                myProgress.finish(now, false, title);
            }
        }
    }

    private class JobNode {
        private final long myJobId;
        private final JobDisplayName myName;
        private final @Nullable GroupNode myGroup;
        private final BuildProgress<BuildProgressDescriptor> myProgress;
        private final LogRenderer myLogRenderer = new LogRenderer();
        private String myConclusion = "";
        private boolean myFinished;
        private boolean myHasOutput;

        private JobNode(long jobId, JobDisplayName name, @Nullable GroupNode group, BuildProgress<BuildProgressDescriptor> progress) {
            myJobId = jobId;
            myName = name;
            myGroup = group;
            myProgress = progress;
        }

        private void update(WorkflowRun.JobStatus job) {
            myHasOutput = true;
            if ("completed".equals(job.status())) {
                finish(normalizeConclusion(job.conclusion()));
            }
        }

        private void finish(String conclusion) {
            if (myFinished) {
                return;
            }
            myFinished = true;
            myConclusion = conclusion;
            long now = System.currentTimeMillis();
            LocalizeValue title = LocalizeValue.of(myName.name());
            if ("skipped".equals(conclusion) || cancelled(conclusion)) {
                myProgress.cancel(now, title);
            }
            else if (WorkflowRunProcessHandler.successful(conclusion)) {
                myProgress.finish(now, false, title);
            }
            else {
                myAnyFailed = true;
                myProgress.fail(now, title);
            }
            if (myGroup != null) {
                myGroup.finishIfDone();
            }
        }

        private boolean failed() {
            return myFinished && !WorkflowRunProcessHandler.successful(myConclusion) && !cancelled(myConclusion);
        }

        private boolean skipped() {
            return myFinished && ("skipped".equals(myConclusion) || cancelled(myConclusion));
        }

        private boolean downloadableLog() {
            return myFinished || myHasOutput;
        }
    }

    private class SaveJobLogAction extends DumbAwareAction implements AnActionWithSyncUpdate {
        private final ExecutionNode<?> myNode;

        private SaveJobLogAction(ExecutionNode<?> node) {
            super(GitHubWorkflowLocalize.workflowRunDownloadLogTooltip(), GitHubWorkflowLocalize.workflowRunDownloadLogTooltip(), PlatformIconGroup.actionsMenu_saveall());
            myNode = node;
        }

        @Override
        public void update(AnActionEvent event) {
            JobNode job = findJob(myNode);
            event.getPresentation().setEnabledAndVisible(myProcessHandler.runId() > 0 && job != null && job.downloadableLog());
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(AnActionEvent event) {
            JobNode job = findJob(myNode);
            if (job != null) {
                myProcessHandler.downloadJobLog(job.myJobId, job.myName.name());
            }
        }
    }

    private static class ToolbarAction extends DumbAwareAction implements AnActionWithSyncUpdate {
        private final LocalizeValue myText;
        private final BooleanSupplier myVisible;
        private final Runnable myCommand;

        private ToolbarAction(LocalizeValue tooltip, Image icon, BooleanSupplier visible, Runnable command) {
            super(tooltip, tooltip, icon);
            myText = tooltip;
            myVisible = visible;
            myCommand = command;
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(AnActionEvent event) {
            myCommand.run();
        }

        @Override
        public void update(AnActionEvent event) {
            Presentation presentation = event.getPresentation();
            presentation.setText(myText);
            presentation.setDescription(myText);
            presentation.setEnabledAndVisible(myVisible.getAsBoolean());
        }
    }
}
