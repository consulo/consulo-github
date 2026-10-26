// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.http.graphql.dto.GraphQLConnectionDTO;
import consulo.http.graphql.dto.GraphQLCursorPageInfoDTO;
import consulo.http.graphql.dto.GraphQLFragment;

import java.util.List;

@GraphQLFragment("/graphql/fragment/reactions.graphql")
public interface GHReactable {
    ReactionConnection getReactions();

    class ReactionConnection extends GraphQLConnectionDTO<GHReaction> {
        public ReactionConnection(GraphQLCursorPageInfoDTO pageInfo) {
            this(pageInfo, List.of());
        }

        @JsonCreator
        public ReactionConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo, @JsonProperty("nodes") List<GHReaction> nodes) {
            super(pageInfo, nodes);
        }
    }
}
