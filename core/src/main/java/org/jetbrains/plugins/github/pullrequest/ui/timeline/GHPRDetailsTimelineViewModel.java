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
package org.jetbrains.plugins.github.pullrequest.ui.timeline;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.review.async.ComputationState;
import consulo.review.icon.IconsProvider;
import consulo.review.util.ComputedResult;
import consulo.review.util.FutureUtil;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHReaction;
import org.jetbrains.plugins.github.api.data.GHReactionContent;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState;
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.GHReactionsService;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDetailsDataProvider;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsFull;
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionViewModelImpl;
import org.jetbrains.plugins.github.pullrequest.ui.emoji.GHReactionsViewModel;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class GHPRDetailsTimelineViewModel {
    private static final Logger LOG = Logger.getInstance(GHPRDetailsTimelineViewModel.class);

    private final Project myProject;
    private final Disposable myCs;
    private final GHPRDataContext myDataContext;
    private final GHPRDataProvider myDataProvider;

    private final GHUser myCurrentUser;
    private final GHReactionsService myReactionsService;
    private final IconsProvider<GHReactionContent> myReactionIconsProvider;

    private final ObservableValue<ComputedResult<GHPRDetailsFull>> myDetails;

    private final MutableObservableValue<@Nullable GHPREditDescriptionViewModel> myDescriptionEditVm = MutableObservableValue.of(null);

    private final MutableObservableValue<List<GHReaction>> myLoadedReactionsState = MutableObservableValue.of(List.of());
    private final GHReactionsViewModel myReactionsVm;

    private final ObservableValue<Boolean> myIsMerged;
    private final ObservableValue<Boolean> myCanDeleteMergedBranch;

    private final ObservableValue<@Nullable String> myHeadRefName;

    GHPRDetailsTimelineViewModel(Project project, Disposable parentCs, GHPRDataContext dataContext, GHPRDataProvider dataProvider) {
        myProject = project;
        myCs = Disposable.newDisposable(GHPRDetailsTimelineViewModel.class.getName());
        Disposer.register(parentCs, myCs);
        myDataContext = dataContext;
        myDataProvider = dataProvider;

        myCurrentUser = dataContext.getSecurityService().getCurrentUser();
        myReactionsService = dataContext.getReactionsService();
        myReactionIconsProvider = dataContext.getReactionIconsProvider();

        ComputationState<GHPullRequest> detailsComputation = dataProvider.getDetailsData().detailsComputationFlow(myCs);
        myDetails = detailsComputation.map(it -> it == null ? ComputedResult.loading() : it.map(this::createDetails));

        Runnable reactionsListener = myDetails.addListener(this::updateLoadedReactions);
        Disposer.register(myCs, reactionsListener::run);
        updateLoadedReactions(myDetails.get());
        myReactionsVm = new GHReactionViewModelImpl(myCs,
            dataProvider.getId().id(),
            myLoadedReactionsState,
            myCurrentUser,
            myReactionsService,
            myReactionIconsProvider);

        myIsMerged = myDetails.map(it -> {
            GHPRDetailsFull details = it.getOrNull();
            return details != null && details.state() == GHPullRequestState.MERGED;
        });
        myCanDeleteMergedBranch = myDetails.map(it -> {
            GHPRDetailsFull details = it.getOrNull();
            if (details == null) {
                return false;
            }
            return details.canDeleteHeadRef() && details.state() == GHPullRequestState.MERGED;
        });

        myHeadRefName = myDetails.map(it -> {
            GHPRDetailsFull details = it.getOrNull();
            return details == null ? null : details.headRefName();
        });
    }

    private void updateLoadedReactions(ComputedResult<GHPRDetailsFull> result) {
        GHPRDetailsFull details = result.getOrNull();
        if (details != null) {
            myLoadedReactionsState.set(details.reactions());
        }
    }

    public ObservableValue<ComputedResult<GHPRDetailsFull>> getDetails() {
        return myDetails;
    }

    public ObservableValue<@Nullable GHPREditDescriptionViewModel> getDescriptionEditVm() {
        return myDescriptionEditVm;
    }

    public GHReactionsViewModel getReactionsVm() {
        return myReactionsVm;
    }

    public ObservableValue<Boolean> isMerged() {
        return myIsMerged;
    }

    public ObservableValue<Boolean> getCanDeleteMergedBranch() {
        return myCanDeleteMergedBranch;
    }

    public ObservableValue<@Nullable String> getHeadRefName() {
        return myHeadRefName;
    }

    private GHPRDetailsFull createDetails(GHPullRequest data) {
        GHActor author = data.getAuthor();
        GithubServerPath server = myDataContext.getRepositoryDataService().getRepositoryMapping().getRepository().getServerPath();
        return new GHPRDetailsFull(myDataProvider.getId(),
            data.getUrl(),
            author != null ? author : myDataContext.getSecurityService().getGhostUser(),
            data.getCreatedAt(),
            data.getState(),
            GHMarkdownToHtmlConverter.convertToHtml(data.getTitle(), myProject),
            data.getBody(),
            GHMarkdownToHtmlConverter.convertToHtml(data.getBody(), myProject, server),
            data.getHeadRefId(),
            data.getHeadRefName(),
            data.getViewerCanUpdate(),
            data.getViewerCanReact(),
            data.getViewerCanDeleteHeadRef() && data.getHeadRefId() != null,
            data.getReactions().getNodes());
    }

    public void editDescription() {
        if (myDescriptionEditVm.get() != null) {
            return;
        }
        GHPRDetailsFull details = myDetails.get().getOrNull();
        if (details == null) {
            return;
        }
        String detailsDescription = details.description();
        String description = detailsDescription != null ? detailsDescription : "";
        GHPRDetailsDataProvider detailsData = myDataProvider.getDetailsData();
        GHPREditDescriptionViewModel vm = new GHPREditDescriptionViewModel(myProject, myCs, detailsData, description, () -> {
            GHPREditDescriptionViewModel current = myDescriptionEditVm.getAndUpdate(it -> null);
            if (current != null) {
                Disposer.dispose(current);
            }
        });
        myDescriptionEditVm.set(vm);
        vm.requestFocus();
    }

    public void deleteMergedBranch() {
        if (!myCanDeleteMergedBranch.get()) {
            return;
        }
        GHPRDetailsFull details = myDetails.get().getOrNull();
        if (details == null) {
            return;
        }
        String refId = details.headRefId();
        if (refId == null) {
            return;
        }

        myDataProvider.getDetailsData().deleteMergedBranch(refId).whenComplete((result, error) -> {
            if (error != null && !FutureUtil.isCancellation(error)) {
                LOG.warn(FutureUtil.unwrap(error));
            }
        });
    }
}
