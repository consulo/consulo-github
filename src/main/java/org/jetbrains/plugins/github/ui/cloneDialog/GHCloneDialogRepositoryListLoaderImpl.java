// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.ui.cloneDialog;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubOrg;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.data.request.Affiliation;
import org.jetbrains.plugins.github.api.data.request.GithubRequestPagination;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class GHCloneDialogRepositoryListLoaderImpl implements GHCloneDialogRepositoryListLoader {
    private final UIAccess myUiAccess;
    private final AccountManager myAccountManager;

    private int myLoaderCount;
    private final List<Runnable> myLoadingListeners = new CopyOnWriteArrayList<>();

    private final GHCloneDialogRepositoryListModel myListModel = new GHCloneDialogRepositoryListModel();

    private final Map<String, CompletableFuture<?>> myJobsMap = new HashMap<>();

    private boolean myDisposed;

    @RequiredUIAccess
    public GHCloneDialogRepositoryListLoaderImpl(Disposable parentDisposable, AccountManager accountManager) {
        myUiAccess = UIAccess.current();
        myAccountManager = accountManager;

        Disposer.register(parentDisposable, () -> {
            myDisposed = true;
            for (CompletableFuture<?> job : myJobsMap.values()) {
                job.cancel(false);
            }
            myJobsMap.clear();
        });
    }

    @Override
    public boolean getLoading() {
        return myLoaderCount > 0;
    }

    @Override
    public GHCloneDialogRepositoryListModel getListModel() {
        return myListModel;
    }

    @Override
    @RequiredUIAccess
    public void loadRepositories(Account account) {
        CompletableFuture<?> currentJob = myJobsMap.get(account.getId());
        if (currentJob != null && !currentJob.isDone()) {
            return;
        }

        CompletableFuture<Object> job = new CompletableFuture<>();
        myJobsMap.put(account.getId(), job);

        setLoaderCount(myLoaderCount + 1);
        job.whenCompleteAsync((result, error) -> {
            setLoaderCount(myLoaderCount - 1);
            if (error == null || job.isCancelled() || myDisposed) {
                return;
            }
            myListModel.setError(account, unwrap(error));
        }, myUiAccess);

        try {
            doLoadRepositories(account, job).whenComplete((result, error) -> {
                if (error != null) {
                    job.completeExceptionally(unwrap(error));
                }
                else {
                    job.complete(null);
                }
            });
        }
        catch (Exception e) {
            job.completeExceptionally(e);
        }
    }

    private CompletableFuture<?> doLoadRepositories(Account account, CompletableFuture<?> job) {
        GithubServerPath server = GithubAccountType.toServerPath(myAccountManager.getServerUrl(account));
        GithubApiRequestExecutor executor = GithubApiRequestExecutorFactory.getInstance().create(account, server);

        return executor.execute(GithubApiRequests.CurrentUser.get(server)).thenCompose(details -> {
            GithubApiPagesLoader.Request<GithubRepo> repoPagesRequest = GithubApiRequests.CurrentUser.Repos.pages(
                server,
                null,
                null,
                EnumSet.of(Affiliation.OWNER, Affiliation.COLLABORATOR, Affiliation.ORG_MEMBER),
                GithubRequestPagination.DEFAULT
            );

            Consumer<List<GithubRepo>> repoCollector = repos -> myUiAccess.execute(() -> {
                if (job.isDone() || myDisposed) {
                    return;
                }
                myListModel.addRepositories(account, details, repos);
            });

            return GithubApiPagesLoader.batchesFlow(executor, repoPagesRequest).apply(repoCollector)
                .thenCompose(it -> {
                    checkCanceled(job);
                    return GithubApiPagesLoader.loadAll(executor, GithubApiRequests.CurrentUser.Orgs.pages(server));
                })
                .thenCompose(orgs -> {
                    List<GithubOrg> userOrganizations = new ArrayList<>(orgs);
                    userOrganizations.sort(Comparator.comparing(GithubOrg::getLogin));

                    CompletableFuture<Object> chain = CompletableFuture.completedFuture(null);
                    for (GithubOrg org : userOrganizations) {
                        chain = chain.thenCompose(it -> {
                            checkCanceled(job);
                            GithubApiPagesLoader.Request<GithubRepo> orgRepoRequest =
                                GithubApiRequests.Organisations.Repos.pages(server, org.getLogin(), GithubRequestPagination.DEFAULT);
                            return asObject(GithubApiPagesLoader.batchesFlow(executor, orgRepoRequest).apply(repoCollector));
                        });
                    }
                    return chain;
                });
        });
    }

    @Override
    @RequiredUIAccess
    public void clear(Account account) {
        CompletableFuture<?> job = myJobsMap.remove(account.getId());
        if (job != null) {
            job.cancel(false);
        }
        myListModel.clear(account);
    }

    @Override
    public void addLoadingStateListener(Runnable listener) {
        myLoadingListeners.add(listener);
    }

    private void setLoaderCount(int loaderCount) {
        myLoaderCount = loaderCount;
        for (Runnable listener : myLoadingListeners) {
            listener.run();
        }
    }

    private static void checkCanceled(CompletableFuture<?> job) {
        if (job.isDone()) {
            throw new CancellationException();
        }
    }

    private static CompletableFuture<Object> asObject(CompletableFuture<?> future) {
        return future.thenApply(it -> null);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable result = error;
        while ((result instanceof CompletionException || result instanceof ExecutionException) && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }
}
