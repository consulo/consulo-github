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
import consulo.review.details.data.CodeReviewCIJobState;
import org.jetbrains.plugins.github.api.data.GHCheckRun;
import org.jetbrains.plugins.github.api.data.GHCheckSuite;
import org.jetbrains.plugins.github.api.data.GHCommitCheckSuiteConclusion;
import org.jetbrains.plugins.github.api.data.GHCommitStatusContext;
import org.jetbrains.plugins.github.api.data.GHCommitStatusContextState;
import org.jetbrains.plugins.github.api.data.GHCommitWithCheckStatuses;
import org.jetbrains.plugins.github.api.data.GHRefUpdateRule;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitWithCheckStatuses;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeStateStatus;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestMergeabilityData;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class GHPRMergeabilityStateBuilder {
    private final GHPullRequestMergeabilityData myMergeabilityData;

    private boolean myCanOverrideAsAdmin = false;
    private List<String> myRequiredContexts = List.of();
    private boolean myIsRestricted = false;
    private int myRequiredApprovingReviewsCount = 0;

    public GHPRMergeabilityStateBuilder(GHPullRequestMergeabilityData mergeabilityData, boolean currentUserIsAdmin) {
        myMergeabilityData = mergeabilityData;

        GHRefUpdateRule baseRefUpdateRule = mergeabilityData.getBaseRefUpdateRule();
        if (baseRefUpdateRule != null) {
            // TODO: load via PullRequest.viewerCanMergeAsAdmin when we update the min version
            myCanOverrideAsAdmin = /*baseBranchProtectionRules.enforceAdmins?.enabled == false &&*/currentUserIsAdmin;
            myRequiredContexts = baseRefUpdateRule.getRequiredStatusCheckContexts().stream().filter(Objects::nonNull).toList();
            myIsRestricted = !baseRefUpdateRule.getViewerCanPush();
            Integer count = baseRefUpdateRule.getRequiredApprovingReviewCount();
            myRequiredApprovingReviewsCount = count == null ? 0 : count;
        }
    }

    public GHPRMergeabilityState build() {
        Boolean hasConflicts = switch (myMergeabilityData.getMergeable()) {
            case MERGEABLE -> false;
            case CONFLICTING -> true;
            case UNKNOWN -> null;
        };

        List<GHPullRequestCommitWithCheckStatuses> commits = myMergeabilityData.getCommits().getNodes();
        GHCommitWithCheckStatuses lastCommit = commits.isEmpty() ? null : commits.get(commits.size() - 1).getCommit();
        GHCommitWithCheckStatuses.Status status = lastCommit == null ? null : lastCommit.getStatus();
        List<GHCommitStatusContext> contexts = status == null ? List.of() : status.getContexts();
        List<CodeReviewCIJob> ciJobs = new ArrayList<>();
        for (GHCommitStatusContext context : contexts) {
            ciJobs.add(new CodeReviewCIJob(context.getContext(), toCiState(context.getState()), context.isRequired(), context.getTargetUrl()));
        }
        GHCommitWithCheckStatuses.GHCheckSuiteConnection checkSuites = lastCommit == null ? null : lastCommit.getCheckSuites();
        if (checkSuites != null) {
            for (GHCheckSuite checkSuite : checkSuites.getNodes()) {
                GHCheckSuite.CheckRunConnection checkRuns = checkSuite.getCheckRuns();
                if (checkRuns == null) {
                    continue;
                }
                for (GHCheckRun checkRun : checkRuns.getNodes()) {
                    String detailsUrl = checkRun.getDetailsUrl();
                    ciJobs.add(new CodeReviewCIJob(checkRun.getName(), toCiState(checkRun.getConclusion()), checkRun.isRequired(),
                        detailsUrl != null ? detailsUrl : checkRun.getUrl()));
                }
            }
        }

        GHPullRequestMergeStateStatus mergeStateStatus = myMergeabilityData.getMergeStateStatus();
        boolean canBeMerged;
        if (mergeStateStatus.canMerge()) {
            canBeMerged = true;
        }
        else {
            canBeMerged = mergeStateStatus.adminCanMerge() && myCanOverrideAsAdmin;
        }

        GHPRMergeabilityState.ChecksState checksState = switch (mergeStateStatus) {
            case CLEAN, DIRTY, DRAFT, HAS_HOOKS, UNKNOWN, UNSTABLE -> null;
            case BEHIND -> GHPRMergeabilityState.ChecksState.BLOCKING_BEHIND;
            case BLOCKED -> {
                if (myRequiredContexts.isEmpty()
                    || contexts.stream()
                    .filter(it -> it.getState() == GHCommitStatusContextState.SUCCESS)
                    .map(GHCommitStatusContext::getContext)
                    .toList()
                    .containsAll(myRequiredContexts)) {
                    yield null;
                }
                yield GHPRMergeabilityState.ChecksState.BLOCKING_FAILING;
            }
        };

        int actualRequiredApprovingReviewsCount =
            mergeStateStatus == GHPullRequestMergeStateStatus.BLOCKED && !myIsRestricted
                && checksState != GHPRMergeabilityState.ChecksState.BLOCKING_FAILING
                ? myRequiredApprovingReviewsCount
                : 0;

        return new GHPRMergeabilityState(hasConflicts,
            ciJobs,
            canBeMerged, myMergeabilityData.getCanBeRebased(),
            myIsRestricted, actualRequiredApprovingReviewsCount);
    }

    private static CodeReviewCIJobState toCiState(GHCommitStatusContextState state) {
        return switch (state) {
            case ERROR, EXPECTED, FAILURE -> CodeReviewCIJobState.FAILED;
            case PENDING -> CodeReviewCIJobState.PENDING;
            case SUCCESS -> CodeReviewCIJobState.SUCCESS;
        };
    }

    private static CodeReviewCIJobState toCiState(@Nullable GHCommitCheckSuiteConclusion conclusion) {
        if (conclusion == null) {
            return CodeReviewCIJobState.PENDING;
        }
        return switch (conclusion) {
            case ACTION_REQUIRED, CANCELLED, NEUTRAL, STALE, STARTUP_FAILURE, TIMED_OUT, FAILURE -> CodeReviewCIJobState.FAILED;
            case SKIPPED -> CodeReviewCIJobState.SKIPPED;
            case SUCCESS -> CodeReviewCIJobState.SUCCESS;
        };
    }
}
