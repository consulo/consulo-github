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
package org.jetbrains.plugins.github.pullrequest.data.provider;

import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;

public class GHPRDataProviderImpl implements GHPRDataProvider {
    private final GHPRIdentifier myId;
    private final GHPRDetailsDataProvider myDetailsData;
    private final GHPRTimelineDataProvider myTimelineData;
    private final GHPRChangesDataProvider myChangesData;
    private final GHPRCommentsDataProvider myCommentsData;
    private final GHPRReviewDataProvider myReviewData;
    private final GHPRViewedStateDataProvider myViewedStateData;

    public GHPRDataProviderImpl(GHPRIdentifier id,
                                GHPRDetailsDataProvider detailsData,
                                GHPRTimelineDataProvider timelineData,
                                GHPRChangesDataProvider changesData,
                                GHPRCommentsDataProvider commentsData,
                                GHPRReviewDataProvider reviewData,
                                GHPRViewedStateDataProvider viewedStateData) {
        myId = id;
        myDetailsData = detailsData;
        myTimelineData = timelineData;
        myChangesData = changesData;
        myCommentsData = commentsData;
        myReviewData = reviewData;
        myViewedStateData = viewedStateData;
    }

    @Override
    public GHPRIdentifier getId() {
        return myId;
    }

    @Override
    public GHPRDetailsDataProvider getDetailsData() {
        return myDetailsData;
    }

    @Override
    public GHPRTimelineDataProvider getTimelineData() {
        return myTimelineData;
    }

    @Override
    public GHPRChangesDataProvider getChangesData() {
        return myChangesData;
    }

    @Override
    public GHPRCommentsDataProvider getCommentsData() {
        return myCommentsData;
    }

    @Override
    public GHPRReviewDataProvider getReviewData() {
        return myReviewData;
    }

    @Override
    public GHPRViewedStateDataProvider getViewedStateData() {
        return myViewedStateData;
    }
}
