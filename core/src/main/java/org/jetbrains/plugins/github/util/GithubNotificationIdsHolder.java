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
package org.jetbrains.plugins.github.util;

import java.util.List;

public final class GithubNotificationIdsHolder {
    public static final String MISSING_DEFAULT_ACCOUNT = "github.missing.default.account";
    public static final String PULL_REQUEST_CANNOT_SET_TRACKING_BRANCH = "github.pull.request.cannot.set.tracking.branch";
    public static final String CLONE_UNABLE_TO_CREATE_DESTINATION_DIR = "github.clone.unable.to.create.destination.dir";
    public static final String CLONE_UNABLE_TO_FIND_DESTINATION = "github.clone.unable.to.find.destination";
    public static final String OPEN_IN_BROWSER_FILE_IS_NOT_UNDER_REPO = "github.open.in.browser.file.is.not.under.repo";
    public static final String OPEN_IN_BROWSER_CANNOT_GET_LAST_REVISION = "github.open.in.browser.cannot.get.last.revision";
    public static final String REBASE_SUCCESS = "github.rebase.success";
    public static final String GIST_CANNOT_CREATE = "github.gist.cannot.create";
    public static final String PULL_REQUEST_CANNOT_LOAD_BRANCHES = "github.pull.request.cannot.load.branches";
    public static final String PULL_REQUEST_CANNOT_COLLECT_ADDITIONAL_DATA = "github.pull.request.cannot.collect.additional.data";
    public static final String PULL_REQUEST_CANNOT_LOAD_FORKS = "github.pull.request.cannot.load.forks";
    public static final String PULL_REQUEST_FAILED_TO_ADD_REMOTE = "github.pull.request.failed.to.add.remote";
    public static final String PULL_REQUEST_PUSH_FAILED = "github.pull.request.push.failed";
    public static final String PULL_REQUEST_CREATION_ERROR = "github.pull.request.creation.error";
    public static final String PULL_REQUEST_CANNOT_COLLECT_DIFF_DATA = "github.pull.request.cannot.collect.diff.data";
    public static final String PULL_REQUEST_CANNOT_FIND_REPO = "github.pull.request.cannot.find.repo";
    public static final String PULL_REQUEST_CREATED = "github.pull.request.created";
    public static final String PULL_REQUEST_CANNOT_PROCESS_REMOTE = "github.pull.request.cannot.process.remote";
    public static final String PULL_REQUEST_NO_CURRENT_BRANCH = "github.pull.request.no.current.branch";
    public static final String PULL_REQUEST_BRANCH_UPDATE_FAILED = "github.pull.request.branch.update.failed";
    public static final String REBASE_CANNOT_VALIDATE_UPSTREAM_REMOTE = "github.rebase.cannot.validate.upstream.remote";
    public static final String REBASE_UPSTREAM_IS_OWN_REPO = "github.rebase.upstream.is.own.repo";
    public static final String REBASE_CANNOT_GER_USER_INFO = "github.rebase.cannot.get.user.info";
    public static final String REBASE_CANNOT_RETRIEVE_UPSTREAM_INFO = "github.rebase.cannot.retrieve.upstream.info";
    public static final String REBASE_CANNOT_CONFIGURE_UPSTREAM_REMOTE = "github.rebase.cannot.configure.upstream.remote";
    public static final String REBASE_REPO_NOT_FOUND = "github.rebase.repo.not.found";
    public static final String REBASE_CANNOT_LOAD_REPO_INFO = "github.rebase.cannot.load.repo.info";
    public static final String REBASE_REPO_IS_NOT_A_FORK = "github.rebase.repo.is.not.a.fork";
    public static final String REBASE_MULTI_REPO_NOT_SUPPORTED = "github.rebase.multi.repo.not.supported";
    public static final String REBASE_REMOTE_ORIGIN_NOT_FOUND = "github.rebase.remote.origin.not.found";
    public static final String REBASE_ACCOUNT_NOT_FOUND = "github.rebase.account.not.found";
    public static final String REBASE_FAILED_TO_MATCH_REPO = "rebase.error.failed.to.match.gh.repo";
    public static final String GIST_CREATED = "github.gist.created";

    private GithubNotificationIdsHolder() {
    }

    public static List<String> getNotificationIds() {
        return List.of(MISSING_DEFAULT_ACCOUNT,
            PULL_REQUEST_CANNOT_SET_TRACKING_BRANCH,
            CLONE_UNABLE_TO_CREATE_DESTINATION_DIR,
            CLONE_UNABLE_TO_FIND_DESTINATION,
            OPEN_IN_BROWSER_FILE_IS_NOT_UNDER_REPO,
            OPEN_IN_BROWSER_CANNOT_GET_LAST_REVISION,
            REBASE_SUCCESS,
            GIST_CANNOT_CREATE,
            PULL_REQUEST_CANNOT_LOAD_BRANCHES,
            PULL_REQUEST_CANNOT_COLLECT_ADDITIONAL_DATA,
            PULL_REQUEST_CANNOT_LOAD_FORKS,
            PULL_REQUEST_FAILED_TO_ADD_REMOTE,
            PULL_REQUEST_PUSH_FAILED,
            PULL_REQUEST_CREATION_ERROR,
            PULL_REQUEST_CANNOT_COLLECT_DIFF_DATA,
            PULL_REQUEST_CANNOT_FIND_REPO,
            PULL_REQUEST_CREATED,
            PULL_REQUEST_CANNOT_PROCESS_REMOTE,
            PULL_REQUEST_NO_CURRENT_BRANCH,
            PULL_REQUEST_BRANCH_UPDATE_FAILED,
            REBASE_CANNOT_VALIDATE_UPSTREAM_REMOTE,
            REBASE_UPSTREAM_IS_OWN_REPO,
            REBASE_CANNOT_GER_USER_INFO,
            REBASE_CANNOT_RETRIEVE_UPSTREAM_INFO,
            REBASE_CANNOT_CONFIGURE_UPSTREAM_REMOTE,
            REBASE_REPO_NOT_FOUND,
            REBASE_CANNOT_LOAD_REPO_INFO,
            REBASE_REPO_IS_NOT_A_FORK,
            REBASE_MULTI_REPO_NOT_SUPPORTED,
            REBASE_REMOTE_ORIGIN_NOT_FOUND,
            REBASE_ACCOUNT_NOT_FOUND,
            REBASE_FAILED_TO_MATCH_REPO,
            GIST_CREATED);
    }
}
