// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github.pullrequest.data.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.http.page.ApiPageUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.util.FutureUtil;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.change.patch.BinaryFilePatch;
import consulo.versionControlSystem.change.patch.FilePatch;
import consulo.versionControlSystem.change.patch.PatchReader;
import consulo.versionControlSystem.change.patch.PatchSyntaxException;
import consulo.versionControlSystem.change.patch.TextFilePatch;
import consulo.virtualFileSystem.status.FileStatus;
import git4idea.changes.GitBranchComparisonResult;
import git4idea.changes.GitCommitShaWithPatches;
import git4idea.remote.GitRemoteUrlCoordinates;
import git4idea.remote.hosting.GitCodeReviewUtils;
import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.data.GHCommit;
import org.jetbrains.plugins.github.api.data.GHCommitHash;
import org.jetbrains.plugins.github.api.data.commit.GHCommitFile;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommit;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static consulo.review.util.ResultUtil.processErrorAndGet;
import static consulo.review.util.ResultUtil.runCatchingUser;

public class GHPRChangesServiceImpl implements GHPRChangesService {
    private static final Logger LOG = Logger.getInstance(GHPRChangesService.class);

    static final int MAX_CONCURRENT_COMMITS = 50;

    private final Project myProject;
    private final GithubApiRequestExecutor myRequestExecutor;
    private final GitRemoteUrlCoordinates myGitRemote;
    private final GHRepositoryCoordinates myGhRepository;

    private final LoadingCache<Pair<@Nullable String, String>, CompletableFuture<List<FilePatch>>> myPatchesCache = CacheBuilder.newBuilder()
        .expireAfterAccess(Duration.ofMinutes(5))
        .build(new CacheLoader<>() {
            @Override
            public CompletableFuture<List<FilePatch>> load(Pair<@Nullable String, String> spec) {
                String ref1 = spec.getFirst();
                String ref2 = spec.getSecond();
                if (ref1 == null) {
                    return loadCommitDiff(ref2);
                }
                else {
                    return loadDiff(ref1, ref2).thenApplyAsync(GHPRChangesServiceImpl::readAllPatches, AppExecutorUtil.getAppExecutorService());
                }
            }
        });

    public GHPRChangesServiceImpl(Project project,
                                  GithubApiRequestExecutor requestExecutor,
                                  GitRemoteUrlCoordinates gitRemote,
                                  GHRepositoryCoordinates ghRepository) {
        myProject = project;
        myRequestExecutor = requestExecutor;
        myGitRemote = gitRemote;
        myGhRepository = ghRepository;
    }

    @Override
    public CompletableFuture<?> fetch(String refspec) {
        return processErrorAndGet(runCatchingUser(() -> GitCodeReviewUtils.fetch(myGitRemote.repository(), myGitRemote.remote(), refspec)),
            e -> LOG.info("Error occurred while fetching \"" + refspec + "\"", e));
    }

    @Override
    public CompletableFuture<Boolean> areAllRevisionsFetched(List<String> revisions) {
        return processErrorAndGet(runCatchingUser(() -> GitCodeReviewUtils.testRevisionsExist(myGitRemote.repository(), revisions)),
            e -> LOG.info("Error occurred while seeking revisions " + revisions, e));
    }

    @Override
    public CompletableFuture<List<GHCommit>> loadCommitsFromApi(GHPRIdentifier pullRequestId) {
        return processErrorAndGet(runCatchingUser(() -> ApiPageUtil.foldGQLPagesToList(
                    pagination -> myRequestExecutor.execute(GHGQLRequests.PullRequest.commits(myGhRepository, pullRequestId.number(), pagination)))
                .thenApply(commits -> commits.stream().map(GHPullRequestCommit::getCommit).toList())),
            e -> LOG.info("Error occurred while loading commits for PR " + pullRequestId.number(), e));
    }

    private CompletableFuture<List<FilePatch>> loadCommitDiff(String oid) {
        return processErrorAndGet(runCatchingUser(() -> {
            GithubApiPagesLoader.Request<GHCommitFile> request = new GithubApiPagesLoader.Request<>(
                GithubApiRequests.Repos.Commits.getDiffFiles(myGhRepository, oid), GithubApiRequests.Repos.Commits::getDiffFiles);
            return GithubApiPagesLoader.loadAll(myRequestExecutor, request)
                .thenApply(files -> files.stream().map(GHPRChangesServiceImpl::toPatch).filter(Objects::nonNull).toList());
        }), e -> LOG.info("Error occurred while loading diffs for commit " + oid, e));
    }

