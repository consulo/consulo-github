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
package org.jetbrains.plugins.github.pullrequest.ui.details;

import consulo.disposer.Disposable;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.review.details.CodeReviewDetailsStatusComponentFactory;
import consulo.review.localize.ReviewLocalize;
import consulo.review.util.Either;
import consulo.review.util.ReviewUIUtil;
import consulo.ui.Avatar;
import consulo.ui.AvatarStyle;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.util.concurrent.coroutine.ObservableValue;
import git4idea.remote.hosting.ui.ResolveConflictsLocallyDialogComponentFactory;
import git4idea.remote.hosting.ui.ResolveConflictsLocallyViewModel;
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRReviewerState;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRReviewerStateUIUtil;
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRReRequestReviewerAction;
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRRemoveReviewerAction;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRResolveConflictsLocallyError;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStatusViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GHPRStatusChecksComponentFactory {
    private static final int AVATAR_SIZE = 20;

    private GHPRStatusChecksComponentFactory() {
    }

    @RequiredUIAccess
    public static Component create(Disposable scope, Project project, GHPRDetailsViewModel detailsVm) {
        CodeReviewDetailsStatusComponentFactory statusFactory = project.getApplication().getInstance(CodeReviewDetailsStatusComponentFactory.class);
        GHPRStatusViewModel reviewStatusVm = detailsVm.getStatusVm();
        GHPRReviewFlowViewModel reviewFlowVm = detailsVm.getReviewFlowVm();
        GHPRSecurityService securityService = detailsVm.getSecurityService();

        Component loadingPanel = createLoadingComponent(scope, reviewStatusVm, securityService);

        VerticalLayout statusesPanel = VerticalLayout.create();
        statusesPanel.add(createAccessDeniedLabel(scope, reviewStatusVm, securityService));
        statusesPanel.add(statusFactory.createCiComponent(scope, reviewStatusVm));
        statusesPanel.add(createConflictsStatusComponent(scope, project, statusFactory, reviewStatusVm.getResolveConflictsVm()));
        statusesPanel.add(statusFactory.createRequiredReviewsComponent(scope, reviewStatusVm.getRequiredApprovingReviewsCount(),
            reviewStatusVm.isDraft()));
        statusesPanel.add(statusFactory.createRestrictionComponent(scope, reviewStatusVm.isRestricted(), reviewStatusVm.isDraft()));
        statusesPanel.add(statusFactory.createNeedReviewerComponent(scope, reviewFlowVm.getReviewerReviews()));
        statusesPanel.add(statusFactory.<GHPullRequestRequestedReviewer, GHPRReviewerState>createReviewersReviewStateComponent(scope,
            reviewFlowVm.getReviewerReviewStates(),
            (reviewer, reviewState) -> GHPRReviewerStateUIUtil.getText(reviewState, reviewer.getPresentableName()),
            (reviewer, reviewState) -> {
                Avatar avatar = Avatar.create(LocalizeValue.of(reviewer.getPresentableName()),
                    detailsVm.getAvatarIconsProvider().getIcon(reviewer.getAvatarUrl(), AVATAR_SIZE));
                avatar.addStyle(AvatarStyle.X_SMALL);
                avatar.addStyle(GHPRReviewerStateUIUtil.getBorderStyle(reviewState));
                return avatar;
            },
            (reviewer, reviewState) -> GHPRReviewerStateUIUtil.getStatusIcon(reviewState),
            (reviewer, reviewState) -> createReviewerActionGroup(scope, reviewFlowVm, reviewer, reviewState)
        ));

        Component scrollableStatusesPanel = ScrollableLayout.create(statusesPanel);

        DockLayout wrapper = DockLayout.create();
        ReviewUIUtil.bind(scope, reviewStatusVm.getMergeabilityState(), mergeability ->
            wrapper.center(mergeability == null ? loadingPanel : scrollableStatusesPanel));
        return wrapper;
    }

    @RequiredUIAccess
    private static @Nullable ActionGroup createReviewerActionGroup(Disposable scope,
                                                                   GHPRReviewFlowViewModel reviewFlowVm,
                                                                   GHPullRequestRequestedReviewer reviewer,
                                                                   GHPRReviewerState reviewState) {
        if (!reviewFlowVm.getUserCanManageReview()) {
            return null;
        }
        List<AnAction> actions = new ArrayList<>();
        // A re-request only makes sense once the reviewer has already reviewed; removal is always available.
        if (!reviewState.isAwaitingReview()) {
            actions.add(new GHPRReRequestReviewerAction(scope, reviewFlowVm, reviewer));
        }
        actions.add(new GHPRRemoveReviewerAction(scope, reviewFlowVm, reviewer));
        return new DefaultActionGroup(actions);
    }

    @RequiredUIAccess
    private static Component createConflictsStatusComponent(Disposable scope,
                                                            Project project,
                                                            CodeReviewDetailsStatusComponentFactory statusFactory,
                                                            ResolveConflictsLocallyViewModel<GHPRResolveConflictsLocallyError> resolveConflictsVm) {
        ObservableValue<Either<LocalizeValue, Runnable>> resolveAction = resolveConflictsVm.getRequestOrError().map(requestOrError ->
            requestOrError.bimap(
                error -> switch (error) {
                    case GHPRResolveConflictsLocallyError.AlreadyResolvedLocally it ->
                        ReviewLocalize.reviewDetailsResolveConflictsErrorAlreadyResolvedLocally();
                    case GHPRResolveConflictsLocallyError.MergeInProgress it -> ReviewLocalize.reviewDetailsResolveConflictsErrorMergeInProgress();
                    case GHPRResolveConflictsLocallyError.DetailsNotLoaded it -> ReviewLocalize.reviewDetailsResolveConflictsErrorDetailsNotLoaded();
                    case GHPRResolveConflictsLocallyError.RepositoryNotFound it ->
                        GithubLocalize.pullRequestResolveconflictsErrorRepositorynotfound(it.baseOrHead().getText());
                    case GHPRResolveConflictsLocallyError.RemoteNotFound it ->
                        GithubLocalize.pullRequestResolveconflictsErrorRemotenotfound(it.baseOrHead().getText(), it.coordinates().getWebURI());
                },
                request -> (Runnable) () -> resolveConflictsVm.performResolveConflicts(() ->
                    ResolveConflictsLocallyDialogComponentFactory.showBranchUpdateDialog(project, request.headRefName(), request.baseRefName()))
            ));
        return statusFactory.createConflictsComponent(scope, resolveConflictsVm.getHasConflicts(), resolveAction, resolveConflictsVm.isBusy());
    }

    @RequiredUIAccess
    private static Component createLoadingComponent(Disposable scope, GHPRStatusViewModel reviewStatusVm, GHPRSecurityService securityService) {
        Label stateLabel = Label.create(GithubLocalize.pullRequestLoadingStatus());
        stateLabel.setImage(PlatformIconGroup.runconfigurationsTestnotran());
        Component accessDeniedLabel = createAccessDeniedLabel(scope, reviewStatusVm, securityService);
        VerticalLayout panel = VerticalLayout.create();
        panel.add(stateLabel);
        panel.add(accessDeniedLabel);
        return panel;
    }

    @RequiredUIAccess
    private static Component createAccessDeniedLabel(Disposable scope, GHPRStatusViewModel reviewStatusVm, GHPRSecurityService securityService) {
        boolean viewerDidAuthor = reviewStatusVm.getViewerDidAuthor();
        boolean mergeForbidden = securityService.isMergeForbiddenForProject();
        boolean canMerge = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE);
        boolean canClose = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || viewerDidAuthor;
        boolean canMarkReadyForReview = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE) || viewerDidAuthor;

        Label label = Label.create(LocalizeValue.empty());
        label.setImage(PlatformIconGroup.generalError());
        ReviewUIUtil.bind(scope, reviewStatusVm.isDraft(), isDraft -> {
            LocalizeValue text;
            if (!canClose) {
                text = GithubLocalize.pullRequestRepoAccessRequired();
            }
            else if (!canMarkReadyForReview && isDraft) {
                text = GithubLocalize.pullRequestRepoWriteAccessRequired();
            }
            else if (!canMerge && !isDraft) {
                text = GithubLocalize.pullRequestRepoWriteAccessRequired();
            }
            else if (mergeForbidden && !isDraft) {
                text = GithubLocalize.pullRequestMergeDisabled();
            }
            else {
                text = LocalizeValue.empty();
            }
            label.setText(text);
            label.setVisible(text != LocalizeValue.empty());
        });
        return label;
    }
}
