/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api;

import java.util.List;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
class GithubAuthorizationRaw implements DataConstructor {
    @Nullable
    public Long id;
    @Nullable
    public String url;
    @Nullable
    public String token;
    @Nullable
    public String note;
    @Nullable
    public String noteUrl;
    @Nullable
    public List<String> scopes;

    @SuppressWarnings("ConstantConditions")
    public GithubAuthorization createAuthorization() {
        return new GithubAuthorization(token, scopes);
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> T create(@Nonnull Class<T> resultClass) {
        if (resultClass.isAssignableFrom(GithubAuthorization.class)) {
            return (T)createAuthorization();
        }

        throw new ClassCastException(this.getClass().getName() + ": bad class type: " + resultClass.getName());
    }
}
