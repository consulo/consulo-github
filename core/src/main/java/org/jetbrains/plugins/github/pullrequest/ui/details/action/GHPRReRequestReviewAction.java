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
package org.jetbrains.plugins.github.pullrequest.ui.details.action;

import consulo.disposer.Disposable;
import consulo.github.localize.GithubLocalize;
import consulo.review.localize.ReviewLocalize;
import consulo.review.util.ReviewUIUtil;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.review.util.ReviewAction;
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel;

public class GHPRReRequestReviewAction extends ReviewAction {
    private final GHPRReviewFlowViewModel myReviewFlowVm;

    @RequiredUIAccess
    public GHPRReRequestReviewAction(Disposable scope, GHPRReviewFlowViewModel reviewFlowVm) {
        super(ReviewLocalize.reviewDetailsActionRerequest());
        myReviewFlowVm = reviewFlowVm;
        ReviewUIUtil.bind(scope, reviewFlowVm.isBusy(), isBusy ->
            setEnabled(!isBusy && reviewFlowVm.getUserCanManageReview()));
    }

    @RequiredUIAccess
    @Override
    public void actionPerformed(AnActionEvent e) {
        myReviewFlowVm.reRequestReview();
    }
}
