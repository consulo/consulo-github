// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util;

import git4idea.remote.GitRemoteUrlCoordinates;
import git4idea.remote.hosting.HostedGitRepositoryMapping;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.ui.branch.GitRepositoryMappingData;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jspecify.annotations.Nullable;

public class GHGitRepositoryMapping implements GitRepositoryMappingData, HostedGitRepositoryMapping {
    private final GHRepositoryCoordinates myRepository;
    private final GitRemoteUrlCoordinates myRemote;

    public GHGitRepositoryMapping(GHRepositoryCoordinates repository, GitRemoteUrlCoordinates remote) {
        myRepository = repository;
        myRemote = remote;
    }

    @Override
    public GHRepositoryCoordinates getRepository() {
        return myRepository;
    }

    @Override
    public GitRemoteUrlCoordinates getRemote() {
        return myRemote;
    }

    @Override
    public GitRemote getGitRemote() {
        return myRemote.remote();
    }

    @Override
    public GitRepository getGitRepository() {
        return myRemote.repository();
    }

    @Override
    public String getRepositoryPath() {
        return myRepository.getRepositoryPath().getRepository();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHGitRepositoryMapping that)) {
            return false;
        }

        return myRepository.equals(that.myRepository);
    }

    @Override
    public int hashCode() {
        return myRepository.hashCode();
    }

    @Override
    public String toString() {
        return "(repository=" + myRepository + ", remote=" + myRepository + ")";
    }

    public static @Nullable GHGitRepositoryMapping create(GithubServerPath server, GitRemoteUrlCoordinates remote) {
        GHRepositoryPath repositoryPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remote.url());
        if (repositoryPath == null) {
            return null;
        }
        GHRepositoryCoordinates repository = new GHRepositoryCoordinates(server, repositoryPath);
        return new GHGitRepositoryMapping(repository, remote);
    }
}
