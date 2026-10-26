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
package org.jetbrains.plugins.github.pullrequest.ui.details.model.impl;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.details.data.ReviewRequestState;
import consulo.review.icon.IconsProvider;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.review.issue.IssueIdsProcessor;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest;
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter;
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStatusViewModelImpl;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModelHelper;
import org.jspecify.annotations.Nullable;

import java.util.function.BiConsumer;

public class GHPRDetailsViewModelImpl implements GHPRDetailsViewModel {
    private final Disposable myCs;
    private final Project myProject;

    private final MutableObservableValue<GHPullRequest> myDetailsState;

    private final GHPRIdentifier myPrId;
    private final String myNumber;
    private final String myUrl;
    private final ObservableValue<String> myTitle;
    private final ObservableValue<String> myDescription;
    private final ObservableValue<ReviewRequestState> myReviewRequestState;
    private final MutableObservableValue<Boolean> myIsUpdating = MutableObservableValue.of(false);

    private final GHPRSecurityService mySecurityService;
    private final IconsProvider<String> myAvatarIconsProvider;
    private final GHPRBranchesViewModel myBranchesVm;
    private final GHPRChangesViewModelImpl myChangesVm;
    private final GHPRStatusViewModelImpl myStatusVm;
    private final GHPRReviewFlowViewModelImpl myReviewFlowVm;

    public GHPRDetailsViewModelImpl(Project project,
                                    Disposable parentCs,
                                    GHPRDataContext dataContext,
                                    GHPRDataProvider dataProvider,
                                    GHPullRequest details,
                                    BiConsumer<@Nullable GHPRIdentifier, Boolean> openPullRequestDiff) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myProject = project;

        myDetailsState = MutableObservableValue.of(details);

        myPrId = details.getPrId();
        myNumber = "#" + details.getNumber();
        myUrl = details.getUrl();

        myTitle = myDetailsState.map(it -> GHMarkdownToHtmlConverter.convertToHtml(it.getTitle(), project));
        myDescription = myDetailsState.map(it -> IssueIdsProcessor.processIssueIdsHtml(project, it.getBody()));
        myReviewRequestState = myDetailsState.map(it -> {
            if (it.isDraft()) {
                return ReviewRequestState.DRAFT;
            }
            return switch (it.getState()) {
                case CLOSED -> ReviewRequestState.CLOSED;
                case MERGED -> ReviewRequestState.MERGED;
                case OPEN -> ReviewRequestState.OPENED;
            };
        });

        mySecurityService = dataContext.getSecurityService();
        myAvatarIconsProvider = dataContext.getAvatarIconsProvider();
        myBranchesVm = new GHPRBranchesViewModel(myCs, project, dataContext.getRepositoryDataService().getRepositoryMapping(),
            dataContext.getSecurityService().getAccount(), myDetailsState);

        GHPRReviewViewModelHelper reviewVmHelper = new GHPRReviewViewModelHelper(myCs, project, dataProvider);
        myChangesVm = new GHPRChangesViewModelImpl(myCs, project, dataContext, dataProvider, openPullRequestDiff);

        myStatusVm = new GHPRStatusViewModelImpl(myCs, project, dataContext.getRepositoryDataService().getRepositoryMapping().getRemote(),
            dataProvider.getDetailsData(), myDetailsState);

        myReviewFlowVm = new GHPRReviewFlowViewModelImpl(myCs,
            project,
            myDetailsState,
            dataContext.getRepositoryDataService(),
            dataContext.getSecurityService(),
            dataContext.getAvatarIconsProvider(),
            dataProvider.getDetailsData(),
            dataProvider.getChangesData(),
            reviewVmHelper);
    }

    @Override
    public GHPRIdentifier getPrId() {
        return myPrId;
    }

    @Override
    public String getNumber() {
        return myNumber;
    }

    @Override
    public String getUrl() {
        return myUrl;
    }

    @Override
    public ObservableValue<String> getTitle() {
        return myTitle;
    }

    @Override
    public ObservableValue<String> getDescription() {
        return myDescription;
    }

    @Override
    public ObservableValue<ReviewRequestState> getReviewRequestState() {
        return myReviewRequestState;
    }

    @Override
    public MutableObservableValue<Boolean> isUpdating() {
        return myIsUpdating;
    }

    @Override
    public GHPRSecurityService getSecurityService() {
        return mySecurityService;
    }

    @Override
    public IconsProvider<String> getAvatarIconsProvider() {
        return myAvatarIconsProvider;
    }

    @Override
    public GHPRBranchesViewModel getBranchesVm() {
        return myBranchesVm;
    }

    @Override
    public GHPRChangesViewModelImpl getChangesVm() {
        return myChangesVm;
    }

    @Override
    public GHPRStatusViewModelImpl getStatusVm() {
        return myStatusVm;
    }

    @Override
    public GHPRReviewFlowViewModelImpl getReviewFlowVm() {
        return myReviewFlowVm;
    }

    public void update(GHPullRequest details) {
        myDetailsState.set(details);
    }

    @Override
    public void openPullRequestInfoAndTimeline(long number) {
        GHPRConnectedProjectViewModel vm = GHPRProjectViewModel.getInstance(myProject).getConnectedProjectVm().get();
        if (vm != null) {
            vm.openPullRequestInfoAndTimeline(number);
        }
    }

    public void destroy() {
        Disposer.dispose(myCs);
    }
}
