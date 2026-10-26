// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@GraphQLFragment("/graphql/fragment/pullRequestTemplate.graphql")
public class GHRepositoryPullRequestTemplate {
    private final @Nullable String body;

    protected GHRepositoryPullRequestTemplate() {
        this.body = null;
    }

    public GHRepositoryPullRequestTemplate(@Nullable String body) {
        this.body = body;
    }

    public @Nullable String getBody() {
        return body;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHRepositoryPullRequestTemplate that)) {
            return false;
        }
        return Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(body);
    }
}
