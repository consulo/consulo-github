// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import org.jspecify.annotations.Nullable;

public class GithubContent {
    private final String name;
    private final String path;
    private final String type;
    private final long size;
    private final String url;
    private final @Nullable String downloadUrl;

    protected GithubContent() {
        this.name = "";
        this.path = "";
        this.type = "";
        this.size = 0;
        this.url = "";
        this.downloadUrl = null;
    }

    public GithubContent(String name, String path, String type, long size, String url, @Nullable String downloadUrl) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.size = size;
        this.url = url;
        this.downloadUrl = downloadUrl;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getType() {
        return type;
    }

    public long getSize() {
        return size;
    }

    public String getUrl() {
        return url;
    }

    public @Nullable String getDownloadUrl() {
        return downloadUrl;
    }

    public static final class Types {
        public static final String FILE = "file";
        public static final String DIR = "dir";
        public static final String SYMLINK = "symlink";
        public static final String SUBMODULE = "submodule";

        private Types() {
        }
    }
}
