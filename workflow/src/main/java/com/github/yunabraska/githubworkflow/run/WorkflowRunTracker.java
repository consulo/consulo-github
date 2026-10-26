/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.run;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.process.ProcessHandler;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Tracks workflow runs started from one project so editor gutter actions can switch between run and stop.
 */
@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class WorkflowRunTracker {
    private final Project myProject;
    private final ConcurrentMap<String, ProcessHandler> myRuns = new ConcurrentHashMap<>();

    @Inject
    public WorkflowRunTracker(Project project) {
        myProject = project;
    }

    public static WorkflowRunTracker getInstance(Project project) {
        return project.getInstance(WorkflowRunTracker.class);
    }

    public static String key(@Nullable String workflowPath) {
        return Optional.ofNullable(workflowPath).orElse("").replace('\\', '/');
    }

    public boolean isRunning(@Nullable String workflowPath) {
        return myRuns.containsKey(key(workflowPath));
    }

    public void register(@Nullable String workflowPath, ProcessHandler processHandler) {
        myRuns.put(key(workflowPath), processHandler);
        refreshGutters();
    }

    public void unregister(@Nullable String workflowPath, ProcessHandler processHandler) {
        myRuns.remove(key(workflowPath), processHandler);
        refreshGutters();
    }

    public boolean stop(@Nullable String workflowPath) {
        return Optional.ofNullable(myRuns.get(key(workflowPath)))
            .map(processHandler -> {
                processHandler.destroyProcess();
                return true;
            })
            .orElse(false);
    }

    private void refreshGutters() {
        if (myProject.isDisposed()) {
            return;
        }
        myProject.getUIAccess().execute(() -> {
            if (!myProject.isDisposed()) {
                DaemonCodeAnalyzer.getInstance(myProject).settingsChanged();
            }
        });
    }
}
