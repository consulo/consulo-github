// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.issue;

import consulo.application.progress.ProgressIndicator;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubIssue;
import org.jetbrains.plugins.github.api.data.GithubSearchedIssue;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;

public final class GithubIssuesLoadingHelper {
    private GithubIssuesLoadingHelper() {
    }

    public static List<GithubIssue> load(GithubApiRequestExecutor executor, ProgressIndicator indicator, GithubServerPath server,
                                         String owner, String repo, boolean withClosed) throws IOException {
        return load(executor, indicator, server, owner, repo, withClosed, 100, null);
    }

    public static List<GithubIssue> load(GithubApiRequestExecutor executor, ProgressIndicator indicator, GithubServerPath server,
                                         String owner, String repo, boolean withClosed, int maximum) throws IOException {
        return load(executor, indicator, server, owner, repo, withClosed, maximum, null);
    }

    public static List<GithubIssue> load(GithubApiRequestExecutor executor, ProgressIndicator indicator, GithubServerPath server,
                                         String owner, String repo, boolean withClosed, int maximum, @Nullable String assignee)
        throws IOException {
        return GithubApiPagesLoader.load(executor, indicator,
            GithubApiRequests.Repos.Issues.pages(server, owner, repo, withClosed ? "all" : "open", assignee), maximum);
    }

    public static List<GithubSearchedIssue> search(GithubApiRequestExecutor executor, ProgressIndicator indicator, GithubServerPath server,
                                                   String owner, String repo, boolean withClosed) throws IOException {
        return search(executor, indicator, server, owner, repo, withClosed, null, null);
    }

    public static List<GithubSearchedIssue> search(GithubApiRequestExecutor executor, ProgressIndicator indicator, GithubServerPath server,
                                                   String owner, String repo, boolean withClosed, @Nullable String assignee)
        throws IOException {
        return search(executor, indicator, server, owner, repo, withClosed, assignee, null);
    }

    public static List<GithubSearchedIssue> search(GithubApiRequestExecutor executor, ProgressIndicator indicator, GithubServerPath server,
                                                   String owner, String repo, boolean withClosed, @Nullable String assignee,
                                                   @Nullable String query) throws IOException {
        return GithubApiPagesLoader.loadAll(executor, indicator,
            GithubApiRequests.Search.Issues.pages(server, new GHRepositoryPath(owner, repo), withClosed ? null : "open", assignee, query));
    }
}