    private CompletableFuture<String> loadDiff(String ref1, String ref2) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(GithubApiRequests.Repos.Commits.getDiff(myGhRepository, ref1, ref2))),
            e -> LOG.info("Error occurred while loading diffs between " + ref1 + " and " + ref2, e));
    }

    @Override
    public CompletableFuture<String> loadMergeBaseOid(String baseRefOid, String headRefOid) {
        return processErrorAndGet(runCatchingUser(() -> myRequestExecutor.execute(GithubApiRequests.Repos.Commits.compare(myGhRepository, baseRefOid, headRefOid))
                .thenApply(result -> result.getMergeBaseCommit().getSha())),
            e -> LOG.info("Error occurred while calculating merge base for " + baseRefOid + " and " + headRefOid, e));
    }

    @Override
    public CompletableFuture<List<FilePatch>> loadPatch(String ref1, String ref2) {
        return myPatchesCache.getUnchecked(Pair.create(ref1, ref2));
    }

    @Override
    public CompletableFuture<GitBranchComparisonResult> createChangesProvider(GHPRIdentifier id,
                                                                              String baseRef,
                                                                              String mergeBaseRef,
                                                                              String headRef,
                                                                              List<GHCommit> commits) {
        return processErrorAndGet(runCatchingUser(() -> {
            CompletableFuture<List<GitCommitShaWithPatches>> commitsWithPatchesReq =
                FutureUtil.mapConcurrentOrdered(commits, MAX_CONCURRENT_COMMITS, commit ->
                    myPatchesCache.getUnchecked(Pair.create(null, commit.getOid())).thenApply(patches ->
                        new GitCommitShaWithPatches(commit.getOid(), commit.getParents().stream().map(GHCommitHash::getOid).toList(), patches)));
            GithubApiPagesLoader.Request<GHCommitFile> request = new GithubApiPagesLoader.Request<>(
                GithubApiRequests.Repos.PullRequests.getDiffFiles(myGhRepository, id), GithubApiRequests.Repos.PullRequests::getDiffFiles);
            CompletableFuture<List<FilePatch>> prPatchesReq = GithubApiPagesLoader.loadAll(myRequestExecutor, request)
                .thenApply(files -> files.stream().map(GHPRChangesServiceImpl::toPatch).filter(Objects::nonNull).toList());

            return prPatchesReq.thenCombine(commitsWithPatchesReq, (prPatches, commitsWithPatches) ->
                GitBranchComparisonResult.create(myProject, myGitRemote.repository().getRoot(), baseRef, mergeBaseRef, commitsWithPatches, prPatches));
        }), e -> LOG.info("Error occurred while building changes from commits", e));
    }

    private static List<FilePatch> readAllPatches(String diffFile) {
        PatchReader reader = new PatchReader(diffFile, true);
        try {
            reader.parseAllPatches();
        }
        catch (PatchSyntaxException e) {
            throw new CompletionException(e);
        }
        return new ArrayList<>(reader.getPatches());
    }

    private static @Nullable FilePatch toPatch(GHCommitFile file) {
        String previousFilename = file.getPreviousFilename();
        String beforeFilePath = file.getStatus() != GHCommitFile.Status.added
            ? (previousFilename != null ? previousFilename : file.getFilename())
            : null;
        String afterFilePath = file.getStatus() != GHCommitFile.Status.removed ? file.getFilename() : null;
        FilePatch filePatch;
        String patchText = file.getPatch();
        if (patchText == null) {
            byte[] emptyContent = new byte[0];
            filePatch = new BinaryFilePatch(
                file.getStatus() != GHCommitFile.Status.added ? emptyContent : null,
                file.getStatus() != GHCommitFile.Status.removed ? emptyContent : null
            );
        }
        else {
            String headerFileBefore = beforeFilePath != null ? "a/" + beforeFilePath : "/dev/null";
            String headerFileAfter = afterFilePath != null ? "b/" + afterFilePath : "/dev/null";
            String header = "--- " + headerFileBefore + "\n+++ " + headerFileAfter + "\n";
            String patch = header + patchText;
            TextFilePatch textPatch = null;
            for (FilePatch it : readAllPatches(patch)) {
                if (it instanceof TextFilePatch text) {
                    textPatch = text;
                    break;
                }
            }
            if (textPatch != null) {
                FileStatus fileStatus = switch (file.getStatus()) {
                    case added -> FileStatus.ADDED;
                    case removed -> FileStatus.DELETED;
                    default -> FileStatus.MODIFIED;
                };
                textPatch.setFileStatus(fileStatus);
            }
            filePatch = textPatch;
        }
        if (filePatch != null) {
            filePatch.setBeforeName(beforeFilePath);
            filePatch.setAfterName(afterFilePath);
        }
        return filePatch;
    }
}
