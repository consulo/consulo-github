// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.action;

import consulo.annotation.component.ActionImpl;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.review.localize.ReviewLocalize;
import consulo.review.util.ComputedResult;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.ComponentEvent;
import consulo.ui.ex.action.ActionPlaces;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.Presentation;
import org.jetbrains.plugins.github.pullrequest.data.GHPullRequestPendingReview;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPROnCurrentBranchService;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRReviewViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.review.GHPRSubmitReviewPopup;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

@ActionImpl(id = "Github.PullRequest.Review.Submit")
public class GHPRReviewSubmitAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    public GHPRReviewSubmitAction() {
        super(LocalizeValue.of("…"), GithubLocalize.pullRequestReviewSubmitActionDescription());
    }

    @Override
    public boolean displayTextInToolbar() {
        return true;
    }

    @Override
    public void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setDescription(GithubLocalize.pullRequestReviewSubmitActionDescription());
        GHPRReviewViewModel vm = findVm(e);
        if (vm == null) {
            presentation.setEnabledAndVisible(false);
            return;
        }
        presentation.setVisible(true);
        ComputedResult<@Nullable GHPullRequestPendingReview> pendingReviewResult = vm.getPendingReview().get();
        presentation.setEnabled(!pendingReviewResult.isInProgress());
        if (presentation.isEnabledAndVisible()) {
            GHPullRequestPendingReview review = pendingReviewResult.getOrNull();
            presentation.setText(getText(e.getPlace(), review == null ? null : review.commentsCount()));
        }
        else {
            presentation.setText(getText(e.getPlace(), null));
        }
    }

    private static LocalizeValue getText(String place, @Nullable Integer pendingComments) {
        if (ActionPlaces.isPopupPlace(place) && !place.toLowerCase(Locale.ROOT).contains("gitlab")) {
            return ReviewLocalize.reviewStartSubmitAction();
        }
        if (pendingComments == null) {
            return ReviewLocalize.reviewStartSubmitAction();
        }
        return ReviewLocalize.reviewStartSubmitActionWithComments(pendingComments);
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        GHPRReviewViewModel vm = findVm(e);
        if (vm == null) {
            return;
        }
        Project project = e.getData(Project.KEY);
        ComponentEvent<?> uiEvent = e.getUIEvent();
        Component component = uiEvent == null ? null : uiEvent.getComponent();
        vm.setSubmitReviewInputHandler(it -> {
            if (component != null) {
                return GHPRSubmitReviewPopup.INSTANCE.show(it, component);
            }
            if (project != null) {
                return GHPRSubmitReviewPopup.INSTANCE.show(it, project);
            }
            return CompletableFuture.completedFuture(null);
        });
        vm.submitReview();
    }

    /**
     * Tries to find the review VM in the data context or in a branch widget service
     */
    private static @Nullable GHPRReviewViewModel findVm(AnActionEvent e) {
        GHPRReviewViewModel vm = e.getData(GHPRReviewViewModel.DATA_KEY);
        if (vm != null) {
            return vm;
        }
        Project project = e.getData(Project.KEY);
        return project == null ? null : GHPROnCurrentBranchService.getInstance(project).getVmState().get();
    }
}
