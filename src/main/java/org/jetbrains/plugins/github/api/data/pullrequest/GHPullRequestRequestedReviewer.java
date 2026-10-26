// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.data.GHBot;
import org.jetbrains.plugins.github.api.data.GHMannequin;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jspecify.annotations.Nullable;

@GraphQLFragment("/graphql/fragment/pullRequestReviewer.graphql")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = false,
    defaultImpl = GHPullRequestRequestedReviewer.Unknown.class)
@JsonSubTypes({
    @JsonSubTypes.Type(name = "User", value = GHUser.class),
    @JsonSubTypes.Type(name = "Bot", value = GHBot.class),
    @JsonSubTypes.Type(name = "Mannequin", value = GHMannequin.class),
    @JsonSubTypes.Type(name = "Team", value = GHTeam.class)
})
public interface GHPullRequestRequestedReviewer {
    String getId();

    String getShortName();

    String getUrl();

    @Nullable
    String getAvatarUrl();

    @Nullable
    String getName();

    default String getPresentableName() {
        String name = getName();
        return name != null ? name : getShortName();
    }

    class Unknown implements GHPullRequestRequestedReviewer {
        private final String id;
        private final String url;
        private final String avatarUrl;
        private final @Nullable String name;

        protected Unknown() {
            this("", "", "", null);
        }

        public Unknown(String id, String url, String avatarUrl, @Nullable String name) {
            this.id = id;
            this.url = url;
            this.avatarUrl = avatarUrl;
            this.name = name;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getShortName() {
            return id;
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
    }
}
