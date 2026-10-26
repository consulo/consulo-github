// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jspecify.annotations.Nullable;

@GraphQLFragment("/graphql/fragment/user.graphql")
public class GHUser extends GHNode implements GHActor, GHPullRequestRequestedReviewer {
    private final String login;
    private final String url;
    private final String avatarUrl;
    private final @Nullable String name;

    protected GHUser() {
        super("");
        this.login = "";
        this.url = "";
        this.avatarUrl = "";
        this.name = null;
    }

    public GHUser(String id, String login, String url, String avatarUrl, @Nullable String name) {
        super(id);
        this.login = login;
        this.url = url;
        this.avatarUrl = avatarUrl;
        this.name = name;
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
        return name;
    }

    public static final GHUser FAKE_GHOST = new GHUser("jb-ghost-user", "ghost", "", "", null);

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
