// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.jspecify.annotations.Nullable;

import java.util.Date;

public class GHGitActor {
    private final @Nullable String name;
    private final @Nullable String email;
    private final String avatarUrl;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssX")
    private final @Nullable Date date;
    private final @Nullable GHUser user;

    protected GHGitActor() {
        this(null, null, "", null, null);
    }

    public GHGitActor(@Nullable String name, @Nullable String email, String avatarUrl, @Nullable Date date, @Nullable GHUser user) {
        this.name = name;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.date = date;
        this.user = user;
    }

    public @Nullable String getName() {
        return name;
    }

    public @Nullable String getEmail() {
        return email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public @Nullable Date getDate() {
        return date;
    }

    public @Nullable GHUser getUser() {
        return user;
    }
}
