// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

public enum GHReactionContent {
    THUMBS_UP,
    THUMBS_DOWN,
    LAUGH,
    HOORAY,
    CONFUSED,
    HEART,
    ROCKET,
    EYES;

    public String getPresentableName() {
        return switch (this) {
            case THUMBS_UP -> "thumbs up";
            case THUMBS_DOWN -> "thumbs down";
            case LAUGH -> "laugh";
            case HOORAY -> "hooray";
            case CONFUSED -> "confused";
            case HEART -> "heart";
            case ROCKET -> "rocket";
            case EYES -> "eyes";
        };
    }
}
