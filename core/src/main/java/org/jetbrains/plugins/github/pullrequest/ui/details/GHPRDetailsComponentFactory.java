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
package org.jetbrains.plugins.github.pullrequest.ui.details;

import consulo.dataContext.UiDataProvider;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.github.localize.GithubLocalize;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.review.details.CodeReviewDetailsComponentFactory;
import consulo.review.details.CommitPresentation;
import consulo.review.list.error.ErrorStatusPanelFactory;
import consulo.review.list.error.ErrorStatusPresenter;
import consulo.review.util.ComputedResult;
import consulo.review.util.ReviewUIUtil;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.Space;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.ActionManager;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.ActionPopupMenu;
import consulo.ui.ex.action.AnAction;
import consulo.ui.image.Image;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.versionControlSystem.review.CodeReviewProgressTreeModelFromDetails;
import consulo.versionControlSystem.review.changes.CodeReviewChangeListComponentFactory;
import consulo.versionControlSystem.review.details.CodeReviewDetailsBranchComponentFactory;
import consulo.versionControlSystem.review.details.CodeReviewDetailsCommitInfoComponentFactory;
import consulo.versionControlSystem.review.details.CodeReviewDetailsCommitsComponentFactory;
import consulo.versionControlSystem.review.details.model.CodeReviewChangeListViewModel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHCommitModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModelBase;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRChangesViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;

public final class GHPRDetailsComponentFactory {
    private GHPRDetailsComponentFactory() {
    }

    @RequiredUIAccess
    public static Component create(Disposable scope, Project project, GHPRDetailsViewModel detailsVm) {
        return create(scope, project, detailsVm, true);
    }

