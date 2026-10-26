// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import org.jspecify.annotations.Nullable;

public class GHPullRequestReviewRequest {
    private final @Nullable GHPullRequestRequestedReviewer requestedReviewer;

    protected GHPullRequestReviewRequest() {
        this.requestedReviewer = null;
    }

    public GHPullRequestReviewRequest(@Nullable GHPullRequestRequestedReviewer requestedReviewer) {
        this.requestedReviewer = requestedReviewer;
    }

    public @Nullable GHPullRequestRequestedReviewer getRequestedReviewer() {
        return requestedReviewer;
    }
}
