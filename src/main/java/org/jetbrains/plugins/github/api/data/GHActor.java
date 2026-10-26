// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import consulo.http.graphql.dto.GraphQLFragment;
import consulo.review.user.CodeReviewUser;

@GraphQLFragment("/graphql/fragment/actor.graphql")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = false,
    defaultImpl = GHActor.Unknown.class)
@JsonSubTypes({
    @JsonSubTypes.Type(name = "User", value = GHUser.class),
    @JsonSubTypes.Type(name = "Bot", value = GHBot.class),
    @JsonSubTypes.Type(name = "Mannequin", value = GHMannequin.class),
    @JsonSubTypes.Type(name = "Organization", value = GHOrganization.class),
    @JsonSubTypes.Type(name = "EnterpriseUserAccount", value = GHEnterpriseUserAccount.class),
})
public interface GHActor extends CodeReviewUser {
    String getId();

    String getLogin();

    String getUrl();

    @Override
    String getAvatarUrl();

    String getPresentableName();

    class Unknown implements GHActor {
        private final String id;
        private final String login;
        private final String url;
        private final String avatarUrl;

        protected Unknown() {
            this("", "", "", "");
        }

        public Unknown(String id, String login, String url, String avatarUrl) {
            this.id = id;
            this.login = login;
            this.url = url;
            this.avatarUrl = avatarUrl;
        }

        @Override
        public String getId() {
            return id;
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
        public String getPresentableName() {
            return login;
        }
    }
}
