// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.pullrequest;

import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class GHPullRequestRestIdOnly {
    private final String id;
    private final String nodeId;
    private final long number;
    private final @Nullable String title;

    protected GHPullRequestRestIdOnly() {
        this.id = "";
        this.nodeId = "";
        this.number = 0;
        this.title = null;
    }

    public GHPullRequestRestIdOnly(String id, String nodeId, long number, @Nullable String title) {
        this.id = id;
        this.nodeId = nodeId;
        this.number = number;
        this.title = title;
    }

    public String getId() {
        return id;
    }

    public String getNodeId() {
        return nodeId;
    }

    public long getNumber() {
        return number;
    }

    public @Nullable String getTitle() {
        return title;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPullRequestRestIdOnly that)) {
            return false;
        }
        return Objects.equals(id, that.id)
            && Objects.equals(nodeId, that.nodeId)
            && number == that.number
            && Objects.equals(title, that.title);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, nodeId, number, title);
    }

    public GHPRIdentifier toPRIdentifier() {
        return new GHPRIdentifier(nodeId, number);
    }
}
