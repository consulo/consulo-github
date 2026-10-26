// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import consulo.http.graphql.dto.GraphQLFragment;

@GraphQLFragment("/graphql/fragment/commitHash.graphql")
public class GHCommitHash extends GHNode {
    private final String oid;
    private final String abbreviatedOid;

    protected GHCommitHash() {
        super("");
        this.oid = "";
        this.abbreviatedOid = "";
    }

    public GHCommitHash(String id, String oid, String abbreviatedOid) {
        super(id);
        this.oid = oid;
        this.abbreviatedOid = abbreviatedOid;
    }

    public String getOid() {
        return oid;
    }

    public String getAbbreviatedOid() {
        return abbreviatedOid;
    }
}
