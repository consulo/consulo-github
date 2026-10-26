// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import com.fasterxml.jackson.annotation.JsonProperty;
import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.data.GHNode;
import org.jspecify.annotations.Nullable;

@GraphQLFragment("/graphql/fragment/teamInfo.graphql")
public class GHTeam extends GHNode implements GHPullRequestRequestedReviewer {
    private final String slug;
    private final String url;
    @JsonProperty("teamAvatarUrl")
    private final @Nullable String avatarUrl;
    @JsonProperty("teamName")
    private final @Nullable String name;
    private final String combinedSlug;

    protected GHTeam() {
        super("");
        this.slug = "";
        this.url = "";
        this.avatarUrl = null;
        this.name = null;
        this.combinedSlug = "";
    }

    public GHTeam(String id, String slug, String url, @Nullable String avatarUrl, @Nullable String name, String combinedSlug) {
        super(id);
        this.slug = slug;
        this.url = url;
        this.avatarUrl = avatarUrl;
        this.name = name;
        this.combinedSlug = combinedSlug;
    }

    public String getSlug() {
        return slug;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public @Nullable String getAvatarUrl() {
        return avatarUrl;
    }

    @Override
    public @Nullable String getName() {
        return name;
    }

    public String getCombinedSlug() {
        return combinedSlug;
    }

    @Override
    public String getShortName() {
        return combinedSlug;
    }
}
