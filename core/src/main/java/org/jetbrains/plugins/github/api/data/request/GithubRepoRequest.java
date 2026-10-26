// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.jspecify.annotations.Nullable;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubRepoRequest {
  private final String name;
  private final String description;

  @JsonProperty("private")
  private final boolean isPrivate;
  private final @Nullable Boolean autoInit;

  public GithubRepoRequest(String name, String description, boolean isPrivate, @Nullable Boolean autoInit) {
    this.name = name;
    this.description = description;
    this.isPrivate = isPrivate;
    this.autoInit = autoInit;
  }
}
