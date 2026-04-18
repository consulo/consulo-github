// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import java.util.Objects;

public class GHNode {
    private String myId;

    public GHNode() {
    }

    public GHNode(String id) {
        myId = id;
    }

    public String getId() {
        return myId;
    }

    public void setId(String id) {
        myId = id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHNode that)) {
            return false;
        }
        return Objects.equals(myId, that.myId);
    }

    @Override
    public int hashCode() {
        return myId != null ? myId.hashCode() : 0;
    }
}
