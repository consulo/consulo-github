// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration", "MismatchedQueryAndUpdateOfCollection"})
public class GithubGistRequest {
  private final String description;
  private final Map<String, GistFile> files;

  @JsonProperty("public")
  private final boolean isPublic;

  public static class GistFile {
    private final String content;

    public GistFile(String content) {
      this.content = content;
    }
  }

  public GithubGistRequest(List<FileContent> files, String description, boolean isPublic) {
    this.description = description;
    this.isPublic = isPublic;

    this.files = new HashMap<>();
    for (FileContent file : files) {
      this.files.put(file.getFileName(), new GistFile(file.getContent()));
    }
  }

  public static class FileContent {
    private final String myFileName;
    private final String myContent;

    public FileContent(String fileName, String content) {
      myFileName = fileName;
      myContent = content;
    }

    public String getFileName() {
      return myFileName;
    }

    public String getContent() {
      return myContent;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FileContent that = (FileContent)o;

      if (!myContent.equals(that.myContent)) return false;
      if (!myFileName.equals(that.myFileName)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myFileName.hashCode();
      result = 31 * result + myContent.hashCode();
      return result;
    }
  }
}
