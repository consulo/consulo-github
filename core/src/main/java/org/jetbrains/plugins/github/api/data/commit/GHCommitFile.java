// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.commit;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class GHCommitFile {
    private final @Nullable String previousFilename;
    private final String filename;
    private final Status status;
    // null for binary files
    private final @Nullable String patch;

    protected GHCommitFile() {
        this(null, "", Status.unchanged, null);
    }

    public GHCommitFile(@Nullable String previousFilename, String filename, Status status, @Nullable String patch) {
        this.previousFilename = previousFilename;
        this.filename = filename;
        this.status = status;
        this.patch = patch;
    }

    public @Nullable String getPreviousFilename() {
        return previousFilename;
    }

    public String getFilename() {
        return filename;
    }

    public Status getStatus() {
        return status;
    }

    public @Nullable String getPatch() {
        return patch;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHCommitFile that)) {
            return false;
        }
        return Objects.equals(previousFilename, that.previousFilename)
            && Objects.equals(filename, that.filename)
            && status == that.status
            && Objects.equals(patch, that.patch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(previousFilename, filename, status, patch);
    }

    @SuppressWarnings("EnumEntryName")
    public enum Status {
        added, removed, modified, renamed, copied, changed, unchanged
    }
}
