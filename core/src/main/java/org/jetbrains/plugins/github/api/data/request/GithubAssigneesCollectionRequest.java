// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import java.util.Collection;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubAssigneesCollectionRequest {
  private final Collection<String> assignees;

  public GithubAssigneesCollectionRequest(Collection<String> assignees) {
    this.assignees = assignees;
  }
}
