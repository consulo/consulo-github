// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;



@SuppressWarnings("UnusedDeclaration")
public class GithubUserWithPermissions extends GithubUser {
  private GithubPermissions permissions;

  public GithubPermissions getPermissions() {
    return permissions;
  }
}
