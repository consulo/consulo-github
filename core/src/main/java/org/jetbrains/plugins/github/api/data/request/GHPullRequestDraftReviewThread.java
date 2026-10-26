// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.request;

import consulo.diff.util.Side;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class GHPullRequestDraftReviewThread {
    private final String body;
    private final int line;
    private final String path;
    private final Side side;
    private final @Nullable Integer startLine;
    private final @Nullable Side startSide;

    protected GHPullRequestDraftReviewThread() {
        this.body = "";
        this.line = 0;
        this.path = "";
        this.side = null;
        this.startLine = null;
        this.startSide = null;
    }

    public GHPullRequestDraftReviewThread(String body,
                                          int line,
                                          String path,
                                          Side side,
                                          @Nullable Integer startLine,
                                          @Nullable Side startSide) {
        this.body = body;
        this.line = line;
        this.path = path;
        this.side = side;
        this.startLine = startLine;
        this.startSide = startSide;
    }

    public String getBody() {
        return body;
    }

    public int getLine() {
        return line;
    }

    public String getPath() {
        return path;
    }

    public Side getSide() {
        return side;
    }

    public @Nullable Integer getStartLine() {
        return startLine;
    }

    public @Nullable Side getStartSide() {
        return startSide;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPullRequestDraftReviewThread that)) {
            return false;
        }
        return Objects.equals(body, that.body)
            && line == that.line
            && Objects.equals(path, that.path)
            && Objects.equals(side, that.side)
            && Objects.equals(startLine, that.startLine)
            && Objects.equals(startSide, that.startSide);
    }

    @Override
    public int hashCode() {
        return Objects.hash(body, line, path, side, startLine, startSide);
    }
}
