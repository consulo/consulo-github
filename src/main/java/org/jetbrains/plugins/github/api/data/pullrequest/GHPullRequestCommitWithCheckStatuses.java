// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.data.GHCommitWithCheckStatuses;
import org.jetbrains.plugins.github.api.data.GHNode;

@GraphQLFragment("/graphql/fragment/pullRequestCommitWithCheckStatuses.graphql")
public class GHPullRequestCommitWithCheckStatuses extends GHNode {
    private final GHCommitWithCheckStatuses commit;

    protected GHPullRequestCommitWithCheckStatuses() {
        super("");
        this.commit = null;
    }

    public GHPullRequestCommitWithCheckStatuses(String id, GHCommitWithCheckStatuses commit) {
        super(id);
        this.commit = commit;
    }

    public GHCommitWithCheckStatuses getCommit() {
        return commit;
    }
}
