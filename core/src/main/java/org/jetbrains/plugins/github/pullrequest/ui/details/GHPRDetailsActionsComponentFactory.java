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
import consulo.disposer.Disposer;
import consulo.github.localize.GithubLocalize;
import consulo.project.Project;
import consulo.review.details.CodeReviewDetailsActionsComponentFactory;
import consulo.review.details.data.ReviewRequestState;
import consulo.review.details.data.ReviewRole;
import consulo.review.details.data.ReviewState;
import consulo.review.localize.ReviewLocalize;
import consulo.review.util.ReviewUIUtil;
import consulo.ui.Button;
import consulo.ui.ButtonStyle;
import consulo.ui.Component;
import consulo.ui.OptionButton;
import consulo.ui.OptionButtonItem;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.ActionGroup;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.pullrequest.ui.GHPRReviewerState;
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRCloseAction;
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRCommitMergeAction;
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRPostReviewAction;
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRReRequestReviewAction;
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRRebaseMergeAction;
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRReopenAction;
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRRequestReviewAction;
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRSetMyselfAsReviewerAction;
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRSquashMergeAction;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.RepositoryRestrictions;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRSubmitReviewPopup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class GHPRDetailsActionsComponentFactory {
    private static final String PLACE = "Github.PullRequest.Details";

    private GHPRDetailsActionsComponentFactory() {
    }

    @RequiredUIAccess
    public static Component create(Disposable scope,
                                   Project project,
                                   ObservableValue<ReviewRequestState> reviewRequestState,
                                   GHPRReviewFlowViewModel reviewFlowVm) {
        CodeReviewDetailsActionsComponentFactory factory = project.getApplication().getInstance(CodeReviewDetailsActionsComponentFactory.class);
        Actions reviewActions = new Actions(
            new GHPRRequestReviewAction(scope, reviewFlowVm),
            new GHPRReRequestReviewAction(scope, reviewFlowVm),
            new GHPRCloseAction(scope, reviewFlowVm),
            new GHPRReopenAction(scope, reviewFlowVm),
            new GHPRSetMyselfAsReviewerAction(scope, reviewFlowVm),
            new GHPRPostReviewAction(scope, reviewFlowVm),
            new GHPRCommitMergeAction(scope, reviewFlowVm),
            new GHPRSquashMergeAction(scope, reviewFlowVm),
            new GHPRRebaseMergeAction(scope, reviewFlowVm)
        );

        DockLayout wrapper = DockLayout.create();
        AtomicReference<Disposable> roleScope = new AtomicReference<>();
        ReviewUIUtil.bind(scope, reviewFlowVm.getRole(), role -> {
            Disposable previous = roleScope.getAndSet(null);
            if (previous != null) {
                Disposer.dispose(previous);
            }
            Disposable cs = Disposable.newDisposable("GHPRDetailsActions " + role);
            Disposer.register(scope, cs);
            roleScope.set(cs);

            Component mainPanel = switch (role) {
                case AUTHOR -> createActionsForAuthor(cs, factory, reviewFlowVm, reviewActions);
                case REVIEWER -> createActionsForReviewer(cs, factory, reviewFlowVm, reviewActions);
                case GUEST -> createActionsForGuest(cs, factory, reviewActions);
            };
            wrapper.center(factory.createActionsComponent(cs, reviewRequestState,
                mainPanel,
                factory.createActionsForMergedReview(),
                factory.createActionsForClosedReview(cs, reviewActions.reopenReviewAction()),
                factory.createActionsForDraftReview(cs, reviewActions.postReviewAction())));
        });
        return wrapper;
    }

    @RequiredUIAccess
    private static Component createActionsForAuthor(Disposable cs,
                                                    CodeReviewDetailsActionsComponentFactory factory,
                                                    GHPRReviewFlowViewModel reviewFlowVm,
                                                    Actions reviewActions) {
        ObservableValue<ReviewState> reviewState = reviewFlowVm.getReviewState();
        Component requestReviewButton = factory.createRequestReviewButton(cs, reviewState, reviewFlowVm.getRequestedReviewers(),
            reviewActions.requestReviewAction());
        Component reRequestReviewButton = factory.createReRequestReviewButton(cs, reviewState, reviewFlowVm.getRequestedReviewers(),
            reviewActions.reRequestReviewAction());
        Component mergeReviewButton = createMergeReviewOptionButton(cs, reviewFlowVm, reviewActions,
            reviewState.map(it -> it == ReviewState.ACCEPTED));

        DefaultActionGroup moreActionsGroup = createMoreActionGroup();
        Component moreActionsButton = factory.createMoreButton(moreActionsGroup);
        ReviewUIUtil.bind(cs, reviewState, state -> {
            moreActionsGroup.removeAll();
            switch (state) {
                case NEED_REVIEW, WAIT_FOR_UPDATES -> {
                    moreActionsGroup.add(createMergeActionGroup(reviewActions));
                    moreActionsGroup.add(reviewActions.closeReviewAction());
                }
                case ACCEPTED -> {
                    moreActionsGroup.add(reviewActions.requestReviewAction());
                    moreActionsGroup.add(reviewActions.closeReviewAction());
                }
            }
        });

        HorizontalLayout panel = HorizontalLayout.create(CodeReviewDetailsActionsComponentFactory.BUTTONS_GAP);
        panel.add(requestReviewButton);
        panel.add(reRequestReviewButton);
        panel.add(mergeReviewButton);
        panel.add(moreActionsButton);
        return panel;
    }

    @RequiredUIAccess
    private static Component createActionsForReviewer(Disposable cs,
                                                      CodeReviewDetailsActionsComponentFactory factory,
                                                      GHPRReviewFlowViewModel reviewFlowVm,
                                                      Actions reviewActions) {
        Button submitReviewButton = Button.create(GithubLocalize.pullRequestReviewActionsSubmit(0));
        ReviewUIUtil.bind(cs, reviewFlowVm.getPendingComments(),
            pendingComments -> submitReviewButton.setText(GithubLocalize.pullRequestReviewActionsSubmit(pendingComments)));
        ReviewUIUtil.bind(cs, reviewFlowVm.getCurrentUserReviewState(),
            myState -> submitReviewButton.setVisible(myState != GHPRReviewerState.APPROVED));
        submitReviewButton.addClickListener(event -> reviewFlowVm.submitReview());

        reviewFlowVm.setSubmitReviewInputHandler(vm -> GHPRSubmitReviewPopup.INSTANCE.show(vm, submitReviewButton, true));
        Disposer.register(cs, () -> reviewFlowVm.setSubmitReviewInputHandler(null));

        Component mergeReviewButton = createMergeReviewOptionButton(cs, reviewFlowVm, reviewActions,
            ObservableValue.combine(reviewFlowVm.getReviewState(), reviewFlowVm.getCurrentUserReviewState(),
                (overallState, myState) -> overallState == ReviewState.ACCEPTED && (myState == null || !myState.isAwaitingReview())));

        DefaultActionGroup moreActionsGroup = createMoreActionGroup();
        Component moreActionsButton = factory.createMoreButton(moreActionsGroup);
        ReviewUIUtil.bind(cs, reviewFlowVm.getReviewState(), state -> {
            moreActionsGroup.removeAll();
            switch (state) {
                case NEED_REVIEW, WAIT_FOR_UPDATES -> {
                    moreActionsGroup.add(reviewActions.requestReviewAction());
                    moreActionsGroup.add(createMergeActionGroup(reviewActions));
                    moreActionsGroup.add(reviewActions.closeReviewAction());
                }
                case ACCEPTED -> {
                    moreActionsGroup.add(reviewActions.requestReviewAction());
                    moreActionsGroup.add(reviewActions.closeReviewAction());
                }
            }
        });

        HorizontalLayout panel = HorizontalLayout.create(CodeReviewDetailsActionsComponentFactory.BUTTONS_GAP);
        panel.add(submitReviewButton);
        panel.add(mergeReviewButton);
        panel.add(moreActionsButton);
        return panel;
    }

    @RequiredUIAccess
    private static Component createActionsForGuest(Disposable cs, CodeReviewDetailsActionsComponentFactory factory, Actions reviewActions) {
        Button setMyselfAsReviewerButton = ReviewUIUtil.createActionButton(cs, reviewActions.setMyselfAsReviewerAction(), PLACE);

        DefaultActionGroup moreActionsGroup = createMoreActionGroup();
        Component moreActionsButton = factory.createMoreButton(moreActionsGroup);
        moreActionsGroup.removeAll();
        moreActionsGroup.add(reviewActions.requestReviewAction());
        moreActionsGroup.add(createMergeActionGroup(reviewActions));
        moreActionsGroup.add(reviewActions.closeReviewAction());

        HorizontalLayout panel = HorizontalLayout.create(CodeReviewDetailsActionsComponentFactory.BUTTONS_GAP);
        panel.add(setMyselfAsReviewerButton);
        panel.add(moreActionsButton);
        return panel;
    }

    @RequiredUIAccess
    private static Component createMergeReviewOptionButton(Disposable cs,
                                                           GHPRReviewFlowViewModel reviewFlowVm,
                                                           Actions reviewActions,
                                                           ObservableValue<Boolean> visibleWhen) {
        // Usual order: [0] -- "Merge", [1] -- "Squash and Merge", [2] -- "Rebase"
        List<AnAction> actions = new ArrayList<>(List.of(reviewActions.mergeReviewAction(), reviewActions.mergeSquashReviewAction(),
            reviewActions.rebaseReviewAction()));
        RepositoryRestrictions restrictions = reviewFlowVm.getRepositoryRestrictions();
        AnAction mainAction;
        if (restrictions.isMergeAllowed()) {
            mainAction = actions.remove(0);
        }
        else if (restrictions.isSquashMergeAllowed()) {
            mainAction = actions.remove(1);
        }
        else {
            mainAction = actions.remove(2);
        }

        OptionButton button = OptionButton.create(mainAction.getTemplatePresentation().getTextValue());
        button.addStyle(ButtonStyle.PRIMARY);
        button.addClickListener(event -> ReviewUIUtil.performAction(mainAction, button, PLACE));
        Runnable updateOptions = () -> {
            button.setEnabled(mainAction.getTemplatePresentation().isEnabled());
            List<OptionButtonItem> options = new ArrayList<>();
            for (AnAction action : actions) {
                options.add(new OptionButtonItem(action.getTemplatePresentation().getTextValue(), null,
                    action.getTemplatePresentation().isEnabled(), event -> ReviewUIUtil.performAction(action, button, PLACE)));
            }
            button.setOptions(options);
        };
        ReviewUIUtil.bind(cs, reviewFlowVm.isBusy(), reviewFlowVm.isMergeEnabled(), (busy, enabled) -> updateOptions.run());
        ReviewUIUtil.bind(cs, reviewFlowVm.isSquashMergeEnabled(), reviewFlowVm.isRebaseEnabled(), (squash, rebase) -> updateOptions.run());
        ReviewUIUtil.bind(cs, visibleWhen, button::setVisible);
        return button;
    }

    private static ActionGroup createMergeActionGroup(Actions reviewActions) {
        DefaultActionGroup group = new DefaultActionGroup(ReviewLocalize.reviewDetailsActionMergeGroup(), true);
        group.add(reviewActions.mergeReviewAction());
        group.add(reviewActions.mergeSquashReviewAction());
        group.add(reviewActions.rebaseReviewAction());
        return group;
    }

    private static DefaultActionGroup createMoreActionGroup() {
        return new DefaultActionGroup(GithubLocalize.pullRequestMergeCommitAction(), true);
    }

    private record Actions(AnAction requestReviewAction,
                           AnAction reRequestReviewAction,
                           AnAction closeReviewAction,
                           AnAction reopenReviewAction,
                           AnAction setMyselfAsReviewerAction,
                           AnAction postReviewAction,
                           AnAction mergeReviewAction,
                           AnAction mergeSquashReviewAction,
                           AnAction rebaseReviewAction) {
    }
}
