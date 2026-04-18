// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jspecify.annotations.Nullable;

public class GHUser extends GHNode implements GHActor {
    private String myLogin;
    private String myUrl;
    private String myAvatarUrl;
    private @Nullable String myName;

    public GHUser() {
    }

    public GHUser(String id, String login, String url, String avatarUrl, @Nullable String name) {
        super(id);
        myLogin = login;
        myUrl = url;
        myAvatarUrl = avatarUrl;
        myName = name;
    }

    @Override
    public String getLogin() {
        return myLogin;
    }

    @Override
    public String getUrl() {
        return myUrl;
    }

    @Override
    public String getAvatarUrl() {
        return myAvatarUrl;
    }

    public @Nullable String getName() {
        return myName;
    }

    @Override
    public String getPresentableName() {
        return myName != null ? myName : myLogin;
    }
}
