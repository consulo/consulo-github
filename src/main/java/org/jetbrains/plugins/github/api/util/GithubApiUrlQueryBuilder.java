// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util;

import consulo.util.io.URLUtil;
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public class GithubApiUrlQueryBuilder {
    private final StringBuilder myBuilder = new StringBuilder();

    public void param(String name, @Nullable String value) {
        if (value != null) {
            append(URLUtil.encodeURIComponent(name) + "=" + URLUtil.encodeURIComponent(value));
        }
    }

    public void param(@Nullable GithubRequestPagination pagination) {
        if (pagination != null) {
            param("page", String.valueOf(pagination.getPageNumber()));
            param("per_page", String.valueOf(pagination.getPageSize()));
        }
    }

    private void append(String part) {
        if (myBuilder.isEmpty()) {
            myBuilder.append("?");
        }
        else {
            myBuilder.append("&");
        }
        myBuilder.append(part);
    }

    public static String urlQuery(Consumer<GithubApiUrlQueryBuilder> init) {
        GithubApiUrlQueryBuilder query = new GithubApiUrlQueryBuilder();
        init.accept(query);
        return query.myBuilder.toString();
    }

    public static String paginationQuery(@Nullable GithubRequestPagination pagination) {
        return urlQuery(it -> it.param(pagination));
    }
}
