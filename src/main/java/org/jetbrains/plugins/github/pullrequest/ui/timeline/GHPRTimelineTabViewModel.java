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

import consulo.localize.LocalizeValue;
import consulo.review.details.model.CodeReviewDetailsTabViewModel;
import consulo.review.details.model.CodeReviewDetailsViewModel;
import consulo.review.timeline.CodeReviewTimelineViewModel;

public final class GHPRTimelineTabViewModel implements CodeReviewDetailsTabViewModel {
    private final LocalizeValue myDisplayName;
    private final CodeReviewDetailsViewModel myDetailsVm;
    private final GHPRTimelineViewModel myTimelineVm;

    public GHPRTimelineTabViewModel(LocalizeValue displayName, CodeReviewDetailsViewModel detailsVm, GHPRTimelineViewModel timelineVm) {
        myDisplayName = displayName;
        myDetailsVm = detailsVm;
        myTimelineVm = timelineVm;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return myDisplayName;
    }

    @Override
    public CodeReviewDetailsViewModel getDetailsVm() {
        return myDetailsVm;
    }

    @Override
    public CodeReviewTimelineViewModel getTimelineVm() {
        return myTimelineVm;
    }
}
