/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.state;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.disposer.Disposer;
import consulo.fileEditor.FileEditorManager;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.project.startup.BackgroundStartupActivity;
import consulo.ui.UIAccess;
import consulo.virtualFileSystem.VirtualFile;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.github.yunabraska.githubworkflow.state.GitHubActionCache.getActionCache;

@ExtensionImpl
public class GitHubActionCacheStartup implements BackgroundStartupActivity {
    @Override
    public void runActivity(Project project, UIAccess uiAccess) {
        PsiManager.getInstance(project).addPsiTreeChangeListener(new GitHubActionCache.ActionMetadataChangeListener(), project);

        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        for (VirtualFile openedFile : fileEditorManager.getOpenFiles()) {
            GitHubActionCache.asyncInitAllActions(project, openedFile);
        }

        ScheduledFuture<?> cleanupTask = AppExecutorUtil.getAppScheduledExecutorService()
            .scheduleWithFixedDelay(() -> getActionCache().cleanUp(), 0, 30, TimeUnit.MINUTES);
        Disposer.register(project, () -> cleanupTask.cancel(false));
    }
}
