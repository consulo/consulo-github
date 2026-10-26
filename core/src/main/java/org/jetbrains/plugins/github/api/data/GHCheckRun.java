// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jspecify.annotations.Nullable;

@GraphQLFragment("/graphql/fragment/checkRun.graphql")
public class GHCheckRun {
    private final String name;
    private final @Nullable GHCommitCheckSuiteConclusion conclusion;
    private final boolean isRequired;
    private final @Nullable String detailsUrl;
    private final String url;

    protected GHCheckRun() {
        this.name = "";
        this.conclusion = null;
        this.isRequired = false;
        this.detailsUrl = null;
        this.url = "";
    }

    public GHCheckRun(String name,
                      @Nullable GHCommitCheckSuiteConclusion conclusion,
                      boolean isRequired,
                      @Nullable String detailsUrl,
                      String url) {
        this.name = name;
        this.conclusion = conclusion;
        this.isRequired = isRequired;
        this.detailsUrl = detailsUrl;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public @Nullable GHCommitCheckSuiteConclusion getConclusion() {
        return conclusion;
    }

    public boolean isRequired() {
        return isRequired;
    }

    public @Nullable String getDetailsUrl() {
        return detailsUrl;
    }

    public String getUrl() {
        return url;
    }
}
