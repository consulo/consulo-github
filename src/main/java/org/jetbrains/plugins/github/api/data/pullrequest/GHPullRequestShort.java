// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.api.data.pullrequest;

import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHNode;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.List;

public class GHPullRequestShort extends GHNode {
    private String myUrl;
    private long myNumber;
    private String myTitle;
    private GHPullRequestState myState;
    private boolean myIsDraft;
    private @Nullable GHActor myAuthor;
    private Date myCreatedAt;
    private Date myUpdatedAt;
    private List<GHUser> myAssignees;
    private List<GHLabel> myLabels;
    private boolean myViewerCanUpdate;
    private boolean myViewerDidAuthor;

    public GHPullRequestShort() {
    }

    public String getUrl() {
        return myUrl;
    }

    public void setUrl(String url) {
        myUrl = url;
    }

    public long getNumber() {
        return myNumber;
    }

    public void setNumber(long number) {
        myNumber = number;
    }

    public String getTitle() {
        return myTitle;
    }

    public void setTitle(String title) {
        myTitle = title;
    }

    public GHPullRequestState getState() {
        return myState;
    }

    public void setState(GHPullRequestState state) {
        myState = state;
    }

    public boolean isDraft() {
        return myIsDraft;
    }

    public void setDraft(boolean draft) {
        myIsDraft = draft;
    }

    public @Nullable GHActor getAuthor() {
        return myAuthor;
    }

    public void setAuthor(@Nullable GHActor author) {
        myAuthor = author;
    }

    public Date getCreatedAt() {
        return myCreatedAt;
    }

    public void setCreatedAt(Date createdAt) {
        myCreatedAt = createdAt;
    }

    public Date getUpdatedAt() {
        return myUpdatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        myUpdatedAt = updatedAt;
    }

    public List<GHUser> getAssignees() {
        return myAssignees != null ? myAssignees : List.of();
    }

    public void setAssignees(List<GHUser> assignees) {
        myAssignees = assignees;
    }

    public List<GHLabel> getLabels() {
        return myLabels != null ? myLabels : List.of();
    }

    public void setLabels(List<GHLabel> labels) {
        myLabels = labels;
    }

    public boolean isViewerCanUpdate() {
        return myViewerCanUpdate;
    }

    public void setViewerCanUpdate(boolean viewerCanUpdate) {
        myViewerCanUpdate = viewerCanUpdate;
    }

    public boolean isViewerDidAuthor() {
        return myViewerDidAuthor;
    }

    public void setViewerDidAuthor(boolean viewerDidAuthor) {
        myViewerDidAuthor = viewerDidAuthor;
    }

    @Override
    public String toString() {
        return "#" + myNumber + " " + myTitle;
    }
}
