// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jspecify.annotations.Nullable;

@GraphQLFragment("/graphql/fragment/actor.graphql")
public class GHOrganization extends GHNode implements GHActor {
    private final String login;
    private final String url;
    private final String avatarUrl;
    private final @Nullable String name;

    protected GHOrganization() {
        super("");
        this.login = "";
        this.url = "";
        this.avatarUrl = "";
        this.name = null;
    }

    public GHOrganization(String id, String login, String url, String avatarUrl, @Nullable String name) {
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

    public @Nullable String getName() {
        return name;
    }

    @Override
    public String getPresentableName() {
        String name = getName();
        return name != null ? name : getLogin();
    }
}
