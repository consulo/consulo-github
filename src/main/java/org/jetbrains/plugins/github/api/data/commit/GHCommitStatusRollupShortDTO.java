// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.commit;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.data.GHCommitStatusContextState;

import java.util.Objects;

@GraphQLFragment("/graphql/fragment/commitStatusRollupShort.graphql")
public class GHCommitStatusRollupShortDTO {
    private final GHCommitStatusContextState state;

    protected GHCommitStatusRollupShortDTO() {
        this.state = null;
    }

    public GHCommitStatusRollupShortDTO(GHCommitStatusContextState state) {
        this.state = state;
    }

    public GHCommitStatusContextState getState() {
        return state;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHCommitStatusRollupShortDTO that)) {
            return false;
        }
        return Objects.equals(state, that.state);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state);
    }
}
