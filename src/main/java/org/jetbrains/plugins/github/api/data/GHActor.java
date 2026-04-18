// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

/**
 * Represents a GitHub actor (user, bot, mannequin, organization, etc.).
 */
public interface GHActor {
    String getId();

    String getLogin();

    String getUrl();

    String getAvatarUrl();

    String getPresentableName();
}
