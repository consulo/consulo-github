// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.review;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.util.FutureUtil;
import consulo.review.util.SingleCoroutineLauncher;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import git4idea.workingTrees.GitWorkingTreesService;
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent;
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class GHPRSubmitReviewViewModelImpl implements GHPRSubmitReviewViewModel {
    private static final Logger LOG = Logger.getInstance(GHPRSubmitReviewViewModelImpl.class);

    private final Project myProject;
    private final GHPRReviewDataProvider myDataProvider;
    private final boolean myViewerIsAuthor;
    private final @Nullable GHPullRequestPendingReview myPendingReview;
    private final Runnable myOnDone;

    private final SingleCoroutineLauncher myTaskLauncher = new SingleCoroutineLauncher();

    private final GHPRReviewWorktreeSessionState myWorktreeSessionState;
    private final boolean myCanDeleteWorktree;

    // Defaults to unchecked on first use, but remembers the last choice for the rest of the IDE session.
    private final MutableObservableValue<Boolean> myDeleteWorktreeAfterSubmit;

    private final MutableObservableValue<@Nullable Throwable> myError = MutableObservableValue.of(null);
    private final MutableObservableValue<Integer> myDraftCommentsCount;

    public GHPRSubmitReviewViewModelImpl(Disposable parentCs,
                                         Project project,
                                         GHPRReviewDataProvider dataProvider,
                                         boolean viewerIsAuthor,
                                         @Nullable GHPullRequestPendingReview pendingReview,
                                         Runnable onDone) {
        myProject = project;
        myDataProvider = dataProvider;
        myViewerIsAuthor = viewerIsAuthor;
        myPendingReview = pendingReview;
        myOnDone = onDone;
        myWorktreeSessionState = GHPRReviewWorktreeSessionState.getInstance(project);
        myCanDeleteWorktree = GitWorkingTreesService.getInstance(project).isCurrentProjectLinkedWorktree();
        myDeleteWorktreeAfterSubmit = MutableObservableValue.of(myWorktreeSessionState.getDeleteWorktreeAfterSubmit());
        myDraftCommentsCount = MutableObservableValue.of(pendingReview == null ? 0 : pendingReview.commentsCount());
        Disposer.register(parentCs, myTaskLauncher::cancel);
    }

    @Override
    public boolean getViewerIsAuthor() {
        return myViewerIsAuthor;
    }

    @Override
    public boolean getHasPendingReview() {
        return myPendingReview != null;
    }

    @Override
    public boolean getCanDeleteWorktree() {
        return myCanDeleteWorktree;
    }

    @Override
    public ObservableValue<Boolean> getDeleteWorktreeAfterSubmit() {
        return myDeleteWorktreeAfterSubmit;
    }

    @Override
    public void setDeleteWorktreeAfterSubmit(boolean value) {
        myDeleteWorktreeAfterSubmit.set(value);
        myWorktreeSessionState.setDeleteWorktreeAfterSubmit(value);
    }

    @Override
    public ObservableValue<Boolean> isBusy() {
        return myTaskLauncher.getBusy();
    }

    @Override
    public ObservableValue<@Nullable Throwable> getError() {
        return myError;
    }

    @Override
    public ObservableValue<Integer> getDraftCommentsCount() {
        return myDraftCommentsCount;
    }

    @Override
    public MutableObservableValue<String> getText() {
        return myDataProvider.getPendingReviewComment();
    }

    @Override
    public void submit(GHPullRequestReviewEvent event) {
        String body = getText().get();
        myTaskLauncher.launch(() -> {
            CompletableFuture<?> request = myPendingReview != null
                ? myDataProvider.submitReview(myPendingReview.id(), event, body)
                : myDataProvider.createReview(event, body);
            return request.whenComplete((result, error) -> {
                if (error != null) {
                    if (!FutureUtil.isCancellation(error)) {
                        myError.set(FutureUtil.unwrap(error));
                    }
                    return;
                }
                myError.set(null);
                getText().set("");
                if (myCanDeleteWorktree && myDeleteWorktreeAfterSubmit.get()) {
                    // Runs on the worktree service scope, so it survives onDone() closing this project.
                    GitWorkingTreesService.getInstance(myProject).deleteCurrentProjectWorktree();
                }
                myOnDone.run();
            });
        });
    }

    @Override
    public void discard() {
        if (myPendingReview != null) {
            myTaskLauncher.launch(() -> myDataProvider.deleteReview(myPendingReview.id()).whenComplete((r, e) -> {
                if (e == null) {
                    myOnDone.run();
                }
                else if (!FutureUtil.isCancellation(e)) {
                    LOG.error(FutureUtil.unwrap(e));
                }
            }));
        }
    }

    @Override
    public void cancel() {
        myOnDone.run();
    }
}
