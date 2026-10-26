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
package org.jetbrains.plugins.github.pullrequest.comment.ui;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.comment.CodeReviewCommentBodyBlock;
import consulo.review.util.ComputedResult;
import consulo.review.util.FutureUtil;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.messageList.AsyncHtmlImageLoader;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.change.patch.ApplyPatchStatus;
import consulo.versionControlSystem.change.patch.TextFilePatch;
import consulo.versionControlSystem.review.comment.CodeReviewCommentBodyViewModel;
import consulo.versionControlSystem.review.comment.CodeReviewCommentBodyViewModelFactory;
import consulo.versionControlSystem.review.comment.CodeReviewSuggestedChangeSupport;
import consulo.versionControlSystem.review.comment.CodeReviewSuggestionSyntax;
import consulo.versionControlSystem.review.comment.CodeReviewSuggestionThreadData;
import consulo.virtualFileSystem.VirtualFile;
import git4idea.remote.GitRemoteUrlCoordinates;
import git4idea.remote.hosting.GitRemoteBranchesUtil;
import git4idea.remote.hosting.HostedGitRepositoryRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryFlows;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewComment;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewThread;
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter;
import org.jetbrains.plugins.github.pullrequest.comment.GHSuggestedChangeApplier;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GHPRReviewCommentBodyViewModel implements CodeReviewSuggestedChangeSupport {
    private static final Logger LOG = Logger.getInstance(GHPRReviewCommentBodyViewModel.class);

    private final Disposable myCs;
    private final Project myProject;
    private final GHPRDetailsDataProvider myDetailsData;
    private final GHPRReviewDataProvider myReviewData;
    private final String myThreadId;
    private final String myCommentId;

    private final AsyncHtmlImageLoader myHtmlImageLoader;
    private final GithubServerPath myServer;
    private final GitRepository myRepository;
    private final GitRemoteUrlCoordinates myRemoteUrlCoordinates;

    private final MutableObservableValue<@Nullable CodeReviewSuggestionThreadData> myThreadData = MutableObservableValue.of(null);
    private final MutableObservableValue<Boolean> myCanResolvedThread = MutableObservableValue.of(false);
    private final MutableObservableValue<String> myBody = MutableObservableValue.of("");

    private final MutableObservableValue<String> myBodyHtml = MutableObservableValue.of("");
    private final CodeReviewCommentBodyViewModel myBodyVm;

    private final MutableObservableValue<@Nullable GHPullRequest> myLoadedDetailsState = MutableObservableValue.of(null);
    private final MutableObservableValue<@Nullable Boolean> myIsOnReviewBranch = MutableObservableValue.of(null);
    private final ObservableValue<Boolean> myIsOnReviewBranchOrFalse;

    public GHPRReviewCommentBodyViewModel(Disposable parentCs,
                                          Project project,
                                          GHPRDataContext dataContext,
                                          GHPRDataProvider dataProvider,
                                          String threadId,
                                          String commentId) {
        myCs = Disposable.newDisposable("GH PR comment suggestion VM");
        Disposer.register(parentCs, myCs);
        myProject = project;
        myDetailsData = dataProvider.getDetailsData();
        myReviewData = dataProvider.getReviewData();
        myThreadId = threadId;
        myCommentId = commentId;

        myHtmlImageLoader = dataContext.getHtmlImageLoader();
        myServer = dataContext.getRepositoryDataService().getRepositoryMapping().getRepository().getServerPath();
        myRepository = dataContext.getRepositoryDataService().getRemoteCoordinates().repository();
        myRemoteUrlCoordinates = dataContext.getRepositoryDataService().getRemoteCoordinates();

        ComputationState<List<GHPullRequestReviewThread>> threads = myReviewData.threadsComputationFlow(myCs);
        Runnable threadsListener = threads.addListener(this::onThreadsUpdated);
        Disposer.register(myCs, threadsListener::run);
        onThreadsUpdated(threads.get());

        // is used for a resolved comment in a collapsed mode, in other cases blocks rendering should be used
        Runnable bodyListener = myBody.addListener(body ->
            myBodyHtml.set(GHMarkdownToHtmlConverter.convertToHtml(body, myProject, myServer)));
        Disposer.register(myCs, bodyListener::run);

        myBodyVm = project.getInstance(CodeReviewCommentBodyViewModelFactory.class).create(myCs, this, myThreadData, myBody);

        myIsOnReviewBranchOrFalse = myIsOnReviewBranch.map(it -> it != null && it);
        ComputationState<GHPullRequest> details = myDetailsData.detailsComputationFlow(myCs);
        Runnable detailsListener = details.addListener(this::onDetailsUpdated);
        Disposer.register(myCs, detailsListener::run);
        onDetailsUpdated(details.get());
        GitRepositoryFlows.changesSignalFlow(myRepository, myCs, this::updateIsOnReviewBranch);
    }

    private void onThreadsUpdated(@Nullable ComputedResult<List<GHPullRequestReviewThread>> result) {
        List<GHPullRequestReviewThread> threads = result == null ? null : result.getOrNull();
        if (threads == null) {
            return;
        }
        GHPullRequestReviewThread thread = null;
        for (GHPullRequestReviewThread candidate : threads) {
            if (candidate.getId().equals(myThreadId)) {
                thread = candidate;
                break;
            }
        }
        myThreadData.set(thread == null ? null : createThreadData(thread));
        myCanResolvedThread.set(thread != null && thread.getViewerCanResolve());

        String body = "";
        if (thread != null) {
            for (GHPullRequestReviewComment comment : thread.getComments()) {
                if (comment.getId().equals(myCommentId)) {
                    body = comment.getBody();
                    break;
                }
            }
        }
        myBody.set(body);
    }

    private CodeReviewSuggestionThreadData createThreadData(GHPullRequestReviewThread it) {
        Integer line = it.getLine();
        Integer startLine = it.getStartLine();
        Integer originalLine = it.getOriginalLine();
        Integer originalStartLine = it.getOriginalStartLine();
        int lineCount;
        if (line != null && startLine != null) {
            lineCount = line - startLine;
        }
        else if (originalLine != null && originalStartLine != null) {
            lineCount = originalLine - originalStartLine;
        }
        else {
            lineCount = 1;
        }
        lineCount = Math.max(lineCount, 1);
        Integer originalEndLineIndex = originalLine == null ? null : originalLine - 1;
        Integer originalStartLineIndex = originalStartLine == null ? originalEndLineIndex : Integer.valueOf(originalStartLine - 1);
        Integer endLineIndex = line == null ? null : line - 1;
        Integer startLineIndex = startLine == null ? endLineIndex : Integer.valueOf(startLine - 1);
        return new CodeReviewSuggestionThreadData(it.getId(),
            it.isResolved(),
            it.getViewerCanResolve(),
            it.getPath(),
            it.getDiffHunk(),
            lineCount,
            originalStartLineIndex,
            originalEndLineIndex,
            startLineIndex,
            endLineIndex);
    }

    private void onDetailsUpdated(@Nullable ComputedResult<GHPullRequest> result) {
        if (result == null || result.isInProgress()) {
            return;
        }
        myLoadedDetailsState.set(result.getOrNull());
        updateIsOnReviewBranch();
    }

    private void updateIsOnReviewBranch() {
        GHPullRequest details = myLoadedDetailsState.get();
        HostedGitRepositoryRemote remote =
            details == null ? null : GHPRBranchesViewModel.getHeadRemoteDescriptor(details, myRemoteUrlCoordinates);
        if (details == null || remote == null) {
            myIsOnReviewBranch.set(false);
            return;
        }
        CompletableFuture<Boolean> checkedOutFuture =
            GitRemoteBranchesUtil.testRemoteBranchCheckedOut(myRepository, remote, details.getHeadRefName());
        checkedOutFuture.whenComplete((checkedOut, error) -> {
            if (Disposer.isDisposed(myCs)) {
                return;
            }
            if (error != null) {
                if (!FutureUtil.isCancellation(error)) {
                    LOG.warn("Failed to check the review branch", FutureUtil.unwrap(error));
                }
                myIsOnReviewBranch.set(false);
                return;
            }
            myIsOnReviewBranch.set(checkedOut);
        });
    }

    public Project getProject() {
        return myProject;
    }

    public AsyncHtmlImageLoader getHtmlImageLoader() {
        return myHtmlImageLoader;
    }

    public ObservableValue<String> getBody() {
        return myBody;
    }

    public ObservableValue<String> getBodyHtml() {
        return myBodyHtml;
    }

    public ObservableValue<List<CodeReviewCommentBodyBlock>> getBlocks() {
        return myBodyVm.getBlocks();
    }

    @Override
    public ObservableValue<Boolean> isOnReviewBranch() {
        return myIsOnReviewBranchOrFalse;
    }

    @Override
    public CodeReviewSuggestionSyntax getSyntax() {
        return CodeReviewSuggestionSyntax.GITHUB;
    }

    @Override
    public VirtualFile getRepositoryRoot() {
        return myRepository.getRoot();
    }

    @Override
    public boolean canCommit() {
        return true;
    }

    @Override
    public CompletableFuture<ApplyPatchStatus> commit(TextFilePatch patch, String commitMessage) {
        return GHSuggestedChangeApplier.commitSuggestedChanges(myProject, myRepository, patch, commitMessage);
    }

    @Override
    public CompletableFuture<?> resolveThread(String threadId) {
        if (!myCanResolvedThread.get()) {
            return CompletableFuture.completedFuture(null);
        }
        return myReviewData.resolveThread(threadId);
    }

    @RequiredUIAccess
    public void applySuggestionLocally(TextFilePatch patch) {
        myBodyVm.applySuggestionLocally(patch);
    }

    @RequiredUIAccess
    public void commitSuggestion(TextFilePatch patch, String commitMessage) {
        myBodyVm.commitSuggestion(patch, commitMessage);
    }

    public void openPullRequestInfoAndTimeline(long number) {
        GHPRConnectedProjectViewModel vm = GHPRProjectViewModel.getInstance(myProject).getConnectedProjectVm().get();
        if (vm != null) {
            vm.openPullRequestInfoAndTimeline(number);
        }
    }
}
