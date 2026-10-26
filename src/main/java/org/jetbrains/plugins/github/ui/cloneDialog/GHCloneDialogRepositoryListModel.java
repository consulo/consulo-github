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
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser;
import org.jetbrains.plugins.github.api.data.GithubRepo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class GHCloneDialogRepositoryListModel {
    private final Map<String, List<GHRepositoryListItem>> myItemsByAccount = new LinkedHashMap<>();
    private final Map<String, Set<GithubRepo>> myRepositoriesByAccount = new HashMap<>();

    private final List<Runnable> myListeners = new CopyOnWriteArrayList<>();

    public int getSize() {
        int size = 0;
        for (List<GHRepositoryListItem> items : myItemsByAccount.values()) {
            size += items.size();
        }
        return size;
    }

    public GHRepositoryListItem getElementAt(int index) {
        int offset = 0;
        for (List<GHRepositoryListItem> items : myItemsByAccount.values()) {
            if (index >= offset + items.size()) {
                offset += items.size();
                continue;
            }
            return items.get(index - offset);
        }
        throw new IndexOutOfBoundsException(index);
    }

    public Pair<Account, GHRepositoryListItem> getItemAt(int index) {
        GHRepositoryListItem item = getElementAt(index);
        return Pair.create(item.getAccount(), item);
    }

    public int indexOf(Account account, GHRepositoryListItem item) {
        if (!myItemsByAccount.containsKey(account.getId())) {
            return -1;
        }
        int startOffset = 0;
        for (Map.Entry<String, List<GHRepositoryListItem>> entry : myItemsByAccount.entrySet()) {
            List<GHRepositoryListItem> items = entry.getValue();
            if (entry.getKey().equals(account.getId())) {
                int idx = items.indexOf(item);
                if (idx < 0) {
                    return -1;
                }
                return startOffset + idx;
            }
            else {
                startOffset += items.size();
            }
        }
        return -1;
    }

    public List<GHRepositoryListItem> getItems() {
        List<GHRepositoryListItem> result = new ArrayList<>(getSize());
        for (List<GHRepositoryListItem> items : myItemsByAccount.values()) {
            result.addAll(items);
        }
        return result;
    }

    @RequiredUIAccess
    public void clear(Account account) {
        myRepositoriesByAccount.remove(account.getId());
        if (myItemsByAccount.remove(account.getId()) == null) {
            return;
        }
        fireChanged();
    }

    @RequiredUIAccess
    public void setError(Account account, Throwable error) {
        List<GHRepositoryListItem> accountItems = myItemsByAccount.computeIfAbsent(account.getId(), it -> new ArrayList<>());
        GHRepositoryListItem.Error errorItem = new GHRepositoryListItem.Error(account, error);
        accountItems.add(0, errorItem);
        fireChanged();
    }

    /**
     * Since each repository can be in several states at the same time (shared access for a collaborator and shared access for org member) and
     * repositories for collaborators are loaded in separate request before repositories for org members, we need to update order of re-added
     * repo in order to place it close to other organization repos
     */
    @RequiredUIAccess
    public void addRepositories(Account account, GithubAuthenticatedUser details, List<GithubRepo> repos) {
        Set<GithubRepo> repoSet = myRepositoriesByAccount.computeIfAbsent(account.getId(), it -> new HashSet<>());
        List<GHRepositoryListItem> items = myItemsByAccount.computeIfAbsent(account.getId(), it -> new ArrayList<>());

        List<GHRepositoryListItem.Repo> toAdd = new ArrayList<>();
        for (GithubRepo repo : repos) {
            GHRepositoryListItem.Repo item = new GHRepositoryListItem.Repo(account, details, repo);
            boolean isNew = repoSet.add(repo);
            if (!isNew) {
                int idx = items.indexOf(item);
                if (idx >= 0) {
                    items.remove(idx);
                }
            }
            toAdd.add(item);
        }
        items.addAll(toAdd);
        fireChanged();
    }

    public void addChangeListener(Disposable parentDisposable, Runnable listener) {
        myListeners.add(listener);
        Disposer.register(parentDisposable, () -> myListeners.remove(listener));
    }

    private void fireChanged() {
        for (Runnable listener : myListeners) {
            listener.run();
        }
    }
}
