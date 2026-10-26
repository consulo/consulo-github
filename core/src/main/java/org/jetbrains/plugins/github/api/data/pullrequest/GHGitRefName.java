// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import consulo.http.graphql.dto.GraphQLFragment;

@GraphQLFragment("/graphql/fragment/gitRefName.graphql")
public class GHGitRefName {
    private final String name;

    protected GHGitRefName() {
        this.name = "";
    }

    public GHGitRefName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
