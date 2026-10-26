// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

/**
 * Additional params for <a href="https://developer.github.com/v3/repos/#list-your-repositories">GET /user/repos</a> request
 * <p>
 * Defaults to 'all' if not specified.
 */
public enum Type {
    ALL("all"),
    OWNER("owner"),
    PUBLIC("public"),
    PRIVATE("private"),
    MEMBER("member");

    public static final String KEY = "type";

    private final String myValue;

    Type(String value) {
        myValue = value;
    }

    public String getValue() {
        return myValue;
    }
}
