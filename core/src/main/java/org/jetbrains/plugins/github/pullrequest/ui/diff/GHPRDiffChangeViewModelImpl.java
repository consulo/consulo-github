// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.diff;

import consulo.application.progress.EmptyProgressIndicator;
import consulo.application.progress.ProgressIndicator;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.component.ProcessCanceledException;
import consulo.diff.DiffUserDataKeys;
import consulo.diff.request.DiffRequest;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.util.ComputedResult;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.dataholder.UserDataHolderBase;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.change.diff.ChangeDiffRequestProducer;
import git4idea.changes.GitBranchComparisonResult;
import git4idea.changes.GitTextFilePatchWithHistory;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class GHPRDiffChangeViewModelImpl implements GHPRDiffChangeViewModel {
    private final Project myProject;
    private final GitBranchComparisonResult myAllChanges;
    private final RefComparisonChange myChange;
    private final @Nullable GitTextFilePatchWithHistory myDiffData;

    private final MutableObservableValue<@Nullable ComputedResult<DiffRequest>> myRequest = MutableObservableValue.of(null);

    private final Object myLock = new Object();
    private boolean myStarted;
    private boolean myDisposed;
    private @Nullable ProgressIndicator myCurrentIndicator;

    public GHPRDiffChangeViewModelImpl(Project project, Disposable parentCs, GitBranchComparisonResult allChanges, RefComparisonChange change) {
        myProject = project;
        myAllChanges = allChanges;
        myChange = change;
        myDiffData = allChanges.getPatchesByChange().get(change);
        Disposer.register(parentCs, () -> {
            synchronized (myLock) {
                myDisposed = true;
                cancelCurrent();
            }
        });
    }

    @Override
    public RefComparisonChange getChange() {
        return myChange;
    }

    @Override
    public @Nullable GitTextFilePatchWithHistory getDiffData() {
        return myDiffData;
    }

    @Override
    public ObservableValue<@Nullable ComputedResult<DiffRequest>> getRequest() {
        synchronized (myLock) {
            if (!myStarted) {
                myStarted = true;
                startLoading();
            }
        }
        return myRequest;
    }

    @Override
    public void reloadRequest() {
        synchronized (myLock) {
            if (myStarted) {
                startLoading();
            }
        }
    }

    private void startLoading() {
        if (myDisposed) {
            return;
        }
        cancelCurrent();
        ProgressIndicator indicator = new EmptyProgressIndicator();
        myCurrentIndicator = indicator;
        myRequest.set(ComputedResult.loading());
        CompletableFuture.supplyAsync(() -> loadRequest(indicator), AppExecutorUtil.getAppExecutorService())
            .whenComplete((request, error) -> {
                synchronized (myLock) {
                    if (myCurrentIndicator != indicator || myDisposed) {
                        return;
                    }
                    myCurrentIndicator = null;
                }
                if (error == null) {
                    myRequest.set(ComputedResult.success(request));
                }
                else {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    if (!(cause instanceof ProcessCanceledException)) {
                        myRequest.set(ComputedResult.failure(cause));
                    }
                }
            });
    }

    private void cancelCurrent() {
        ProgressIndicator current = myCurrentIndicator;
        myCurrentIndicator = null;
        if (current != null) {
            current.cancel();
        }
    }

    private DiffRequest loadRequest(ProgressIndicator indicator) {
        ChangeDiffRequestProducer changeDiffProducer =
            ChangeDiffRequestProducer.create(myProject, GitBranchComparisonResult.createVcsChange(myChange, myProject));
        if (changeDiffProducer == null) {
            throw new IllegalStateException("Could not create diff producer from " + myChange);
        }
        try {
            DiffRequest request = changeDiffProducer.process(new UserDataHolderBase(), indicator);
            request.putUserData(RefComparisonChange.KEY, myChange);
            request.putUserData(DiffUserDataKeys.CUSTOM_DIFF_COMPUTER,
                myDiffData == null ? null : GitBranchComparisonResult.getDiffComputer(myDiffData));
            return request;
        }
        catch (ProcessCanceledException e) {
            throw e;
        }
        catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHPRDiffChangeViewModelImpl that)) {
            return false;
        }

        if (!myProject.equals(that.myProject)) {
            return false;
        }
        if (!myAllChanges.equals(that.myAllChanges)) {
            return false;
        }
        if (!myChange.equals(that.myChange)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = myProject.hashCode();
        result = 31 * result + myAllChanges.hashCode();
        result = 31 * result + myChange.hashCode();
        return result;
    }
}
