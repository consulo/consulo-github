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
package org.jetbrains.plugins.github.pullrequest.ui.emoji;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.review.icon.IconsProvider;
import consulo.review.util.FutureUtil;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.data.GHReaction;
import org.jetbrains.plugins.github.api.data.GHReactionContent;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.pullrequest.data.GHReactionsService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class GHReactionViewModelImpl implements GHReactionsViewModel {
    private static final Logger LOG = Logger.getInstance(GHReactionViewModelImpl.class);

    private final Disposable myCs;
    private final String myReactableId;
    private final GHUser myCurrentUser;
    private final GHReactionsService myReactionsService;
    private final IconsProvider<GHReactionContent> myReactionIconsProvider;

    private final MutableObservableValue<List<GHReaction>> myReactionsState;
    private final MutableObservableValue<Map<GHReactionContent, ReactionInfo>> myReactionsWithInfo;

    public GHReactionViewModelImpl(Disposable parentCs,
                                   String reactableId,
                                   ObservableValue<List<GHReaction>> reactionsFlow,
                                   GHUser currentUser,
                                   GHReactionsService reactionsService,
                                   IconsProvider<GHReactionContent> reactionIconsProvider) {
        myCs = Disposable.newDisposable("GitHub Reactions View Model");
        Disposer.register(parentCs, myCs);
        myReactableId = reactableId;
        myCurrentUser = currentUser;
        myReactionsService = reactionsService;
        myReactionIconsProvider = reactionIconsProvider;

        myReactionsState = MutableObservableValue.of(reactionsFlow.get());
        myReactionsWithInfo = MutableObservableValue.of(createReactionsWithInfo(myReactionsState.get()));

        Runnable stateListener = myReactionsState.addListener(data -> myReactionsWithInfo.set(createReactionsWithInfo(data)));
        Disposer.register(myCs, stateListener::run);
        Runnable flowListener = reactionsFlow.addListener(myReactionsState::set);
        Disposer.register(myCs, flowListener::run);
    }

    private Map<GHReactionContent, ReactionInfo> createReactionsWithInfo(List<GHReaction> data) {
        Map<GHReactionContent, List<GHUser>> reactionToUsers = new LinkedHashMap<>();
        for (GHReaction reaction : data) {
            List<GHUser> users = reactionToUsers.computeIfAbsent(reaction.getContent(), content -> new ArrayList<>());
            GHUser user = reaction.getUser();
            if (user != null) {
                users.add(user);
            }
        }
        Map<GHReactionContent, ReactionInfo> result = new LinkedHashMap<>();
        for (Map.Entry<GHReactionContent, List<GHUser>> entry : reactionToUsers.entrySet()) {
            List<GHUser> users = entry.getValue();
            boolean reacted = false;
            for (GHUser user : users) {
                if (Objects.equals(user.getId(), myCurrentUser.getId())) {
                    reacted = true;
                    break;
                }
            }
            result.put(entry.getKey(), new ReactionInfo(List.copyOf(users), reacted));
        }
        return result;
    }

    @Override
    public IconsProvider<GHReactionContent> getReactionIconsProvider() {
        return myReactionIconsProvider;
    }

    @Override
    public ObservableValue<Map<GHReactionContent, ReactionInfo>> getReactionsWithInfo() {
        return myReactionsWithInfo;
    }

    @Override
    public void toggle(GHReactionContent reactionContent) {
        if (Disposer.isDisposed(myCs)) {
            return;
        }
        ReactionInfo info = myReactionsWithInfo.get().get(reactionContent);
        boolean isReacted = info != null && info.isReactedByCurrentUser();
        CompletableFuture<GHReaction> request = isReacted
            ? myReactionsService.removeReaction(myReactableId, reactionContent)
            : myReactionsService.addReaction(myReactableId, reactionContent);
        request.whenComplete((reaction, error) -> {
            if (error != null) {
                if (!FutureUtil.isCancellation(error)) {
                    LOG.warn("Failed to toggle reaction", FutureUtil.unwrap(error));
                }
                return;
            }
            if (Disposer.isDisposed(myCs)) {
                return;
            }
            myReactionsState.update(reactions -> {
                List<GHReaction> result = new ArrayList<>(reactions);
                if (isReacted) {
                    result.remove(reaction);
                }
                else {
                    result.add(reaction);
                }
                return List.copyOf(result);
            });
        });
    }
}
