// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jspecify.annotations.Nullable;

import java.util.Date;

@GraphQLFragment("/graphql/fragment/comment.graphql")
public class GHComment extends GHNode implements GHReactable {
    private final @Nullable GHActor author;
    private final String body;
    private final Date createdAt;
    private final GHReactable.ReactionConnection reactions;

    protected GHComment() {
        super("");
        this.author = null;
        this.body = "";
        this.createdAt = null;
        this.reactions = null;
    }

    public GHComment(String id, @Nullable GHActor author, String body, Date createdAt, GHReactable.ReactionConnection reactions) {
        super(id);
        this.author = author;
        this.body = body;
        this.createdAt = createdAt;
        this.reactions = reactions;
    }

    public @Nullable GHActor getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    @Override
    public GHReactable.ReactionConnection getReactions() {
        return reactions;
    }
}
