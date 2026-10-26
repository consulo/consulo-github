// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jetbrains.plugins.github.api.data.GithubIssueState;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "__typename", visible = false,
              defaultImpl = GHPRReferencedSubject.class)
@JsonSubTypes({
    @JsonSubTypes.Type(name = "Issue", value = GHPRReferencedSubject.Issue.class),
    @JsonSubTypes.Type(name = "PullRequest", value = GHPRReferencedSubject.PullRequest.class)
})
public class GHPRReferencedSubject {
    private final String title;
    private final long number;
    private final String url;

    protected GHPRReferencedSubject() {
        this.title = "";
        this.number = 0;
        this.url = "";
    }

    public GHPRReferencedSubject(String title, long number, String url) {
        this.title = title;
        this.number = number;
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public long getNumber() {
        return number;
    }

    public String getUrl() {
        return url;
    }

    public static class Issue extends GHPRReferencedSubject {
        @JsonProperty("issueState")
        private final GithubIssueState state;

        protected Issue() {
            super();
            this.state = GithubIssueState.open;
        }

        public Issue(String title, long number, String url, GithubIssueState state) {
            super(title, number, url);
            this.state = state;
        }

        public GithubIssueState getState() {
            return state;
        }
    }

    public static class PullRequest extends GHPRReferencedSubject {
        @JsonProperty("prState")
        private final GHPullRequestState state;
        private final boolean isDraft;

        protected PullRequest() {
            super();
            this.state = GHPullRequestState.OPEN;
            this.isDraft = false;
        }

        public PullRequest(String title, long number, String url, GHPullRequestState state, boolean isDraft) {
            super(title, number, url);
            this.state = state;
            this.isDraft = isDraft;
        }

        public GHPullRequestState getState() {
            return state;
        }

        public boolean isDraft() {
            return isDraft;
        }
    }
}
