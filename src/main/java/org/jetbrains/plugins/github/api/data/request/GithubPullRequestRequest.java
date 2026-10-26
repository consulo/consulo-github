// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;



@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubPullRequestRequest {
  private final String title;
  private final String body;
  private final String head; // branch with changes
  private final String base; // branch requested to

  public GithubPullRequestRequest(String title, String description, String head, String base) {
    this.title = title;
    this.body = description;
    this.head = head;
    this.base = base;
  }
}
