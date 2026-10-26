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

import consulo.accountManagement.AccountScope;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;

import java.util.function.Supplier;

public enum GithubScope implements AccountScope {
    REPO("repo", GithubLocalize::accountScopeRepo),
    GIST("gist", GithubLocalize::accountScopeGist),
    READ_ORG("read:org", GithubLocalize::accountScopeReadOrg),
    WORKFLOW("workflow", GithubLocalize::accountScopeWorkflow),
    READ_USER("read:user", GithubLocalize::accountScopeReadUser),
    USER_EMAIL("user:email", GithubLocalize::accountScopeUserEmail);

    private final String myId;
    private final Supplier<LocalizeValue> myDescription;

    GithubScope(String id, Supplier<LocalizeValue> description) {
        myId = id;
        myDescription = description;
    }

    @Override
    public String getId() {
        return myId;
    }

    @Override
    public LocalizeValue getDescription() {
        return myDescription.get();
    }
}
