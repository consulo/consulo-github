// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import consulo.annotation.component.ActionParentRef;
import consulo.annotation.component.ActionRef;
import consulo.annotation.component.ActionRefAnchor;
import consulo.codeEditor.Editor;
import consulo.diff.DiffContext;
import consulo.diff.DiffDataKeys;
import consulo.github.icon.GitHubIconGroup;
import consulo.github.localize.GithubLocalize;
import consulo.review.localize.ReviewLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.action.Presentation;
import consulo.versionControlSystem.change.RefComparisonChange;
import consulo.versionControlSystem.review.changes.CodeReviewChangeListDataKeys;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRCumulativeChangeListViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRDiffReviewViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.editor.GHPRReviewFileEditorViewModel;

import java.util.List;

@ActionImpl(
    id = "Github.PullRequest.Changed.MarkViewed.Toggle",
    parents = {
        @ActionParentRef(value = @ActionRef(id = "EditorPopupMenu"), anchor = ActionRefAnchor.LAST),
        @ActionParentRef(
            value = @ActionRef(id = "Code.Review.Diff.Editor.Popup"),
            anchor = ActionRefAnchor.AFTER,
            relatedToAction = @ActionRef(id = "Code.Review.Editor.New.Comment")
        )
    }
)
public class GHPRViewedStateToggleAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    public GHPRViewedStateToggleAction() {
        super(GithubLocalize.actionGithubPullrequestChangedMarkviewedToggleText());
    }

    @Override
    public void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabledAndVisible(false);
        presentation.setDescription(ReviewLocalize.actionCodereviewTogglechangesviewedDescription());

        List<RefComparisonChange> changes = e.getData(CodeReviewChangeListDataKeys.SELECTED_CHANGES);
        GHPRChangeListViewModel changesVm = changes == null ? null : e.getData(GHPRChangeListViewModel.DATA_KEY);
        if (changes != null && changesVm != null) {
            if (!(changesVm instanceof GHPRCumulativeChangeListViewModel cumulativeVm)) {
                return;
            }
            boolean isAllViewed = cumulativeVm.isViewedStateForAllChanges(changes, true);
            if (changes.size() == 1) {
                presentation.setText(isAllViewed
                    ? ReviewLocalize.actionCodereviewTogglechangesviewedMarknotviewed()
                    : ReviewLocalize.actionCodereviewTogglechangesviewedMarkviewed());
            }
            else {
                presentation.setText(isAllViewed
                    ? ReviewLocalize.actionCodereviewTogglechangesviewedMarkallnotviewed()
                    : ReviewLocalize.actionCodereviewTogglechangesviewedMarkallviewed());
            }
            presentation.setEnabledAndVisible(true);
            return;
        }

        Editor fileEditor = e.getData(Editor.KEY);
        GHPRReviewFileEditorViewModel fileVm = fileEditor == null ? null : fileEditor.getUserData(GHPRReviewFileEditorViewModel.KEY);
        if (fileVm != null) {
            if (fileVm.isUpdateRequired().get()) {
                return;
            }
            Boolean isViewed = fileVm.isViewedState().get().getOrNull();
            if (isViewed == null) {
                return;
            }
            presentation.setText(isViewed
                ? ReviewLocalize.actionCodereviewTogglechangesviewedMarkfilenotviewed()
                : ReviewLocalize.actionCodereviewTogglechangesviewedMarkfileviewed());
            presentation.setEnabledAndVisible(true);
            presentation.setIcon(GitHubIconGroup.github());
            return;
        }

        DiffContext diffContext = e.getData(DiffDataKeys.DIFF_CONTEXT);
        GHPRDiffReviewViewModel diffVm = diffContext == null ? null : diffContext.getUserData(GHPRDiffReviewViewModel.KEY);
        if (diffVm != null) {
            Boolean isViewed = diffVm.isViewedState().get().getOrNull();
            if (isViewed == null) {
                return;
            }
            presentation.setText(isViewed
                ? ReviewLocalize.actionCodereviewTogglechangesviewedMarkfilenotviewed()
                : ReviewLocalize.actionCodereviewTogglechangesviewedMarkfileviewed());
            presentation.setEnabledAndVisible(true);
        }
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        List<RefComparisonChange> changes = e.getData(CodeReviewChangeListDataKeys.SELECTED_CHANGES);
        GHPRChangeListViewModel changesVm = e.getData(GHPRChangeListViewModel.DATA_KEY);
        if (changes != null && changesVm != null) {
            if (!(changesVm instanceof GHPRCumulativeChangeListViewModel cumulativeVm)) {
                return;
            }
            boolean isAllViewed = cumulativeVm.isViewedStateForAllChanges(changes, true);
            cumulativeVm.setViewedState(changes, !isAllViewed);
            return;
        }

        Editor fileEditor = e.getData(Editor.KEY);
        GHPRReviewFileEditorViewModel fileVm = fileEditor == null ? null : fileEditor.getUserData(GHPRReviewFileEditorViewModel.KEY);
        if (fileVm != null) {
            if (fileVm.isUpdateRequired().get()) {
                return;
            }
            Boolean isViewed = fileVm.isViewedState().get().getOrNull();
            if (isViewed == null) {
                return;
            }
            fileVm.setViewedState(!isViewed);
            return;
        }

        DiffContext diffContext = e.getData(DiffDataKeys.DIFF_CONTEXT);
        GHPRDiffReviewViewModel diffVm = diffContext == null ? null : diffContext.getUserData(GHPRDiffReviewViewModel.KEY);
        if (diffVm != null) {
            Boolean isViewed = diffVm.isViewedState().get().getOrNull();
            if (isViewed == null) {
                return;
            }
            diffVm.setViewedState(!isViewed);
        }
    }
}
