// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record GHPRIdentifier(String id, long number) {
    @JsonCreator
    public GHPRIdentifier(@JsonProperty("id") String id, @JsonProperty("number") long number) {
        this.id = id;
        this.number = number;
    }
}
