// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

public class GHPullRequestMetrics {
    private final Count allPRCount;
    private final Count openPRCount;
    private final Count openAuthoredPRCount;
    private final Count openAssigneePRCount;
    private final Count openReviewedPRCount;
    private final Count openReviewAssignedPRCount;

    protected GHPullRequestMetrics() {
        this.allPRCount = null;
        this.openPRCount = null;
        this.openAuthoredPRCount = null;
        this.openAssigneePRCount = null;
        this.openReviewedPRCount = null;
        this.openReviewAssignedPRCount = null;
    }

    public GHPullRequestMetrics(Count allPRCount,
                                Count openPRCount,
                                Count openAuthoredPRCount,
                                Count openAssigneePRCount,
                                Count openReviewedPRCount,
                                Count openReviewAssignedPRCount) {
        this.allPRCount = allPRCount;
        this.openPRCount = openPRCount;
        this.openAuthoredPRCount = openAuthoredPRCount;
        this.openAssigneePRCount = openAssigneePRCount;
        this.openReviewedPRCount = openReviewedPRCount;
        this.openReviewAssignedPRCount = openReviewAssignedPRCount;
    }

    public Count getAllPRCount() {
        return allPRCount;
    }

    public Count getOpenPRCount() {
        return openPRCount;
    }

    public Count getOpenAuthoredPRCount() {
        return openAuthoredPRCount;
    }

    public Count getOpenAssigneePRCount() {
        return openAssigneePRCount;
    }

    public Count getOpenReviewedPRCount() {
        return openReviewedPRCount;
    }

    public Count getOpenReviewAssignedPRCount() {
        return openReviewAssignedPRCount;
    }

    public static class Count {
        private final int issueCount;

        protected Count() {
            this(0);
        }

        public Count(int issueCount) {
            this.issueCount = issueCount;
        }

        public int getIssueCount() {
            return issueCount;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Count that)) {
                return false;
            }
            return issueCount == that.issueCount;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(issueCount);
        }
    }
}
