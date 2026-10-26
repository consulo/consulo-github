// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.request;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defaults to 'owner,collaborator,organization_member' if not specified.
 */
public enum Affiliation {
    OWNER("owner"),
    COLLABORATOR("collaborator"),
    ORG_MEMBER("organization_member");

    public static final String KEY = "affiliation";
    public static final Set<Affiliation> ALL = EnumSet.allOf(Affiliation.class);

    private final String myValue;

    Affiliation(String value) {
        myValue = value;
    }

    public String getValue() {
        return myValue;
    }

    public static String combine(Set<Affiliation> affiliations) {
        return affiliations.stream().map(Affiliation::getValue).collect(Collectors.joining(","));
    }

    public static String combine(Affiliation... affiliations) {
        return combine(Set.of(affiliations));
    }
}
