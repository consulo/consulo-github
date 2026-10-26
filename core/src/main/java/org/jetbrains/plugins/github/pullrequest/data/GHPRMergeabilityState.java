// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.pullrequest.data;

import consulo.review.details.data.CodeReviewCIJob;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class GHPRMergeabilityState {
    private final @Nullable Boolean myHasConflicts;
    private final List<CodeReviewCIJob> myCiJobs;
    private final boolean myCanBeMerged;
    private final boolean myCanBeRebased;
    private final boolean myIsRestricted;
    private final int myRequiredApprovingReviewsCount;

    public GHPRMergeabilityState(@Nullable Boolean hasConflicts,
                                 List<CodeReviewCIJob> ciJobs,
                                 boolean canBeMerged,
                                 boolean canBeRebased,
                                 boolean isRestricted,
                                 int requiredApprovingReviewsCount) {
        myHasConflicts = hasConflicts;
        myCiJobs = ciJobs;
        myCanBeMerged = canBeMerged;
        myCanBeRebased = canBeRebased;
        myIsRestricted = isRestricted;
        myRequiredApprovingReviewsCount = requiredApprovingReviewsCount;
    }

    public @Nullable Boolean getHasConflicts() {
        return myHasConflicts;
    }

    public List<CodeReviewCIJob> getCiJobs() {
        return myCiJobs;
    }

    public boolean getCanBeMerged() {
        return myCanBeMerged;
    }

    public boolean getCanBeRebased() {
        return myCanBeRebased;
    }

    public boolean isRestricted() {
        return myIsRestricted;
    }

    public int getRequiredApprovingReviewsCount() {
        return myRequiredApprovingReviewsCount;
    }

    public enum ChecksState {
        BLOCKING_BEHIND, BLOCKING_FAILING, FAILING, PENDING, SUCCESSFUL, NONE
    }
}
