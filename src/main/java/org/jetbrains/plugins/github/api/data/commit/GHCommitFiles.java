// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.commit;

import java.util.List;
import java.util.Objects;

public class GHCommitFiles {
    private final List<GHCommitFile> files;

    protected GHCommitFiles() {
        this(List.of());
    }

    public GHCommitFiles(List<GHCommitFile> files) {
        this.files = files;
    }

    public List<GHCommitFile> getFiles() {
        return files;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHCommitFiles that)) {
            return false;
        }
        return Objects.equals(files, that.files);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(files);
    }
}
