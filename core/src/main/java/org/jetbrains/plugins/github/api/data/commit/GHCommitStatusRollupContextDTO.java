// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.commit;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import consulo.http.graphql.dto.GraphQLFragment;
import org.jetbrains.plugins.github.api.data.GHCommitCheckSuiteConclusion;
import org.jetbrains.plugins.github.api.data.GHCommitCheckSuiteStatusState;
import org.jetbrains.plugins.github.api.data.GHCommitStatusContextState;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

@GraphQLFragment("/graphql/fragment/commitStatusRollup.graphql")
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(name = "CheckRun", value = GHCommitStatusRollupContextDTO.CheckRun.class),
    @JsonSubTypes.Type(name = "StatusContext", value = GHCommitStatusRollupContextDTO.StatusContext.class)
})
public sealed interface GHCommitStatusRollupContextDTO permits GHCommitStatusRollupContextDTO.CheckRun, GHCommitStatusRollupContextDTO.StatusContext {
    final class CheckRun implements GHCommitStatusRollupContextDTO {
        private final String name;
        private final @Nullable String title;
        // seems easier than 3 nested objects with a single property
        private final Map<String, Map<String, Map<String, String>>> checkSuite;
        private final GHCommitCheckSuiteStatusState status;
        private final @Nullable GHCommitCheckSuiteConclusion conclusion;
        private final String url;

        private CheckRun() {
            this("", null, Map.of(), GHCommitCheckSuiteStatusState.QUEUED, null, "");
        }

        public CheckRun(String name,
                        @Nullable String title,
                        Map<String, Map<String, Map<String, String>>> checkSuite,
                        GHCommitCheckSuiteStatusState status,
                        @Nullable GHCommitCheckSuiteConclusion conclusion,
                        String url) {
            this.name = name;
            this.title = title;
            this.checkSuite = checkSuite;
            this.status = status;
            this.conclusion = conclusion;
            this.url = url;
        }

        public String getName() {
            return name;
        }

        public @Nullable String getTitle() {
            return title;
        }

        public Map<String, Map<String, Map<String, String>>> getCheckSuite() {
            return checkSuite;
        }

        public GHCommitCheckSuiteStatusState getStatus() {
            return status;
        }

        public @Nullable GHCommitCheckSuiteConclusion getConclusion() {
            return conclusion;
        }

        public String getUrl() {
            return url;
        }

        public @Nullable String getWorkflowName() {
            Map<String, Map<String, String>> workflowRun = checkSuite.get("workflowRun");
            Map<String, String> workflow = workflowRun == null ? null : workflowRun.get("workflow");
            return workflow == null ? null : workflow.get("name");
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CheckRun that)) {
                return false;
            }
            return Objects.equals(name, that.name)
                && Objects.equals(title, that.title)
                && Objects.equals(checkSuite, that.checkSuite)
                && status == that.status
                && conclusion == that.conclusion
                && Objects.equals(url, that.url);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, title, checkSuite, status, conclusion, url);
        }
    }

    final class StatusContext implements GHCommitStatusRollupContextDTO {
        private final String context;
        private final @Nullable String description;
        private final GHCommitStatusContextState state;
        private final @Nullable String targetUrl;

        private StatusContext() {
            this("", null, GHCommitStatusContextState.EXPECTED, null);
        }

        public StatusContext(String context, @Nullable String description, GHCommitStatusContextState state, @Nullable String targetUrl) {
            this.context = context;
            this.description = description;
            this.state = state;
            this.targetUrl = targetUrl;
        }

        public String getContext() {
            return context;
        }

        public @Nullable String getDescription() {
            return description;
        }

        public GHCommitStatusContextState getState() {
            return state;
        }

        public @Nullable String getTargetUrl() {
            return targetUrl;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StatusContext that)) {
                return false;
            }
            return Objects.equals(context, that.context)
                && Objects.equals(description, that.description)
                && state == that.state
                && Objects.equals(targetUrl, that.targetUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(context, description, state, targetUrl);
        }
    }
}
