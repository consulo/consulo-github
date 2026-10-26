// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.ui.UIAccess;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import git4idea.ui.branch.MergeDirectionModel;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class GHPRCreateMergeDirectionModel implements MergeDirectionModel<GHGitRepositoryMapping> {
    private final GHPRCreateViewModel myVm;

    private final List<Runnable> myChangeListeners = new CopyOnWriteArrayList<>();

    private boolean myHeadSetByUser;

    GHPRCreateMergeDirectionModel(Disposable cs, GHPRCreateViewModel vm) {
        myVm = vm;

        UIAccess uiAccess = vm.getProject().getUIAccess();
        Runnable unsubscribe = vm.getBranches().addListener(branches -> uiAccess.give(() -> {
            if (Disposer.isDisposed(cs)) {
                return;
            }
            for (Runnable listener : myChangeListeners) {
                listener.run();
            }
        }));
        Disposer.register(cs, unsubscribe::run);
        Disposer.register(cs, myChangeListeners::clear);
    }

    private GHPRCreateViewModel.BranchesState getDirection() {
        return myVm.getBranches().get();
    }

    @Override
    public GHGitRepositoryMapping getBaseRepo() {
        return getDirection().baseRepo();
    }

    @Override
    public @Nullable GitRemoteBranch getBaseBranch() {
        return getDirection().baseBranch();
    }

    @Override
    public void setBaseBranch(@Nullable GitRemoteBranch baseBranch) {
        myVm.setBaseBranch(baseBranch);
    }

    @Override
    public @Nullable GHGitRepositoryMapping getHeadRepo() {
        return getDirection().headRepo();
    }

    @Override
    public @Nullable GitBranch getHeadBranch() {
        return getDirection().headBranch();
    }

    @Override
    public boolean getHeadSetByUser() {
        return myHeadSetByUser;
    }

    @Override
    public void setHeadSetByUser(boolean headSetByUser) {
        myHeadSetByUser = headSetByUser;
    }

    @Override
    public void setHead(@Nullable GHGitRepositoryMapping repo, @Nullable GitBranch branch) {
        myVm.setHead(repo, branch);
    }

    @Override
    public void addAndInvokeDirectionChangesListener(Runnable listener) {
        myChangeListeners.add(listener);
        listener.run();
    }

    @Override
    public List<GHGitRepositoryMapping> getKnownRepoMappings() {
        return List.copyOf(myVm.getRepositories());
    }
}
