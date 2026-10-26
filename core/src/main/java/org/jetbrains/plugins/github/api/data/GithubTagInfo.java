// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.api.data;

import java.util.Objects;

@SuppressWarnings("UnusedDeclaration")
public final class GithubTagInfo {
    private String name;
    private String zipballUrl;

    public String getName() {
        return name;
    }

    public String getZipballUrl() {
        return zipballUrl;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        GithubTagInfo info = (GithubTagInfo) o;
        return Objects.equals(name, info.name) && Objects.equals(zipballUrl, info.zipballUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, zipballUrl);
    }
}
