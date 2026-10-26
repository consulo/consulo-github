// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.http.graphql.dto.GraphQLConnectionDTO;
import consulo.http.graphql.dto.GraphQLCursorPageInfoDTO;
import consulo.http.graphql.dto.GraphQLFragment;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

@GraphQLFragment("/graphql/fragment/checkSuite.graphql")
public class GHCheckSuite {
    private final @Nullable CheckRunConnection checkRuns;

    protected GHCheckSuite() {
        this.checkRuns = null;
    }

    public GHCheckSuite(@Nullable CheckRunConnection checkRuns) {
        this.checkRuns = checkRuns;
    }

    public @Nullable CheckRunConnection getCheckRuns() {
        return checkRuns;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHCheckSuite that)) {
            return false;
        }
        return Objects.equals(checkRuns, that.checkRuns);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checkRuns);
    }

    public static class CheckRunConnection extends GraphQLConnectionDTO<GHCheckRun> {
        public CheckRunConnection(GraphQLCursorPageInfoDTO pageInfo) {
            this(pageInfo, List.of());
        }

        @JsonCreator
        public CheckRunConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo, @JsonProperty("nodes") List<GHCheckRun> nodes) {
            super(pageInfo, nodes);
        }
    }
}
