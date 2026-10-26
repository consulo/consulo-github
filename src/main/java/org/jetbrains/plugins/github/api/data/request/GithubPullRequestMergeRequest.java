// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import org.jetbrains.plugins.github.api.data.GithubPullRequestMergeMethod;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubPullRequestMergeRequest {
  private final String commitTitle;
  private final String commitMessage;
  private final String sha;
  private final GithubPullRequestMergeMethod mergeMethod;

  public GithubPullRequestMergeRequest(String commitTitle,
                                       String commitMessage,
                                       String sha,
                                       GithubPullRequestMergeMethod mergeMethod) {
    if (mergeMethod != GithubPullRequestMergeMethod.merge && mergeMethod != GithubPullRequestMergeMethod.squash) {
      throw new IllegalArgumentException("Invalid merge method");
    }

    this.commitTitle = commitTitle;
    this.commitMessage = commitMessage;
    this.sha = sha;
    this.mergeMethod = mergeMethod;
  }
}
