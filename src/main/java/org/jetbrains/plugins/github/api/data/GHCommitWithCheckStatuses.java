// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.http.graphql.dto.GraphQLConnectionDTO;
import consulo.http.graphql.dto.GraphQLCursorPageInfoDTO;
import consulo.http.graphql.dto.GraphQLFragment;
import org.jspecify.annotations.Nullable;

import java.util.List;

@GraphQLFragment("/graphql/fragment/commitWithCheckStatuses.graphql")
public class GHCommitWithCheckStatuses extends GHCommitHash {
    private final @Nullable Status status;
    private final @Nullable GHCheckSuiteConnection checkSuites;

    protected GHCommitWithCheckStatuses() {
        super("", "", "");
        this.status = null;
        this.checkSuites = null;
    }

    public GHCommitWithCheckStatuses(String id,
                                     String oid,
                                     String abbreviatedOid,
                                     @Nullable Status status,
                                     @Nullable GHCheckSuiteConnection checkSuites) {
        super(id, oid, abbreviatedOid);
        this.status = status;
        this.checkSuites = checkSuites;
    }

    public @Nullable Status getStatus() {
        return status;
    }

    public @Nullable GHCheckSuiteConnection getCheckSuites() {
        return checkSuites;
    }

    public static class Status {
        private final List<GHCommitStatusContext> contexts;

        protected Status() {
            this(List.of());
        }

        public Status(List<GHCommitStatusContext> contexts) {
            this.contexts = contexts;
        }

        public List<GHCommitStatusContext> getContexts() {
            return contexts;
        }
    }

    public static class GHCheckSuiteConnection extends GraphQLConnectionDTO<GHCheckSuite> {
        public GHCheckSuiteConnection(GraphQLCursorPageInfoDTO pageInfo) {
            this(pageInfo, List.of());
        }

        @JsonCreator
        public GHCheckSuiteConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo, @JsonProperty("nodes") List<GHCheckSuite> nodes) {
            super(pageInfo, nodes);
        }
    }
}
