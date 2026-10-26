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
package org.jetbrains.plugins.github.pullrequest.data.provider;

import com.google.common.graph.GraphBuilder;
import com.google.common.graph.ImmutableGraph;
import com.google.common.graph.Traverser;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.versionControlSystem.change.patch.FilePatch;
import git4idea.changes.GitBranchComparisonResult;
import org.jetbrains.plugins.github.api.data.GHCommit;
import org.jetbrains.plugins.github.api.data.GHCommitHash;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRChangesService;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public class GHPRChangesDataProviderImpl implements GHPRChangesDataProvider {
    private final GHPRChangesService myChangesService;
    private final Supplier<CompletableFuture<GHPRBranchesRefs>> myLoadReferences;
    private final GHPRIdentifier myPullRequestId;

    private @Nullable ChangesDataLoader myRequests;
    private final List<Runnable> myChangesNeedReloadListeners = new CopyOnWriteArrayList<>();

    public GHPRChangesDataProviderImpl(Disposable parentDisposable,
                                       GHPRChangesService changesService,
                                       Supplier<CompletableFuture<GHPRBranchesRefs>> loadReferences,
                                       GHPRIdentifier pullRequestId) {
        myChangesService = changesService;
        myLoadReferences = loadReferences;
        myPullRequestId = pullRequestId;
        Disposer.register(parentDisposable, () -> {
            ChangesDataLoader requests;
            synchronized (this) {
                requests = myRequests;
                myRequests = null;
            }
            if (requests != null) {
                requests.cancel();
            }
        });
    }

    @Override
    public void addChangesNeedReloadListener(Disposable parentDisposable, Runnable listener) {
        myChangesNeedReloadListeners.add(listener);
        Disposer.register(parentDisposable, () -> myChangesNeedReloadListeners.remove(listener));
    }

    @Override
    public CompletableFuture<List<GHCommit>> loadCommits() {
        return myLoadReferences.get().thenCompose(refs -> getLoader(refs).loadCommits());
    }

    @Override
    public CompletableFuture<GitBranchComparisonResult> loadChanges() {
        return myLoadReferences.get().thenCompose(refs -> getLoader(refs).loadChanges());
    }

    @Override
    public CompletableFuture<?> ensureAllRevisionsFetched() {
        return myLoadReferences.get().thenCompose(refs -> getLoader(refs).fetchRevisions());
    }

    private synchronized ChangesDataLoader getLoader(GHPRBranchesRefs refs) {
        ChangesDataLoader current = myRequests;
        ChangesDataLoader result;
        if (current == null) {
            result = new ChangesDataLoader(refs);
        }
        else if (!current.myRefs.equals(refs)) {
            current.cancel();
            result = new ChangesDataLoader(refs);
        }
        else {
            result = current;
        }
        myRequests = result;
        return result;
    }

    @Override
    public void signalChangesNeedReload() {
        synchronized (this) {
            if (myRequests != null) {
                myRequests.cancel();
            }
            myRequests = null;
        }
        myChangesNeedReloadListeners.forEach(Runnable::run);
    }

    @Override
    public CompletableFuture<@Nullable FilePatch> loadPatchFromMergeBase(String commitSha, String filePath) {
        // cache merge base
        return myLoadReferences.get()
            .thenCompose(refs -> myChangesService.loadMergeBaseOid(refs.baseRef(), refs.headRef()))
            .thenCompose(mergeBase -> myChangesService.loadPatch(mergeBase, commitSha))
            .thenApply(patches -> {
                for (FilePatch patch : patches) {
                    if (GitBranchComparisonResult.getFilePath(patch).equals(filePath)) {
                        return patch;
                    }
                }
                return null;
            });
    }

    private final class ChangesDataLoader {
        private final GHPRBranchesRefs myRefs;

        private @Nullable CompletableFuture<AllPRReferences> myReferencesRequest;
        private @Nullable CompletableFuture<GitBranchComparisonResult> myChangesRequest;
        private @Nullable CompletableFuture<?> myFetchRequest;

        private ChangesDataLoader(GHPRBranchesRefs refs) {
            myRefs = refs;
        }

        private synchronized CompletableFuture<AllPRReferences> referencesRequest() {
            if (myReferencesRequest == null) {
                myReferencesRequest = myChangesService.loadMergeBaseOid(myRefs.baseRef(), myRefs.headRef())
                    .thenCompose(mergeBaseRef -> myChangesService.loadCommitsFromApi(myPullRequestId).thenApply(commits -> {
                        List<GHCommit> sortedCommits = sortCommits(commits, myRefs.headRef());
                        return new AllPRReferences(myRefs.baseRef(), mergeBaseRef, myRefs.headRef(), sortedCommits);
                    }));
            }
            return myReferencesRequest;
        }

        private synchronized CompletableFuture<GitBranchComparisonResult> changesRequest() {
            if (myChangesRequest == null) {
                myChangesRequest = referencesRequest().thenCompose(references -> myChangesService.createChangesProvider(myPullRequestId,
                    references.baseRefOid(), references.mergeBaseRefOid(), references.headRefOid(), references.commits()));
            }
            return myChangesRequest;
        }

        private synchronized CompletableFuture<?> fetchRequest() {
            if (myFetchRequest == null) {
                myFetchRequest = referencesRequest().thenCompose(references -> {
                    List<String> revisions = new ArrayList<>();
                    revisions.add(references.baseRefOid());
                    for (GHCommit commit : references.commits()) {
                        revisions.add(commit.getOid());
                    }
                    return myChangesService.areAllRevisionsFetched(revisions).thenCompose(fetched -> {
                        if (fetched) {
                            return CompletableFuture.completedFuture(null);
                        }
                        return CompletableFuture.allOf(
                                myChangesService.fetch(references.baseRefOid()),
                                myChangesService.fetch("refs/pull/" + myPullRequestId.number() + "/head:"))
                            .thenCompose(ignored -> myChangesService.areAllRevisionsFetched(revisions))
                            .thenApply(allFetched -> {
                                if (!allFetched) {
                                    throw new IllegalStateException("Missing some pull request revisions");
                                }
                                return null;
                            });
                    });
                });
            }
            return myFetchRequest;
        }

        CompletableFuture<List<GHCommit>> loadCommits() {
            return referencesRequest().thenApply(AllPRReferences::commits);
        }

        CompletableFuture<GitBranchComparisonResult> loadChanges() {
            return changesRequest();
        }

        CompletableFuture<?> fetchRevisions() {
            return fetchRequest();
        }

        synchronized void cancel() {
            if (myReferencesRequest != null) {
                myReferencesRequest.cancel(true);
            }
            if (myChangesRequest != null) {
                myChangesRequest.cancel(true);
            }
        }
    }

    private record AllPRReferences(String baseRefOid, String mergeBaseRefOid, String headRefOid, List<GHCommit> commits) {
    }

    // TODO: can we get rid of the tree?
    private static List<GHCommit> sortCommits(Collection<GHCommit> commits, String lastCommitSha) {
        Map<String, GHCommit> commitsBySha = new HashMap<>();

        GHCommit lastCommit = null;
        for (GHCommit commit : commits) {
            if (commit.getOid().equals(lastCommitSha)) {
                lastCommit = commit;
            }
            commitsBySha.put(commit.getOid(), commit);
        }
        if (lastCommit == null) {
            throw new IllegalStateException("Could not determine last commit");
        }

        Set<String> processedCommits = new HashSet<>();
        ImmutableGraph.Builder<GHCommit> builder = GraphBuilder.directed().allowsSelfLoops(false).immutable();
        addCommits(builder, lastCommit, commitsBySha, processedCommits);
        ImmutableGraph<GHCommit> graph = builder.build();
        List<GHCommit> result = new ArrayList<>();
        Traverser.forGraph(graph).depthFirstPostOrder(lastCommit).forEach(result::add);
        return result;
    }

    private static void addCommits(ImmutableGraph.Builder<GHCommit> builder,
                                   GHCommit commit,
                                   Map<String, GHCommit> commitsBySha,
                                   Set<String> processedCommits) {
        boolean alreadyProcessed = !processedCommits.add(commit.getOid());
        if (alreadyProcessed) {
            return;
        }
        builder.addNode(commit);
        for (GHCommitHash parent : commit.getParents()) {
            GHCommit parentCommit = commitsBySha.get(parent.getOid());
            if (parentCommit != null) {
                builder.putEdge(commit, parentCommit);
                addCommits(builder, parentCommit, commitsBySha, processedCommits);
            }
        }
    }
}
