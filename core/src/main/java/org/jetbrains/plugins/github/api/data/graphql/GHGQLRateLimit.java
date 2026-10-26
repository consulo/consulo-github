// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.graphql;

import consulo.http.graphql.dto.GraphQLFragment;

import java.util.Objects;

@GraphQLFragment("/graphql/fragment/rates.graphql")
public class GHGQLRateLimit {
    private final int cost;

    protected GHGQLRateLimit() {
        this.cost = 0;
    }

    public GHGQLRateLimit(int cost) {
        this.cost = cost;
    }

    public int getCost() {
        return cost;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHGQLRateLimit that)) {
            return false;
        }
        return cost == that.cost;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cost);
    }
}