    @RequiredUIAccess
    public static Component create(Disposable scope, Project project, GHPRDetailsViewModel detailsVm, boolean withTitle) {
        VerticalLayout header = VerticalLayout.create(Space.NONE);

        if (withTitle) {
            CodeReviewDetailsComponentFactory detailsComponentFactory =
                project.getApplication().getInstance(CodeReviewDetailsComponentFactory.class);
            VerticalLayout titlePanel = VerticalLayout.create(Space.LARGE);
            titlePanel.add(detailsComponentFactory.createTitle(scope, detailsVm));
            titlePanel.add(detailsComponentFactory.createDescription(scope, detailsVm, () -> showTimelineAction(titlePanel)));
            titlePanel.paddingBuilder().topSet(Space.X_LARGE).bottomSet(Space.LARGE).horizontalSet(Space.X_LARGE).apply();
            header.add(titlePanel);
        }

        VerticalLayout commitsSection = VerticalLayout.create(Space.NONE);
        commitsSection.add(createCommitsAndBranchesComponent(scope, project, detailsVm));
        commitsSection.add(createCommitsInfoComponent(scope, project, detailsVm));
        commitsSection.paddingBuilder().horizontalSet(Space.X_LARGE).apply();
        header.add(commitsSection);

        Component statusChecks = GHPRStatusChecksComponentFactory.create(scope, project, detailsVm);
        VerticalLayout statusPanel = VerticalLayout.create(Space.NONE);
        statusPanel.add(statusChecks);
        statusPanel.paddingBuilder().topSet(Space.MEDIUM).bottomSet(Space.LARGE).horizontalSet(Space.X_LARGE).apply();

        Component actionsComponent =
            GHPRDetailsActionsComponentFactory.create(scope, project, detailsVm.getReviewRequestState(), detailsVm.getReviewFlowVm());
        VerticalLayout actionsPanel = VerticalLayout.create(Space.NONE);
        actionsPanel.add(actionsComponent);
        actionsPanel.paddingBuilder().bottomSet(Space.X_LARGE).horizontalSet(Space.X_LARGE).apply();

        VerticalLayout footer = VerticalLayout.create(Space.NONE);
        footer.add(statusPanel);
        footer.add(actionsPanel);

        DockLayout root = DockLayout.create();
        root.top(header);
        root.center(createCommitFilesBrowserComponent(scope, project, detailsVm.getChangesVm()));
        root.bottom(footer);

        ActionGroup actionGroup = (ActionGroup) ActionManager.getInstance().getAction("Github.PullRequest.Details.Popup");
        root.addContextMenuListener(event -> {
            ActionPopupMenu menu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.POPUP, actionGroup);
            menu.setTargetComponent(root);
            menu.show(event.getComponent(), event.getInputDetails().getX(), event.getInputDetails().getY());
        });
        return root;
    }

    @RequiredUIAccess
    private static Component createCommitsInfoComponent(Disposable cs, Project project, GHPRDetailsViewModel detailsVm) {
        return project.getApplication().getInstance(CodeReviewDetailsCommitInfoComponentFactory.class)
            .create(cs, detailsVm.getChangesVm().getSelectedCommit(),
                (GHCommitModel commit) -> createCommitsPopupPresenter(commit, detailsVm.getSecurityService().getGhostUser()));
    }

    @RequiredUIAccess
    private static Component createCommitsAndBranchesComponent(Disposable cs, Project project, GHPRDetailsViewModel detailsVm) {
        DockLayout panel = DockLayout.create();
        panel.left(project.getApplication().getInstance(CodeReviewDetailsCommitsComponentFactory.class)
            .create(cs, detailsVm.getChangesVm(),
                (GHCommitModel commit) -> createCommitsPopupPresenter(commit, detailsVm.getSecurityService().getGhostUser())));
        panel.right(project.getApplication().getInstance(CodeReviewDetailsBranchComponentFactory.class)
            .create(cs, detailsVm.getBranchesVm()));
        return panel;
    }

    @RequiredUIAccess
    private static void showTimelineAction(Component parentComponent) {
        AnAction action = ActionManager.getInstance().getAction("Github.PullRequest.Timeline.Show");
        if (action == null) {
            return;
        }
        ReviewUIUtil.performAction(action, parentComponent, "unknown");
    }

    private static CommitPresentation createCommitsPopupPresenter(GHCommitModel commit, GHUser ghostUser) {
        GHUser user = commit.getAuthor() == null ? null : commit.getAuthor().getUser();
        return new CommitPresentation(
            commit.getMessageHeadline(),
            commit.getMessageBody(),
            (user == null ? ghostUser : user).getPresentableName(),
            commit.getCommittedDate()
        );
    }

    @RequiredUIAccess
    private static Component createCommitFilesBrowserComponent(Disposable cs, Project project, GHPRChangesViewModel changesVm) {
        DockLayout wrapper = DockLayout.create();
        wrapper.center(createLoadingLabel());
        AtomicReference<@Nullable Disposable> contentScope = new AtomicReference<>();
        Disposer.register(cs, () -> disposeContent(contentScope));
        ReviewUIUtil.bind(cs, changesVm.getChangeListVm(), (ComputedResult<GHPRChangeListViewModelBase> res) -> {
            disposeContent(contentScope);
            Disposable contentCs = Disposable.newDisposable("GHPRDetailsComponentFactory.changes");
            Disposer.register(cs, contentCs);
            contentScope.set(contentCs);
            Component content = res.fold(
                GHPRDetailsComponentFactory::createLoadingLabel,
                changeListVm -> createChangesPanel(contentCs, project, changeListVm),
                error -> createChangesErrorComponent(project, changesVm, error)
            );
            wrapper.center(content);
        });
        return wrapper;
    }

    private static void disposeContent(AtomicReference<@Nullable Disposable> contentScope) {
        Disposable previous = contentScope.getAndSet(null);
        if (previous != null) {
            Disposer.dispose(previous);
        }
    }

    @RequiredUIAccess
    private static Component createChangesPanel(Disposable cs, Project project, GHPRChangeListViewModelBase changeListVm) {
        CodeReviewProgressTreeModelFromDetails progressModel = new CodeReviewProgressTreeModelFromDetails(cs, changeListVm);
        Component tree = project.getApplication().getInstance(CodeReviewChangeListComponentFactory.class)
            .createIn(cs, changeListVm, progressModel, GithubLocalize.pullRequestDoesNotContainChanges());

        ScrollableLayout scrollPane = ScrollableLayout.create(tree);
        scrollPane.putUserData(UiDataProvider.KEY, sink -> {
            sink.set(GHPRChangeListViewModel.DATA_KEY, changeListVm);
            sink.set(CodeReviewChangeListViewModel.DATA_KEY, changeListVm);
        });
        return scrollPane;
    }

    @RequiredUIAccess
    private static Component createChangesErrorComponent(Project project, GHPRChangesViewModel changesVm, Throwable error) {
        ErrorStatusPresenter<Throwable> errorPresenter = ErrorStatusPresenter.simple(
            GithubLocalize.cannotLoadChanges(),
            null,
            changesVm.getChangesLoadingErrorHandler()::getActionForError
        );
        return project.getApplication().getInstance(ErrorStatusPanelFactory.class).create(error, errorPresenter);
    }

    @RequiredUIAccess
    public static Component createLoadingLabel() {
        Label label = Label.create(CommonLocalize.treeNodeLoading());
        label.setImage(Image.busy());
        return label;
    }
}
