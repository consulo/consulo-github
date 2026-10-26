// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.util.lang.StringUtil;

import org.jetbrains.plugins.github.api.GHRepositoryPath;

import java.util.Objects;

@SuppressWarnings("UnusedDeclaration")
public class GithubRepoBasic {
  private Long id;
  //private String nodeId;
  private String name;
  private String fullName;
  private GithubUser owner;
  @JsonProperty("private")
  private Boolean isPrivate;
  private String htmlUrl;
  private String description;
  @JsonProperty("fork")
  private Boolean isFork;

  private String url;
  //urls

  public String getName() {
    return name;
  }

  public String getDescription() {
    return StringUtil.notNullize(description);
  }

  public boolean isPrivate() {
    return isPrivate;
  }

  public boolean isFork() {
    return isFork;
  }

  public String getUrl() {
    return url;
  }

  public String getHtmlUrl() {
    return htmlUrl;
  }

  public GithubUser getOwner() {
    return owner;
  }


  public String getUserName() {
    return getOwner().getLogin();
  }

  public String getFullName() {
    return getUserName() + "/" + getName();
  }

  public GHRepositoryPath getFullPath() {
    return new GHRepositoryPath(getUserName(), getName());
  }

  @Override
  public String toString() {
    return "GithubRepo{" +
           "id=" + id +
           ", name='" + name + '\'' +
           '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GithubRepoBasic basic)) return false;
    return id.equals(basic.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
