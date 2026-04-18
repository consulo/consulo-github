// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data;

import java.util.Objects;

public final class GHPRIdentifier {
    private final String myId;
    private final long myNumber;

    public GHPRIdentifier(String id, long number) {
        myId = id;
        myNumber = number;
    }

    public String getId() {
        return myId;
    }

    public long getNumber() {
        return myNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GHPRIdentifier that)) {
            return false;
        }
        return myNumber == that.myNumber && Objects.equals(myId, that.myId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myId, myNumber);
    }

    @Override
    public String toString() {
        return "#" + myNumber;
    }
}
