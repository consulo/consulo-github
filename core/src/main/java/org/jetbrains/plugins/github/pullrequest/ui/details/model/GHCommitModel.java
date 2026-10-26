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
package org.jetbrains.plugins.github.pullrequest.ui.details.model;

import consulo.project.Project;
import org.jetbrains.plugins.github.api.data.GHCommit;
import org.jetbrains.plugins.github.api.data.GHCommitHash;
import org.jetbrains.plugins.github.api.data.GHGitActor;
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.List;

public class GHCommitModel {
    private final String myId;
    private final String myOid;
    private final String myAbbreviatedOid;
    private final String myUrl;
    private final String myMessageHeadline;
    private final String myMessageBody;
    private final @Nullable GHGitActor myAuthor;
    private final @Nullable GHGitActor myCommitter;
    private final Date myCommittedDate;
    private final List<GHCommitHash> myParents;

    public GHCommitModel(Project project, GHCommit commitDTO) {
        myId = commitDTO.getId();
        myOid = commitDTO.getOid();
        myAbbreviatedOid = commitDTO.getAbbreviatedOid();
        myUrl = commitDTO.getUrl();
        myMessageHeadline = GHMarkdownToHtmlConverter.convertToHtml(commitDTO.getMessageHeadline(), project);
        myMessageBody = GHMarkdownToHtmlConverter.convertToHtml(commitDTO.getMessageBody(), project);
        myAuthor = commitDTO.getAuthor();
        myCommitter = commitDTO.getCommitter();
        myCommittedDate = commitDTO.getCommittedDate();
        myParents = commitDTO.getParents();
    }

    public String getId() {
        return myId;
    }

    public String getOid() {
        return myOid;
    }

    public String getAbbreviatedOid() {
        return myAbbreviatedOid;
    }

    public String getUrl() {
        return myUrl;
    }

    public String getMessageHeadline() {
        return myMessageHeadline;
    }

    public String getMessageBody() {
        return myMessageBody;
    }

    public @Nullable GHGitActor getAuthor() {
        return myAuthor;
    }

    public @Nullable GHGitActor getCommitter() {
        return myCommitter;
    }

    public Date getCommittedDate() {
        return myCommittedDate;
    }

    public List<GHCommitHash> getParents() {
        return myParents;
    }
}
