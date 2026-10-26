// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import java.util.Date;

@SuppressWarnings("UnusedDeclaration")
public class GithubIssueComment {
  private Long id;

  private String url;
  private String htmlUrl;
  private String body;

  private Date createdAt;
  private Date updatedAt;

  private GithubUser user;

  public long getId() {
    return id;
  }

  public String getHtmlUrl() {
    return htmlUrl;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public Date getUpdatedAt() {
    return updatedAt;
  }

  public GithubUser getUser() {
    return user;
  }
}
