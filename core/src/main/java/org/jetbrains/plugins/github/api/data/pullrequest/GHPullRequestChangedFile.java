// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

public class GHPullRequestChangedFile {
    private final String path;
    private final GHPullRequestFileViewedState viewerViewedState;

    protected GHPullRequestChangedFile() {
        this("", GHPullRequestFileViewedState.UNVIEWED);
    }

    public GHPullRequestChangedFile(String path, GHPullRequestFileViewedState viewerViewedState) {
        this.path = path;
        this.viewerViewedState = viewerViewedState;
    }

    public String getPath() {
        return path;
    }

    public GHPullRequestFileViewedState getViewerViewedState() {
        return viewerViewedState;
    }
}
