// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jspecify.annotations.Nullable;

@GraphQLFragment("/graphql/fragment/commitStatusContext.graphql")
public class GHCommitStatusContext {
    private final String context;
    private final GHCommitStatusContextState state;
    private final boolean isRequired;
    private final @Nullable String targetUrl;

    protected GHCommitStatusContext() {
        this.context = "";
        this.state = null;
        this.isRequired = false;
        this.targetUrl = null;
    }

    public GHCommitStatusContext(String context, GHCommitStatusContextState state, boolean isRequired, @Nullable String targetUrl) {
        this.context = context;
        this.state = state;
        this.isRequired = isRequired;
        this.targetUrl = targetUrl;
    }

    public String getContext() {
        return context;
    }

    public GHCommitStatusContextState getState() {
        return state;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public @Nullable String getTargetUrl() {
        return targetUrl;
    }
}
