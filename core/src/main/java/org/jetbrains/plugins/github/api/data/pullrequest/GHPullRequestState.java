// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import org.jetbrains.plugins.github.api.data.GithubIssueState;

public enum GHPullRequestState {
    CLOSED, MERGED, OPEN;

    public GithubIssueState asIssueState() {
        return this == CLOSED || this == MERGED ? GithubIssueState.closed : GithubIssueState.open;
    }
}
