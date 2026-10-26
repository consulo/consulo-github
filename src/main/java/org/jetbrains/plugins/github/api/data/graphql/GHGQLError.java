// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.graphql;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.http.graphql.dto.GraphQLErrorDTO;
import org.jspecify.annotations.Nullable;

public class GHGQLError extends GraphQLErrorDTO {
    private final @Nullable String type;

    @JsonCreator
    public GHGQLError(@JsonProperty("message") String message, @JsonProperty("type") @Nullable String type) {
        super(message);
        this.type = type;
    }

    public @Nullable String getType() {
        return type;
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
