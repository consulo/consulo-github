// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jspecify.annotations.Nullable;
import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;

@GraphQLFragment("/graphql/fragment/actorInfo.graphql")
public class GHMannequin extends GHNode implements GHActor, GHPullRequestRequestedReviewer {
    private final String login;
    private final String url;
    private final String avatarUrl;

    protected GHMannequin() {
        super("");
        this.login = "";
        this.url = "";
        this.avatarUrl = "";
    }

    public GHMannequin(String id, String login, String url, String avatarUrl) {
        super(id);
        this.login = login;
        this.url = url;
        this.avatarUrl = avatarUrl;
    }

    @Override
    public String getLogin() {
        return login;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public String getAvatarUrl() {
        return avatarUrl;
    }

    @Override
    public @Nullable String getName() {
        return null;
    }

    @Override
    public String getShortName() {
        return getLogin();
    }

    @Override
    public String getPresentableName() {
        String name = getName();
        return name != null ? name : getLogin();
    }
}
