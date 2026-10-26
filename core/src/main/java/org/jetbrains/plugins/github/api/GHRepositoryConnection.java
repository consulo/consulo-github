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
package org.jetbrains.plugins.github.api;

import consulo.accountManagement.Account;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import git4idea.remote.hosting.HostedGitRepositoryConnection;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * A low-level helper representing a GitHub repository, to which the app was authorized to connect
 */
public class GHRepositoryConnection implements HostedGitRepositoryConnection<GHGitRepositoryMapping> {
    private final Disposable myScope;
    private final GHGitRepositoryMapping myRepo;
    private final Account myAccount;
    private final GHPRDataContext myDataContext;
    private final String myId = UUID.randomUUID().toString();
    private final CompletableFuture<?> myClosed = new CompletableFuture<>();

    public GHRepositoryConnection(Disposable scope, GHGitRepositoryMapping repo, Account account, GHPRDataContext dataContext) {
        myScope = scope;
        myRepo = repo;
        myAccount = account;
        myDataContext = dataContext;
        if (!Disposer.tryRegister(scope, () -> myClosed.complete(null))) {
            myClosed.complete(null);
        }
    }

    public String getId() {
        return myId;
    }

    @Override
    public GHGitRepositoryMapping getRepo() {
        return myRepo;
    }

    @Override
    public Account getAccount() {
        return myAccount;
    }

    public GHPRDataContext getDataContext() {
        return myDataContext;
    }

    public Disposable getScope() {
        return myScope;
    }

    @Override
    public CompletableFuture<?> close() {
        Disposer.dispose(myScope);
        return myClosed;
    }

    @Override
    public CompletableFuture<?> awaitClose() {
        return myClosed;
    }
}
