// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

public class GHLabel extends GHNode {
    private String myUrl;
    private String myName;
    private String myColor;

    public GHLabel() {
    }

    public GHLabel(String id, String url, String name, String color) {
        super(id);
        myUrl = url;
        myName = name;
        myColor = color;
    }

    public String getUrl() {
        return myUrl;
    }

    public String getName() {
        return myName;
    }

    public String getColor() {
        return myColor;
    }
}
