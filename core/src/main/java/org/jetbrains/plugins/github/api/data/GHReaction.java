// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@GraphQLFragment("/graphql/fragment/reaction.graphql")
public class GHReaction {
    private final GHReactionContent content;
    private final @Nullable GHUser user;

    protected GHReaction() {
        this.content = null;
        this.user = null;
    }

    public GHReaction(GHReactionContent content, @Nullable GHUser user) {
        this.content = content;
        this.user = user;
    }

    public GHReactionContent getContent() {
        return content;
    }

    public @Nullable GHUser getUser() {
        return user;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHReaction that)) {
            return false;
        }
        return Objects.equals(content, that.content)
            && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, user);
    }
}
