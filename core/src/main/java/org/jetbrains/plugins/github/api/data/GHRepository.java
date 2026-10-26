// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.plugins.github.api.GHRepositoryPath;
import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.data.pullrequest.GHGitRefName;
import org.jspecify.annotations.Nullable;

@GraphQLFragment("/graphql/fragment/repository.graphql")
public class GHRepository extends GHNode {
    private final GHRepositoryOwnerName owner;
    private final String nameWithOwner;
    private final @Nullable GHRepositoryPermissionLevel viewerPermission;
    private final boolean mergeCommitAllowed;
    private final boolean squashMergeAllowed;
    private final boolean rebaseMergeAllowed;
    private final @Nullable GHGitRefName defaultBranchRef;
    private final boolean isFork;
    private final String url;
    private final String sshUrl;

    protected GHRepository() {
        super("");
        this.owner = null;
        this.nameWithOwner = "";
        this.viewerPermission = null;
        this.mergeCommitAllowed = false;
        this.squashMergeAllowed = false;
        this.rebaseMergeAllowed = false;
        this.defaultBranchRef = null;
        this.isFork = false;
        this.url = "";
        this.sshUrl = "";
    }

    public GHRepository(String id,
                        GHRepositoryOwnerName owner,
                        String nameWithOwner,
                        @Nullable GHRepositoryPermissionLevel viewerPermission,
                        boolean mergeCommitAllowed,
                        boolean squashMergeAllowed,
                        boolean rebaseMergeAllowed,
                        @Nullable GHGitRefName defaultBranchRef,
                        boolean isFork,
                        String url,
                        String sshUrl) {
        super(id);
        this.owner = owner;
        this.nameWithOwner = nameWithOwner;
        this.viewerPermission = viewerPermission;
        this.mergeCommitAllowed = mergeCommitAllowed;
        this.squashMergeAllowed = squashMergeAllowed;
        this.rebaseMergeAllowed = rebaseMergeAllowed;
        this.defaultBranchRef = defaultBranchRef;
        this.isFork = isFork;
        this.url = url;
        this.sshUrl = sshUrl;
    }

    public GHRepositoryOwnerName getOwner() {
        return owner;
    }

    public String getNameWithOwner() {
        return nameWithOwner;
    }

    public @Nullable GHRepositoryPermissionLevel getViewerPermission() {
        return viewerPermission;
    }

    public boolean getMergeCommitAllowed() {
        return mergeCommitAllowed;
    }

    public boolean getSquashMergeAllowed() {
        return squashMergeAllowed;
    }

    public boolean getRebaseMergeAllowed() {
        return rebaseMergeAllowed;
    }

    public @Nullable GHGitRefName getDefaultBranchRef() {
        return defaultBranchRef;
    }

    public boolean isFork() {
        return isFork;
    }

    public String getUrl() {
        return url;
    }

    public String getSshUrl() {
        return sshUrl;
    }

    public GHRepositoryPath getPath() {
        String[] split = nameWithOwner.split("/");
        return new GHRepositoryPath(split[0], split[1]);
    }

    public @Nullable String getDefaultBranch() {
        return defaultBranchRef == null ? null : defaultBranchRef.getName();
    }
}
