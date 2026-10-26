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

import consulo.accountManagement.AccountFeature;
import consulo.accountManagement.AccountType;
import consulo.annotation.component.ExtensionImpl;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;

import java.util.Set;

@ExtensionImpl
public class GithubRepositoriesFeature implements AccountFeature<GithubScope> {
    @Override
    public String getId() {
        return "github.repositories";
    }

    @Override
    public Class<? extends AccountType<GithubScope>> getAccountType() {
        return GithubAccountType.class;
    }

    @Override
    public LocalizeValue getTitle() {
        return GithubLocalize.accountFeatureRepositoriesTitle();
    }

    @Override
    public LocalizeValue getDescription() {
        return GithubLocalize.accountFeatureRepositoriesDescription();
    }

    @Override
    public Set<GithubScope> getRequiredScopes() {
        return Set.of(GithubScope.REPO);
    }
}
