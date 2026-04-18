// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api;

import java.net.URI;

public class GHRepositoryCoordinates {
    private final GithubServerPath myServerPath;
    private final GHRepositoryPath myRepositoryPath;

    public GHRepositoryCoordinates(GithubServerPath serverPath, GHRepositoryPath repositoryPath) {
        myServerPath = serverPath;
        myRepositoryPath = repositoryPath;
    }

    public GithubServerPath getServerPath() {
        return myServerPath;
    }

    public GHRepositoryPath getRepositoryPath() {
        return myRepositoryPath;
    }

    public String toUrl() {
        return myServerPath.toUrl() + "/" + myRepositoryPath;
    }

    public URI toURI() {
        return myServerPath.toURI().resolve("/" + myRepositoryPath);
    }

    @Override
    public String toString() {
        return myServerPath + "/" + myRepositoryPath;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHRepositoryCoordinates that)) {
            return false;
        }
        return myServerPath.equals(that.myServerPath, true)
            && myRepositoryPath.equals(that.myRepositoryPath);
    }

    @Override
    public int hashCode() {
        int result = myServerPath.hashCode();
        result = 31 * result + myRepositoryPath.hashCode();
        return result;
    }
}
