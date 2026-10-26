// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.http.graphql.dto.GraphQLFragment;
import consulo.http.graphql.dto.GraphQLNodesDTO;
import org.jspecify.annotations.Nullable;

import java.util.Date;

@GraphQLFragment("/graphql/fragment/commit.graphql")
public class GHCommit extends GHCommitShort {
    private final String messageBody;
    private final @Nullable GHGitActor committer;
    private final Date committedDate;
    @JsonProperty("parents")
    private final GraphQLNodesDTO<GHCommitHash> parentsNodes;

    protected GHCommit() {
        super("", "", "", "", "", null);
        this.messageBody = "";
        this.committer = null;
        this.committedDate = null;
        this.parentsNodes = new GraphQLNodesDTO<>();
    }

    public GHCommit(String id,
                    String oid,
                    String abbreviatedOid,
                    String url,
                    String messageHeadline,
                    String messageBody,
                    @Nullable GHGitActor author,
                    @Nullable GHGitActor committer,
                    Date committedDate,
                    GraphQLNodesDTO<GHCommitHash> parents) {
        super(id, oid, abbreviatedOid, url, messageHeadline, author);
        this.messageBody = messageBody;
        this.committer = committer;
        this.committedDate = committedDate;
        this.parentsNodes = parents;
    }

    public String getMessageBody() {
        return messageBody;
    }

    public @Nullable GHGitActor getCommitter() {
        return committer;
    }

    public Date getCommittedDate() {
        return committedDate;
    }

    public List<GHCommitHash> getParents() {
        return parentsNodes.getNodes();
    }
}
