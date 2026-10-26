// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import consulo.http.graphql.dto.GraphQLFragment;

@GraphQLFragment("/graphql/fragment/labelInfo.graphql")
public class GHLabel extends GHNode {
    private final String url;
    private final String name;
    private final String color;

    protected GHLabel() {
        super("");
        this.url = "";
        this.name = "";
        this.color = "";
    }

    public GHLabel(String id, String url, String name, String color) {
        super(id);
        this.url = url;
        this.name = name;
        this.color = color;
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    static GHLabel createTest(String id) {
        return new GHLabel(id, "", "testLabel_" + id, "#FFFFFF");
    }
}
