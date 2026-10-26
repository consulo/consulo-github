// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api;

import consulo.http.client.URIUtil;
import git4idea.remote.hosting.HostedRepositoryCoordinates;

import java.net.URI;

public class GHRepositoryCoordinates implements HostedRepositoryCoordinates {
    private final GithubServerPath myServerPath;
    private final GHRepositoryPath myRepositoryPath;

    public GHRepositoryCoordinates(GithubServerPath serverPath, GHRepositoryPath repositoryPath) {
        myServerPath = serverPath;
        myRepositoryPath = repositoryPath;
    }

    @Override
    public GithubServerPath getServerPath() {
        return myServerPath;
    }

    public GHRepositoryPath getRepositoryPath() {
        return myRepositoryPath;
    }

    public String toUrl() {
        return myServerPath.toUrl() + "/" + myRepositoryPath;
    }

    @Override
    public URI getWebURI() {
        return URIUtil.resolveRelative(myServerPath.toURI(), myRepositoryPath.toString());
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

        if (!myServerPath.equals(that.myServerPath, true)) {
            return false;
        }
        if (!myRepositoryPath.equals(that.myRepositoryPath)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = myServerPath.hashCode();
        result = 31 * result + myRepositoryPath.hashCode();
        return result;
    }
}
