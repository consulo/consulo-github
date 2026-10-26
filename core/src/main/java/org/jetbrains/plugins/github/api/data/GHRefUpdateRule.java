// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data;

import consulo.http.graphql.dto.GraphQLFragment;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

@GraphQLFragment("graphql/fragment/refUpdateRule.graphql")
public class GHRefUpdateRule {
    //Can this branch be deleted.
    private final boolean allowsDeletions;
    //Are force pushes allowed on this branch.
    private final boolean allowsForcePushes;
    //Identifies the protection rule pattern.
    private final String pattern;
    //Number of approving reviews required to update matching branches.
    private final @Nullable Integer requiredApprovingReviewCount;
    //List of required status check contexts that must pass for commits to be accepted to matching branches.
    private final List<String> requiredStatusCheckContexts;
    //Are merge commits prohibited from being pushed to this branch.
    private final boolean requiresLinearHistory;
    //Are commits required to be signed.
    private final boolean requiresSignatures;
    //Can the viewer push to the branch
    private final boolean viewerCanPush;

    protected GHRefUpdateRule() {
        this.allowsDeletions = false;
        this.allowsForcePushes = false;
        this.pattern = "";
        this.requiredApprovingReviewCount = null;
        this.requiredStatusCheckContexts = List.of();
        this.requiresLinearHistory = false;
        this.requiresSignatures = false;
        this.viewerCanPush = false;
    }

    public GHRefUpdateRule(boolean allowsDeletions,
                           boolean allowsForcePushes,
                           String pattern,
                           @Nullable Integer requiredApprovingReviewCount,
                           List<String> requiredStatusCheckContexts,
                           boolean requiresLinearHistory,
                           boolean requiresSignatures,
                           boolean viewerCanPush) {
        this.allowsDeletions = allowsDeletions;
        this.allowsForcePushes = allowsForcePushes;
        this.pattern = pattern;
        this.requiredApprovingReviewCount = requiredApprovingReviewCount;
        this.requiredStatusCheckContexts = requiredStatusCheckContexts;
        this.requiresLinearHistory = requiresLinearHistory;
        this.requiresSignatures = requiresSignatures;
        this.viewerCanPush = viewerCanPush;
    }

    public boolean getAllowsDeletions() {
        return allowsDeletions;
    }

    public boolean getAllowsForcePushes() {
        return allowsForcePushes;
    }

    public String getPattern() {
        return pattern;
    }

    public @Nullable Integer getRequiredApprovingReviewCount() {
        return requiredApprovingReviewCount;
    }

    public List<String> getRequiredStatusCheckContexts() {
        return requiredStatusCheckContexts;
    }

    public boolean getRequiresLinearHistory() {
        return requiresLinearHistory;
    }

    public boolean getRequiresSignatures() {
        return requiresSignatures;
    }

    public boolean getViewerCanPush() {
        return viewerCanPush;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GHRefUpdateRule that)) {
            return false;
        }
        return allowsDeletions == that.allowsDeletions
            && allowsForcePushes == that.allowsForcePushes
            && Objects.equals(pattern, that.pattern)
            && Objects.equals(requiredApprovingReviewCount, that.requiredApprovingReviewCount)
            && Objects.equals(requiredStatusCheckContexts, that.requiredStatusCheckContexts)
            && requiresLinearHistory == that.requiresLinearHistory
            && requiresSignatures == that.requiresSignatures
            && viewerCanPush == that.viewerCanPush;
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowsDeletions, allowsForcePushes, pattern, requiredApprovingReviewCount, requiredStatusCheckContexts, requiresLinearHistory, requiresSignatures, viewerCanPush);
    }
}
