// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.pullrequest;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.data.GHNode;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jspecify.annotations.Nullable;

@GraphQLFragment("/graphql/fragment/pullRequestBranchMatch.graphql")
public class GHPullRequestBranchMatch extends GHNode {
    private final long number;
    private final @Nullable String title;
    private final String headRefName;

    protected GHPullRequestBranchMatch() {
        this("", 0, null, "");
    }

    public GHPullRequestBranchMatch(String id, long number, @Nullable String title, String headRefName) {
        super(id);
        this.number = number;
        this.title = title;
        this.headRefName = headRefName;
    }

    public long getNumber() {
        return number;
    }

    public @Nullable String getTitle() {
        return title;
    }

    public String getHeadRefName() {
        return headRefName;
    }

    public GHPRIdentifier toPRIdentifier() {
        return new GHPRIdentifier(getId(), number);
    }
}
