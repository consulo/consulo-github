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
package org.jetbrains.plugins.github.pullrequest.ui.comment;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.review.icon.IconsProvider;
import consulo.review.util.IncrementallyComputedValue;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRMentionableUsersProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class GHViewModelMentionCompletionImpl implements GHViewModelMentionCompletion {
    private final Disposable myCs;
    private final GHPRMentionableUsersProvider myMentionableUsersProvider;
    private final GHPRReviewDataProvider myReviewDataProvider;
    private final IconsProvider<String> myAvatarIconsProvider;

    private final MutableObservableValue<IncrementallyComputedValue<List<GHUser>>> myPullRequestParticipants =
        MutableObservableValue.of(IncrementallyComputedValue.loading());
    private final AtomicBoolean myParticipantsStarted = new AtomicBoolean();
    private @Nullable CompletableFuture<?> myParticipantsLoading;

    private final MutableObservableValue<IncrementallyComputedValue<List<GHUser>>> myMentionableUsers =
        MutableObservableValue.of(IncrementallyComputedValue.loading());
    private final AtomicBoolean myMentionableUsersStarted = new AtomicBoolean();
    private volatile @Nullable CompletableFuture<?> myMentionableUsersLoading;

    public GHViewModelMentionCompletionImpl(Disposable parentCs,
                                            GHPRMentionableUsersProvider mentionableUsersProvider,
                                            GHPRReviewDataProvider reviewDataProvider,
                                            IconsProvider<String> avatarIconsProvider) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myMentionableUsersProvider = mentionableUsersProvider;
        myReviewDataProvider = reviewDataProvider;
        myAvatarIconsProvider = avatarIconsProvider;
        Disposer.register(myCs, this::cancelLoading);
    }

    @Override
    public ObservableValue<IncrementallyComputedValue<List<GHUser>>> getPullRequestParticipants() {
        if (myParticipantsStarted.compareAndSet(false, true)) {
            myReviewDataProvider.addParticipantsNeedReloadListener(myCs, this::reloadParticipants);
            reloadParticipants();
        }
        return myPullRequestParticipants;
    }

    private void reloadParticipants() {
        CompletableFuture<?> previous;
        CompletableFuture<?> loading;
        synchronized (this) {
            previous = myParticipantsLoading;
            loading = IncrementallyComputedValue.collectIncrementallyTo(myReviewDataProvider::participantsBatches,
                myPullRequestParticipants::set);
            myParticipantsLoading = loading;
        }
        if (previous != null) {
            previous.cancel(true);
        }
    }

    @Override
    public ObservableValue<IncrementallyComputedValue<List<GHUser>>> getMentionableUsers() {
        if (myMentionableUsersStarted.compareAndSet(false, true)) {
            myMentionableUsersLoading = IncrementallyComputedValue.collectIncrementallyTo(
                myMentionableUsersProvider::getMentionableUsersBatches,
                myMentionableUsers::set);
        }
        return myMentionableUsers;
    }

    @Override
    public IconsProvider<String> getAvatarIconsProvider() {
        return myAvatarIconsProvider;
    }

    private void cancelLoading() {
        CompletableFuture<?> participants;
        synchronized (this) {
            participants = myParticipantsLoading;
            myParticipantsLoading = null;
        }
        if (participants != null) {
            participants.cancel(true);
        }
        CompletableFuture<?> mentionableUsers = myMentionableUsersLoading;
        if (mentionableUsers != null) {
            mentionableUsers.cancel(true);
        }
    }
}
