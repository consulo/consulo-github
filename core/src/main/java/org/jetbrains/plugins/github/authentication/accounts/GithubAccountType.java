/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github.authentication.accounts;

import consulo.accountManagement.AccountAuthorizeConfig;
import consulo.accountManagement.AccountConnection;
import consulo.accountManagement.AccountDetails;
import consulo.accountManagement.AccountScope;
import consulo.accountManagement.AccountType;
import consulo.accountManagement.authorize.OAuthAuthorizeConfig;
import consulo.accountManagement.authorize.TokenAuthorizeConfig;
import consulo.accountManagement.oauth.OAuthCredentials;
import consulo.annotation.component.ExtensionImpl;
import consulo.github.icon.GitHubIconGroup;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubApiResponse;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser;
import org.jetbrains.plugins.github.authentication.AuthorizationType;
import org.jetbrains.plugins.github.authentication.GHOAuthService;
import org.jetbrains.plugins.github.authentication.util.GHSecurityUtil;
import org.jetbrains.plugins.github.exceptions.GithubParseException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * GitHub and GitHub Enterprise accounts
 */
@ExtensionImpl
public class GithubAccountType implements AccountType<GithubScope> {
    public static final String ID = "github";

    private static final String OAUTH_SCOPES_HEADER = "X-OAuth-Scopes";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LocalizeValue getName() {
        return GithubLocalize.accountTypeName();
    }

    @Override
    public Image getIcon() {
        return GitHubIconGroup.github();
    }

    @Override
    public Class<GithubScope> getScopeClass() {
        return GithubScope.class;
    }

    @Override
    public Set<GithubScope> getBaseScopes() {
        return Set.of(GithubScope.READ_USER);
    }

    @Override
    public String getDefaultServerUrl() {
        return GithubServerPath.DEFAULT_SERVER.toUrl();
    }

    @Override
    public boolean isServerEditable() {
        return true;
    }

    @Override
    public List<? extends AccountAuthorizeConfig> getAuthorizeConfigs() {
        List<AccountAuthorizeConfig> configs = new ArrayList<>();
        configs.add(new TokenAuthorizeConfig() {
            @Override
            public boolean isDefault() {
                return true;
            }

            @Override
            public @Nullable String buildNewTokenUrl(@Nullable String serverUrl, Set<? extends AccountScope> scopes) {
                List<String> ids = new ArrayList<>();
                for (AccountScope scope : scopes) {
                    ids.add(scope.getId());
                }
                return GHSecurityUtil.buildNewTokenUrl(toServerPath(serverUrl), ids.isEmpty() ? GHSecurityUtil.MASTER_SCOPES : ids);
            }
        });
        if (GHOAuthService.isAvailable()) {
            configs.add(new OAuthAuthorizeConfig() {
                @Override
                public CompletableFuture<? extends OAuthCredentials> authorize(@Nullable String serverUrl, Set<? extends AccountScope> scopes) {
                    if (!toServerPath(serverUrl).isGithubDotCom()) {
                        return CompletableFuture.failedFuture(
                            new IllegalArgumentException(GithubLocalize.loginOauthGithubComOnly().get()));
                    }
                    return GHOAuthService.getInstance().authorize();
                }
            });
        }
        return configs;
    }

    public static @Nullable AccountAuthorizeConfig findAuthorizeConfig(List<? extends AccountAuthorizeConfig> configs, AuthorizationType authType) {
        for (AccountAuthorizeConfig config : configs) {
            if (authType == AuthorizationType.OAUTH && config instanceof OAuthAuthorizeConfig) {
                return config;
            }
            if (authType == AuthorizationType.TOKEN && config instanceof TokenAuthorizeConfig) {
                return config;
            }
        }
        return null;
    }

    @Override
    public CompletableFuture<AccountDetails> loadDetails(AccountConnection connection) {
        GithubServerPath server = toServerPath(connection.getServerUrl());
        GithubApiRequestExecutor executor = GithubApiRequestExecutorFactory.getInstance().create(server, connection);
        return executor.execute(GithubApiRequests.CurrentUser.get(server)).thenApply(GithubAccountType::toDetails);
    }

    @Override
    public CompletableFuture<Set<GithubScope>> loadGrantedScopes(AccountConnection connection, Set<GithubScope> requested) {
        GithubServerPath server = toServerPath(connection.getServerUrl());
        GithubApiRequestExecutor executor = GithubApiRequestExecutorFactory.getInstance().create(server, connection);
        GithubApiRequest<@Nullable String> request = new GithubApiRequest.Get<>(GithubApiRequests.getUrl(server, "/user")) {
            @Override
            public @Nullable String extractResult(GithubApiResponse response) {
                return response.findHeader(OAUTH_SCOPES_HEADER);
            }
        };
        return executor.execute(request).thenApply(header -> {
            if (header == null) {
                return requested;
            }
            Set<GithubScope> granted = new LinkedHashSet<>();
            for (String id : header.split(",")) {
                String trimmed = id.trim();
                for (GithubScope scope : GithubScope.values()) {
                    if (scope.getId().equals(trimmed)) {
                        granted.add(scope);
                    }
                }
                if (trimmed.equals("user")) {
                    granted.add(GithubScope.READ_USER);
                    granted.add(GithubScope.USER_EMAIL);
                }
                if (trimmed.equals("admin:org") || trimmed.equals("write:org")) {
                    granted.add(GithubScope.READ_ORG);
                }
            }
            return granted;
        });
    }

    private static AccountDetails toDetails(GithubAuthenticatedUser user) {
        String name = user.getName();
        return new AccountDetails(name == null || name.isEmpty() ? user.getLogin() : name, user.getLogin(), user.getAvatarUrl());
    }

    public static GithubServerPath toServerPath(@Nullable String serverUrl) {
        if (serverUrl == null || serverUrl.isEmpty()) {
            return GithubServerPath.DEFAULT_SERVER;
        }
        try {
            return GithubServerPath.from(serverUrl);
        }
        catch (GithubParseException e) {
            return GithubServerPath.DEFAULT_SERVER;
        }
    }
}
