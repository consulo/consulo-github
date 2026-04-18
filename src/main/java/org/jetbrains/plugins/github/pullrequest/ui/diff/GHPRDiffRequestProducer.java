// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2025 consulo.io
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
package org.jetbrains.plugins.github.pullrequest.ui.diff;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import consulo.diff.DiffContentFactory;
import consulo.diff.DiffManager;
import consulo.diff.content.DiffContent;
import consulo.diff.request.SimpleDiffRequest;
import consulo.logging.Logger;
import consulo.project.Project;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces diff requests for PR changed files by fetching content from GitHub API.
 */
public class GHPRDiffRequestProducer {
    private static final Logger LOG = Logger.getInstance(GHPRDiffRequestProducer.class);

    private final Project myProject;
    private final GithubApiRequestExecutor myExecutor;
    private final String myOwner;
    private final String myRepo;
    private final long myPrNumber;

    public GHPRDiffRequestProducer(Project project, GithubApiRequestExecutor executor,
                                   String owner, String repo, long prNumber) {
        myProject = project;
        myExecutor = executor;
        myOwner = owner;
        myRepo = repo;
        myPrNumber = prNumber;
    }

    /**
     * Fetches the list of changed files in the PR.
     */
    public List<ChangedFile> fetchChangedFiles() throws IOException {
        String url = myExecutor.toString().contains("api.github.com")
            ? "https://api.github.com"
            : myExecutor.toString();

        // Use the REST API to get files
        String filesUrl = url + "/repos/" + myOwner + "/" + myRepo + "/pulls/" + myPrNumber + "/files?per_page=100";
        // Note: the actual URL construction should go through the server path
        // This is a simplified version

        List<ChangedFile> files = new ArrayList<>();
        // TODO: implement actual API call when integrated with server path
        return files;
    }

    /**
     * Shows a diff for the given file using Consulo's DiffManager.
     */
    public void showDiff(String filename, String baseContent, String headContent) {
        DiffContentFactory contentFactory = DiffContentFactory.getInstance();
        DiffContent baseContentObj = contentFactory.create(myProject, baseContent);
        DiffContent headContentObj = contentFactory.create(myProject, headContent);

        SimpleDiffRequest diffRequest = new SimpleDiffRequest(
            "PR #" + myPrNumber + ": " + filename,
            baseContentObj,
            headContentObj,
            "Base",
            "Head"
        );

        DiffManager.getInstance().showDiff(myProject, diffRequest);
    }

    /**
     * Represents a changed file in a PR.
     */
    public record ChangedFile(String filename, String status, int additions, int deletions, String patch) {
    }
}
