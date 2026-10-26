// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.graphql.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.http.graphql.dto.GraphQLConnectionDTO;
import consulo.http.graphql.dto.GraphQLCursorPageInfoDTO;
import consulo.http.graphql.dto.GraphQLPagedResponseDataDTO;

import java.util.List;

public class GHGQLSearchQueryResponse<T> implements GraphQLPagedResponseDataDTO<T> {
    private final SearchConnection<T> search;

    @JsonCreator
    public GHGQLSearchQueryResponse(@JsonProperty("search") SearchConnection<T> search) {
        this.search = search;
    }

    public SearchConnection<T> getSearch() {
        return search;
    }

    @Override
    public GraphQLCursorPageInfoDTO getPageInfo() {
        return search.getPageInfo();
    }

    @Override
    public List<T> getNodes() {
        return search.getNodes();
    }

    public static class SearchConnection<T> extends GraphQLConnectionDTO<T> {
        public SearchConnection(GraphQLCursorPageInfoDTO pageInfo) {
            this(pageInfo, List.of());
        }

        @JsonCreator
        public SearchConnection(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo, @JsonProperty("nodes") List<T> nodes) {
            super(pageInfo, nodes);
        }
    }
}
