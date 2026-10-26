// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.data.service;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.registry.Registry;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReviewRequest;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@Singleton
@State(name = "GitHubPullRequestState", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GHPRPersistentInteractionState implements PersistentStateComponent<GHPRPersistentInteractionState.State> {
    private static final String CLEAR_AFTER_DAYS_KEY = "github.clear.last.seen.state.days";
    private static final String MARGIN_MILLIS_KEY = "github.last.seen.state.margin.millis";

    public static class PRState {
        public String id = "";
        public long number;
        public @Nullable Long lastSeen;

        public PRState() {
        }

        public PRState(GHPRIdentifier id, @Nullable Long lastSeen) {
            this.id = id.id();
            this.number = id.number();
            this.lastSeen = lastSeen;
        }

        public GHPRIdentifier getId() {
            return new GHPRIdentifier(id, number);
        }

        public @Nullable Long getLastSeen() {
            return lastSeen;
        }
    }

    public static class State {
        public List<PRState> prStates = new ArrayList<>();

        public State() {
        }

        public State(List<PRState> prStates) {
            this.prStates = new ArrayList<>(prStates);
        }
    }

    private volatile State myState = new State();
    private final List<Runnable> myUpdateListeners = new CopyOnWriteArrayList<>();

    public static GHPRPersistentInteractionState getInstance(Project project) {
        return project.getInstance(GHPRPersistentInteractionState.class);
    }

    public void addUpdateListener(Disposable parentDisposable, Runnable listener) {
        myUpdateListeners.add(listener);
        Disposer.register(parentDisposable, () -> myUpdateListeners.remove(listener));
    }

    public boolean isSeen(GHPullRequestShort pr, GHUser currentUser) {
        GHActor author = pr.getAuthor();
        if (!Objects.equals(author == null ? null : author.getId(), currentUser.getId()) && !isReviewRequested(pr, currentUser)) {
            return true;
        }

        Long lastSeen = null;
        for (PRState prState : myState.prStates) {
            if (prState.getId().equals(pr.getPrId())) {
                lastSeen = prState.getLastSeen();
                break;
            }
        }
        boolean isSeen = lastSeen != null && new Date(lastSeen + Registry.intValue(MARGIN_MILLIS_KEY, 5000)).compareTo(pr.getUpdatedAt()) >= 0;

        // TODO: Revise this check when adding a new-in-timeline line.
        // Cleanup state entries for PRs that have updates
        long clearBeforeTime = System.currentTimeMillis() - Duration.ofDays(Registry.intValue(CLEAR_AFTER_DAYS_KEY, 14)).toMillis();
        if (!isSeen && lastSeen != null && lastSeen < clearBeforeTime) {
            updateStateAndEmit(st -> new State(st.prStates.stream().filter(it -> !it.getId().equals(pr.getPrId())).toList()));
        }

        return isSeen;
    }

    private static boolean isReviewRequested(GHPullRequestShort pr, GHUser currentUser) {
        for (GHPullRequestReviewRequest request : pr.getReviewRequests()) {
            GHPullRequestRequestedReviewer reviewer = request.getRequestedReviewer();
            if (reviewer != null && reviewer.getId().equals(currentUser.getId())) {
                return true;
            }
        }
        return false;
    }

    public void updateStateFor(GHPRIdentifier prId, Function<@Nullable PRState, PRState> updater) {
        updateStateAndEmit(st -> {
            List<PRState> prStatesExcludingCurrent = new ArrayList<>();
            PRState current = null;
            for (PRState prState : st.prStates) {
                if (prState.getId().equals(prId)) {
                    current = prState;
                }
                else {
                    prStatesExcludingCurrent.add(prState);
                }
            }
            prStatesExcludingCurrent.add(updater.apply(current));
            return new State(prStatesExcludingCurrent);
        });
    }

    private void updateStateAndEmit(UnaryOperator<State> updater) {
        synchronized (this) {
            myState = updater.apply(myState);
        }
        myUpdateListeners.forEach(Runnable::run);
    }

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(State state) {
        myState = state;
    }
}
