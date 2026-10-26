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
import consulo.git.localize.GitLocalize;
import consulo.github.localize.GithubLocalize;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.review.list.error.ErrorStatusPresenter;
import consulo.review.localize.ReviewLocalize;
import consulo.review.submit.CodeReviewSubmitPopupHandler;
import consulo.review.util.ReviewUIUtil;
import consulo.ui.Button;
import consulo.ui.ButtonStyle;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.MessageBoxes;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.style.ComponentColors;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.localize.LocalizeValue;
import org.jetbrains.plugins.github.api.data.GHPullRequestReviewEvent;
import org.jetbrains.plugins.github.exceptions.GHAPIExceptionUtil;

import java.util.ArrayList;
import java.util.List;

public final class GHPRSubmitReviewPopup extends CodeReviewSubmitPopupHandler<GHPRSubmitReviewViewModel> {
    public static final GHPRSubmitReviewPopup INSTANCE = new GHPRSubmitReviewPopup();

    private GHPRSubmitReviewPopup() {
    }

    @RequiredUIAccess
    @Override
    protected Component createActionsComponent(Disposable parentDisposable, GHPRSubmitReviewViewModel vm) {
        ObservableValue<Boolean> hasAnyComment = ObservableValue.combine(vm.getText(), vm.getDraftCommentsCount(),
            (text, comments) -> !text.isEmpty() || comments > 0);

        List<Component> buttons = new ArrayList<>();
        if (!vm.getViewerIsAuthor()) {
            Button approveButton = Button.create(GithubLocalize.pullRequestReviewSubmitApproveButton(),
                event -> vm.submit(GHPullRequestReviewEvent.APPROVE));
            approveButton.addStyle(ButtonStyle.PRIMARY);
            ReviewUIUtil.bind(parentDisposable, vm.isBusy(), busy -> approveButton.setEnabled(!busy));
            buttons.add(approveButton);

            Button requestChangesButton = Button.create(GithubLocalize.pullRequestReviewSubmitRequestChanges(),
                event -> vm.submit(GHPullRequestReviewEvent.REQUEST_CHANGES));
            ReviewUIUtil.bind(parentDisposable, vm.isBusy(), hasAnyComment,
                (busy, hasComment) -> requestChangesButton.setEnabled(!busy && hasComment));
            buttons.add(requestChangesButton);
        }

        Button commentButton = Button.create(GithubLocalize.pullRequestReviewSubmitCommentButton(),
            event -> vm.submit(GHPullRequestReviewEvent.COMMENT));
        commentButton.setToolTipText(GithubLocalize.pullRequestReviewSubmitCommentDescription());
        ReviewUIUtil.bind(parentDisposable, vm.isBusy(), hasAnyComment, (busy, hasComment) -> commentButton.setEnabled(!busy && hasComment));
        buttons.add(commentButton);

        HorizontalLayout buttonsPanel = HorizontalLayout.create(ACTIONS_GAP);
        buttons.forEach(buttonsPanel::add);
        if (!vm.getCanDeleteWorktree()) {
            return buttonsPanel;
        }

        CheckBox deleteWorktreeCheckBox = CheckBox.create(GitLocalize.gitWorkingtreesDeleteCurrentWorktreeCheckbox());
        deleteWorktreeCheckBox.setValue(vm.getDeleteWorktreeAfterSubmit().get());
        ReviewUIUtil.bind(parentDisposable, vm.isBusy(), busy -> deleteWorktreeCheckBox.setEnabled(!busy));
        deleteWorktreeCheckBox.addValueListener(event -> vm.setDeleteWorktreeAfterSubmit(Boolean.TRUE.equals(event.getValue())));

        Label deleteWorktreeComment = Label.create(GitLocalize.gitWorkingtreesDeleteCurrentWorktreeProjectWillBeClosed());
        deleteWorktreeComment.setForegroundColor(ComponentColors.DISABLED_TEXT);

        VerticalLayout deleteWorktreePanel = VerticalLayout.create();
        deleteWorktreePanel.add(deleteWorktreeCheckBox);
        deleteWorktreePanel.add(deleteWorktreeComment);

        VerticalLayout root = VerticalLayout.create();
        root.add(deleteWorktreePanel);
        root.add(buttonsPanel);
        return root;
    }

    @RequiredUIAccess
    @Override
    protected Component createTitleActionsComponent(Disposable parentDisposable, GHPRSubmitReviewViewModel vm) {
        Component defaultActions = super.createTitleActionsComponent(parentDisposable, vm);
        if (!vm.getHasPendingReview()) {
            return defaultActions;
        }

        Button discardButton = Button.create(LocalizeValue.empty());
        discardButton.setIcon(PlatformIconGroup.actionsGc());
        discardButton.addStyle(ButtonStyle.INPLACE);
        discardButton.setToolTipText(GithubLocalize.pullRequestDiscardPendingComments());
        ReviewUIUtil.bind(parentDisposable, vm.isBusy(), busy -> discardButton.setEnabled(!busy));
        ReviewUIUtil.bind(parentDisposable, vm.getDraftCommentsCount(), count -> discardButton.setVisible(count > 0));
        discardButton.addClickListener(event -> MessageBoxes.yesNo()
            .title(GithubLocalize.pullRequestDiscardPendingCommentsDialogTitle())
            .text(GithubLocalize.pullRequestDiscardPendingCommentsDialogMsg())
            .showAsync(discardButton)
            .whenComplete((yes, error) -> {
                if (Boolean.TRUE.equals(yes)) {
                    vm.discard();
                }
            }));

        return HorizontalLayout.create(TITLE_ACTIONS_GAP).add(discardButton).add(defaultActions);
    }

    @Override
    protected ErrorStatusPresenter<Throwable> getErrorPresenter() {
        return ErrorStatusPresenter.simpleHTML(ReviewLocalize.reviewSubmitFailed(), GHAPIExceptionUtil::getPresentableMessage, e -> null);
    }
}
