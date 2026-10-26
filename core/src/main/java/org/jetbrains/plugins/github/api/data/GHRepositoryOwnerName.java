// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import consulo.http.graphql.dto.GraphQLFragment;

@GraphQLFragment("/graphql/fragment/repositoryOwnerName.graphql")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = false,
              defaultImpl = GHRepositoryOwnerName.class)
@JsonSubTypes({
    @JsonSubTypes.Type(name = "User", value = GHRepositoryOwnerName.User.class),
    @JsonSubTypes.Type(name = "Organization", value = GHRepositoryOwnerName.Organization.class)
})
public class GHRepositoryOwnerName {
    private final String login;

    protected GHRepositoryOwnerName() {
        this.login = "";
    }

    public GHRepositoryOwnerName(String login) {
        this.login = login;
    }

    public String getLogin() {
        return login;
    }

    public static class User extends GHRepositoryOwnerName {
        protected User() {
            super();
        }

        public User(String login) {
            super(login);
        }
    }

    public static class Organization extends GHRepositoryOwnerName {
        protected Organization() {
            super();
        }

        public Organization(String login) {
            super(login);
        }
    }
}
