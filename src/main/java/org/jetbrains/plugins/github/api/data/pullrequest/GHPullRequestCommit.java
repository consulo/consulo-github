// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import org.jetbrains.plugins.github.api.data.GHCommit;
import org.jetbrains.plugins.github.api.data.GHNode;

public class GHPullRequestCommit extends GHNode {
    private final GHCommit commit;
    private final String url;

    protected GHPullRequestCommit() {
        super("");
        this.commit = null;
        this.url = "";
    }

    public GHPullRequestCommit(String id, GHCommit commit, String url) {
        super(id);
        this.commit = commit;
        this.url = url;
    }

    public GHCommit getCommit() {
        return commit;
    }

    public String getUrl() {
        return url;
    }
}
