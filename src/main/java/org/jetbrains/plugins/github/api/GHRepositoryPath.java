// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api;

import java.util.Locale;

public class GHRepositoryPath {
    private final String myOwner;
    private final String myRepository;

    public GHRepositoryPath(String owner, String repository) {
        myOwner = owner;
        myRepository = repository;
    }

    public String getOwner() {
        return myOwner;
    }

    public String getRepository() {
        return myRepository;
    }

    public String toString(boolean showOwner) {
        return showOwner ? myOwner + "/" + myRepository : myRepository;
    }

    @Override
    public String toString() {
        return myOwner + "/" + myRepository;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHRepositoryPath that)) {
            return false;
        }
        return myOwner.equalsIgnoreCase(that.myOwner)
            && myRepository.equalsIgnoreCase(that.myRepository);
    }

    @Override
    public int hashCode() {
        int result = myOwner.toLowerCase(Locale.ROOT).hashCode();
        result = 31 * result + myRepository.toLowerCase(Locale.ROOT).hashCode();
        return result;
    }
}
