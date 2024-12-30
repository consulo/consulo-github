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

import jakarta.annotation.Nonnull;

/**
 * @author Aleksey Pivovarov
 */
public class GithubFullPath {
    @Nonnull
    private final String myUserName;
    @Nonnull
    private final String myRepositoryName;

    public GithubFullPath(@Nonnull String userName, @Nonnull String repositoryName) {
        myUserName = userName;
        myRepositoryName = repositoryName;
    }

    @Nonnull
    public String getUser() {
        return myUserName;
    }

    @Nonnull
    public String getRepository() {
        return myRepositoryName;
    }

    @Nonnull
    @Override
    public String toString() {
        return myUserName + '/' + myRepositoryName;
    }

    @Override
    public boolean equals(Object o) {
        return this == o
            || o instanceof GithubFullPath that
            && myRepositoryName.equals(that.myRepositoryName)
            && myUserName.equals(that.myUserName);
    }

    @Override
    public int hashCode() {
        int result = myUserName.hashCode();
        result = 31 * result + myRepositoryName.hashCode();
        return result;
    }
}
