// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import jakarta.inject.Singleton;

@Singleton
@State(name = "GithubSettings", storages = @Storage("github"))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class GithubSettings implements PersistentStateComponent<GithubSettings.State> {
    public static final String DEFAULT_HOST_ALIASES = "github.com";

    private State myState = new State();
    private final MutableObservableValue<String> myHostAliasesState = MutableObservableValue.of(DEFAULT_HOST_ALIASES);

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(State state) {
        myState = state;
        myHostAliasesState.set(getHostAliases());
    }

    public static class State {
        public boolean OPEN_IN_BROWSER_GIST = true;
        public boolean COPY_URL_GIST = false;
        // "Secret" in UI, "Public" in API. "Private" here to preserve user settings after refactoring
        public boolean PRIVATE_GIST = true;
        public int CONNECTION_TIMEOUT = 5000;
        public boolean CLONE_GIT_USING_SSH = false;
        public boolean AUTOMATICALLY_MARK_AS_VIEWED = false;
        public boolean IS_UNREAD_MARKERS_ENABLED = true;
        public String HOST_ALIASES = DEFAULT_HOST_ALIASES;
    }

    public static GithubSettings getInstance() {
        return Application.get().getInstance(GithubSettings.class);
    }

    public int getConnectionTimeout() {
        return myState.CONNECTION_TIMEOUT;
    }

    public void setConnectionTimeout(int timeout) {
        myState.CONNECTION_TIMEOUT = timeout;
    }

    public boolean isOpenInBrowserGist() {
        return myState.OPEN_IN_BROWSER_GIST;
    }

    public boolean isCopyURLGist() {
        return myState.COPY_URL_GIST;
    }

    public void setCopyURLGist(boolean copyLink) {
        myState.COPY_URL_GIST = copyLink;
    }

    public boolean isPrivateGist() {
        return myState.PRIVATE_GIST;
    }

    public boolean isCloneGitUsingSsh() {
        return myState.CLONE_GIT_USING_SSH;
    }

    public boolean isAutomaticallyMarkAsViewed() {
        return myState.AUTOMATICALLY_MARK_AS_VIEWED;
    }

    public boolean isSeenMarkersEnabled() {
        return myState.IS_UNREAD_MARKERS_ENABLED;
    }

    public void setPrivateGist(boolean secretGist) {
        myState.PRIVATE_GIST = secretGist;
    }

    public void setOpenInBrowserGist(boolean openInBrowserGist) {
        myState.OPEN_IN_BROWSER_GIST = openInBrowserGist;
    }

    public void setCloneGitUsingSsh(boolean value) {
        myState.CLONE_GIT_USING_SSH = value;
    }

    public void setAutomaticallyMarkAsViewed(boolean automaticallyMarkAsViewed) {
        myState.AUTOMATICALLY_MARK_AS_VIEWED = automaticallyMarkAsViewed;
    }

    public void setIsSeenMarkersEnabled(boolean isUnreadMarkersEnabled) {
        myState.IS_UNREAD_MARKERS_ENABLED = isUnreadMarkersEnabled;
    }

    /**
     * Comma-separated list of host aliases. An "alias=server" entry maps the alias to a GitHub server URL or host.
     * An "alias" entry maps the alias to github.com.
     */
    public String getHostAliases() {
        String aliases = myState.HOST_ALIASES;
        return aliases == null ? DEFAULT_HOST_ALIASES : aliases;
    }

    public void setHostAliases(String aliases) {
        myState.HOST_ALIASES = aliases;
        myHostAliasesState.set(aliases);
    }

    public ObservableValue<String> getHostAliasesState() {
        return myHostAliasesState;
    }
}
