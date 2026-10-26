// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import consulo.http.page.LinkHttpHeaderValue;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class GithubResponsePage<T> {
    private final List<T> myItems;
    private final @Nullable String myFirstLink;
    private final @Nullable String myPrevLink;
    private final @Nullable String myNextLink;
    private final @Nullable String myLastLink;

    public GithubResponsePage(List<T> items) {
        this(items, null, null, null, null);
    }

    public GithubResponsePage(List<T> items,
                              @Nullable String firstLink,
                              @Nullable String prevLink,
                              @Nullable String nextLink,
                              @Nullable String lastLink) {
        myItems = items;
        myFirstLink = firstLink;
        myPrevLink = prevLink;
        myNextLink = nextLink;
        myLastLink = lastLink;
    }

    public GithubResponsePage(List<T> items, @Nullable LinkHttpHeaderValue linkHeaderValue) {
        this(items,
            linkHeaderValue == null ? null : linkHeaderValue.firstLink(),
            linkHeaderValue == null ? null : linkHeaderValue.prevLink(),
            linkHeaderValue == null ? null : linkHeaderValue.nextLink(),
            linkHeaderValue == null ? null : linkHeaderValue.lastLink());
    }

    public List<T> getItems() {
        return myItems;
    }

    public @Nullable String getFirstLink() {
        return myFirstLink;
    }

    public @Nullable String getPrevLink() {
        return myPrevLink;
    }

    public @Nullable String getNextLink() {
        return myNextLink;
    }

    public @Nullable String getLastLink() {
        return myLastLink;
    }

    public boolean getHasNext() {
        return myNextLink != null;
    }
}
