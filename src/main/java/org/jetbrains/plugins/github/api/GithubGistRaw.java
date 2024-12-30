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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
class GithubGistRaw implements DataConstructor {
    @Nullable
    public String id;
    @Nullable
    public String description;

    @SerializedName("public")
    @Nullable
    public Boolean isPublic;

    @Nullable
    public String url;
    @Nullable
    public String htmlUrl;
    @Nullable
    public String gitPullUrl;
    @Nullable
    public String gitPushUrl;

    @Nullable
    public Map<String, GistFileRaw> files;

    @Nullable
    public GithubUserRaw user;

    @Nullable
    public Date createdAt;

    public static class GistFileRaw {
        @Nullable
        public Long size;
        @Nullable
        public String filename;
        @Nullable
        public String content;

        @Nullable
        public String raw_url;

        @Nullable
        public String type;
        @Nullable
        public String language;

        @SuppressWarnings("ConstantConditions")
        @Nonnull
        public GithubGist.GistFile create() {
            return new GithubGist.GistFile(filename, content, raw_url);
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull
    public GithubGist createGist() {
        GithubUser user = this.user == null ? null : this.user.createUser();

        List<GithubGist.GistFile> files = new ArrayList<>();
        for (Map.Entry<String, GistFileRaw> entry : this.files.entrySet()) {
            files.add(entry.getValue().create());
        }

        return new GithubGist(id, description, isPublic, htmlUrl, files, user);
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T> T create(@Nonnull Class<T> resultClass) {
        if (resultClass.isAssignableFrom(GithubGist.class)) {
            return (T)createGist();
        }

        throw new ClassCastException(this.getClass().getName() + ": bad class type: " + resultClass.getName());
    }
}
