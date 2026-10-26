// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.pullrequest.config;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.project.Project;
import consulo.review.diff.DiscussionsViewOption;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.util.lang.Pair;
import consulo.versionControlSystem.ui.ChangesGroupingSupport;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.exceptions.GithubParseException;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

@Singleton
@State(name = "GithubPullRequestsUISettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GithubPullRequestsProjectUISettings implements PersistentStateComponent<GithubPullRequestsProjectUISettings.SettingsState> {
    public static class UrlAndAccount {
        public String url = "";
        public String accountId = "";
    }

    public static class RepoCoordinatesHolder {
        public String server = "";
        public String owner = "";
        public String repository = "";
    }

    public static class SettingsState {
        public @Nullable UrlAndAccount selectedUrlAndAccountId;
        public @Nullable RepoCoordinatesHolder recentNewPullRequestHead;
        public boolean reviewCommentPreferred = true;
        public boolean editorReviewEnabled = true;
        public boolean highlightDiffLinesInEditor = false;
        public Set<String> changesGrouping = new LinkedHashSet<>(Set.of(ChangesGroupingSupport.DIRECTORY_GROUPING, ChangesGroupingSupport.MODULE_GROUPING));
        public DiscussionsViewOption editorReviewViewOption = DiscussionsViewOption.UNRESOLVED_ONLY;
    }

    private final Project myProject;
    private final AccountManager myAccountManager;

    private SettingsState myState = new SettingsState();

    private final MutableObservableValue<Set<String>> myChangesGroupingState = MutableObservableValue.of(Set.copyOf(myState.changesGrouping));
    private final MutableObservableValue<Boolean> myReviewCommentsPreferredState = MutableObservableValue.of(myState.reviewCommentPreferred);
    private final MutableObservableValue<Boolean> myEditorReviewEnabledState = MutableObservableValue.of(myState.editorReviewEnabled);
    private final MutableObservableValue<Boolean> myHighlightDiffLinesInEditorState = MutableObservableValue.of(myState.highlightDiffLinesInEditor);

    @Inject
    public GithubPullRequestsProjectUISettings(Project project, AccountManager accountManager) {
        myProject = project;
        myAccountManager = accountManager;
    }

    public static GithubPullRequestsProjectUISettings getInstance(Project project) {
        return project.getInstance(GithubPullRequestsProjectUISettings.class);
    }

    public synchronized @Nullable Pair<String, Account> getSelectedUrlAndAccount() {
        UrlAndAccount value = myState.selectedUrlAndAccountId;
        if (value == null) {
            return null;
        }
        Account account = myAccountManager.findAccount(value.accountId, myProject);
        if (account == null) {
            return null;
        }
        return Pair.create(value.url, account);
    }

    public synchronized void setSelectedUrlAndAccount(@Nullable Pair<String, Account> value) {
        if (value == null) {
            myState.selectedUrlAndAccountId = null;
            return;
        }
        UrlAndAccount state = new UrlAndAccount();
        state.url = value.getFirst();
        state.accountId = value.getSecond().getId();
        myState.selectedUrlAndAccountId = state;
    }

    public synchronized @Nullable GHRepositoryCoordinates getRecentNewPullRequestHead() {
        RepoCoordinatesHolder holder = myState.recentNewPullRequestHead;
        if (holder == null) {
            return null;
        }
        try {
            return new GHRepositoryCoordinates(GithubServerPath.from(holder.server), new GHRepositoryPath(holder.owner, holder.repository));
        }
        catch (GithubParseException e) {
            return null;
        }
    }

    public synchronized void setRecentNewPullRequestHead(@Nullable GHRepositoryCoordinates value) {
        if (value == null) {
            myState.recentNewPullRequestHead = null;
            return;
        }
        RepoCoordinatesHolder holder = new RepoCoordinatesHolder();
        holder.server = value.getServerPath().toUrl();
        holder.owner = value.getRepositoryPath().getOwner();
        holder.repository = value.getRepositoryPath().getRepository();
        myState.recentNewPullRequestHead = holder;
    }

    public synchronized boolean getReviewCommentsPreferred() {
        return myState.reviewCommentPreferred;
    }

    public void setReviewCommentsPreferred(boolean value) {
        synchronized (this) {
            myState.reviewCommentPreferred = value;
        }
        myReviewCommentsPreferredState.set(value);
    }

    public synchronized boolean getEditorReviewEnabled() {
        return myState.editorReviewEnabled;
    }

    public void setEditorReviewEnabled(boolean value) {
        synchronized (this) {
            myState.editorReviewEnabled = value;
        }
        myEditorReviewEnabledState.set(value);
    }

    public synchronized DiscussionsViewOption getEditorReviewViewOption() {
        return myState.editorReviewViewOption;
    }

    public synchronized void setEditorReviewViewOption(DiscussionsViewOption value) {
        myState.editorReviewViewOption = value;
    }

    public synchronized boolean getHighlightDiffLinesInEditor() {
        return myState.highlightDiffLinesInEditor;
    }

    public void setHighlightDiffLinesInEditor(boolean value) {
        synchronized (this) {
            myState.highlightDiffLinesInEditor = value;
        }
        myHighlightDiffLinesInEditorState.set(value);
    }

    public synchronized Set<String> getChangesGrouping() {
        return Set.copyOf(myState.changesGrouping);
    }

    public void setChangesGrouping(Set<String> value) {
        synchronized (this) {
            myState.changesGrouping = new LinkedHashSet<>(value);
        }
        myChangesGroupingState.set(Set.copyOf(value));
    }

    public ObservableValue<Set<String>> getChangesGroupingState() {
        return myChangesGroupingState;
    }

    public ObservableValue<Boolean> getReviewCommentsPreferredState() {
        return myReviewCommentsPreferredState;
    }

    public ObservableValue<Boolean> getEditorReviewEnabledState() {
        return myEditorReviewEnabledState;
    }

    public ObservableValue<Boolean> getHighlightDiffLinesInEditorState() {
        return myHighlightDiffLinesInEditorState;
    }

    @Override
    public synchronized SettingsState getState() {
        return myState;
    }

    @Override
    public void loadState(SettingsState state) {
        synchronized (this) {
            myState = state;
        }
        myChangesGroupingState.set(Set.copyOf(state.changesGrouping));
        myReviewCommentsPreferredState.set(state.reviewCommentPreferred);
        myEditorReviewEnabledState.set(state.editorReviewEnabled);
        myHighlightDiffLinesInEditorState.set(state.highlightDiffLinesInEditor);
    }
}
