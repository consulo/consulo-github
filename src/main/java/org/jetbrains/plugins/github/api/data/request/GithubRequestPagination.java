// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.request;

public class GithubRequestPagination {
    /**
     * Max page size
     */
    public static final int DEFAULT_PAGE_SIZE = 100;

    public static final GithubRequestPagination DEFAULT = new GithubRequestPagination();

    private final int myPageNumber;
    private final int myPageSize;

    public GithubRequestPagination() {
        this(1);
    }

    public GithubRequestPagination(int pageNumber) {
        this(pageNumber, DEFAULT_PAGE_SIZE);
    }

    public GithubRequestPagination(int pageNumber, int pageSize) {
        myPageNumber = pageNumber;
        myPageSize = pageSize;
    }

    public int getPageNumber() {
        return myPageNumber;
    }

    public int getPageSize() {
        return myPageSize;
    }

    @Override
    public String toString() {
        return "page=" + myPageNumber + "&per_page=" + myPageSize;
    }
}
