// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import java.util.Collection;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubReviewersCollectionRequest {
  private final Collection<String> reviewers;
  private final Collection<String> teamReviewers;

  public GithubReviewersCollectionRequest(Collection<String> reviewers,
                                          Collection<String> teamReviewers) {
    this.reviewers = reviewers;
    this.teamReviewers = teamReviewers;
  }
}
