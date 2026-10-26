// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jspecify.annotations.Nullable;

@GraphQLFragment("/graphql/fragment/commitShort.graphql")
public class GHCommitShort extends GHCommitHash {
    private final String url;
    private final String messageHeadline;
    private final @Nullable GHGitActor author;

    protected GHCommitShort() {
        super("", "", "");
        this.url = "";
        this.messageHeadline = "";
        this.author = null;
    }

    public GHCommitShort(String id, String oid, String abbreviatedOid, String url, String messageHeadline, @Nullable GHGitActor author) {
        super(id, oid, abbreviatedOid);
        this.url = url;
        this.messageHeadline = messageHeadline;
        this.author = author;
    }

    public String getUrl() {
        return url;
    }

    public String getMessageHeadline() {
        return messageHeadline;
    }

    public @Nullable GHGitActor getAuthor() {
        return author;
    }
}
