// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import org.jspecify.annotations.Nullable;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
public class GithubAuthorizationCreateRequest {
  private final List<String> scopes;

  private final @Nullable String note;
  private final @Nullable String noteUrl;

  public GithubAuthorizationCreateRequest(List<String> scopes, @Nullable String note, @Nullable String noteUrl) {
    this.scopes = scopes;
    this.note = note;
    this.noteUrl = noteUrl;
  }
}
