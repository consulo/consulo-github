// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;



//region Issue label
/*{
    "id": 208045946,
    "node_id": "MDU6TGFiZWwyMDgwNDU5NDY=",
    "url": "https://api.github.com/repos/octocat/Hello-World/labels/bug",
    "name": "bug",
    "description": "Houston, we have a problem",
    "color": "f29513",
    "default": true
  }*/
//endregion
@SuppressWarnings("UnusedDeclaration")
public class GithubIssueLabel {
  private Long id;
  private String nodeId;
  private String url;
  private String name;
  private String description;
  private String color;

  public String getNodeId() {
    return nodeId;
  }

  public String getUrl() {
    return url;
  }

  public String getName() {
    return name;
  }

  public String getColor() {
    return color;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GithubIssueLabel label)) return false;

    if (!id.equals(label.id)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
