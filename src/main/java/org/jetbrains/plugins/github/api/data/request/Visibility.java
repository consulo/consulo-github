// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

/**
 * Defaults to 'all' if not specified.
 */
public enum Visibility {
    ALL("all"),
    PUBLIC("public"),
    PRIVATE("private");

    public static final String KEY = "visibility";

    private final String myValue;

    Visibility(String value) {
        myValue = value;
    }

    public String getValue() {
        return myValue;
    }
}
