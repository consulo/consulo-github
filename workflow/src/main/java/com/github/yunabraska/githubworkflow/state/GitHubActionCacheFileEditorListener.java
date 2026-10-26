/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.state;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicImpl;
import consulo.fileEditor.FileEditorManager;
import consulo.fileEditor.event.FileEditorManagerListener;
import consulo.virtualFileSystem.VirtualFile;

@TopicImpl(ComponentScope.PROJECT)
public class GitHubActionCacheFileEditorListener implements FileEditorManagerListener {
    @Override
    public void fileOpened(FileEditorManager source, VirtualFile file) {
        GitHubActionCache.asyncInitAllActions(source.getProject(), file);
    }
}
