// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import org.jspecify.annotations.Nullable;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubCreateIssueRequest {
  private final String title;
  private final @Nullable String body;
  private final @Nullable Long milestone;
  private final @Nullable List<String> labels;
  private final @Nullable List<String> assignees;

  public GithubCreateIssueRequest(String title,
                                  @Nullable String body,
                                  @Nullable Long milestone,
                                  @Nullable List<String> labels,
                                  @Nullable List<String> assignees) {
    this.title = title;
    this.body = body;
    this.milestone = milestone;
    this.labels = labels;
    this.assignees = assignees;
  }
}
