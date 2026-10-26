// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.util;

import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public class GithubApiSearchQueryBuilder {
    private final StringBuilder myBuilder = new StringBuilder();

    public void term(GHPRSearchQuery.Term<?> term) {
        String value = term.getApiValue();
        if (value == null) {
            return;
        }
        if (!value.isEmpty()) {
            append(term.toString());
        }
    }

    public void query(@Nullable String value) {
        if (value != null) {
            append(value);
        }
    }

    private void append(String part) {
        if (!myBuilder.isEmpty()) {
            myBuilder.append(" ");
        }
        myBuilder.append(part);
    }

    public static String searchQuery(Consumer<GithubApiSearchQueryBuilder> init) {
        GithubApiSearchQueryBuilder query = new GithubApiSearchQueryBuilder();
        init.accept(query);
        return query.myBuilder.toString();
    }
}
