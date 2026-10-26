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
package org.jetbrains.plugins.github.pullrequest.ui.diff;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.review.comment.CodeReviewNewCommentViewModel;
import consulo.review.icon.IconsProvider;
import consulo.review.timeline.CodeReviewCommentViewModel;
import consulo.review.timeline.thread.RepliesStateData;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.messageList.AsyncHtmlImageLoader;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.pullrequest.comment.ui.GHPRNewThreadCommentViewModel;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class MappedGHPRReviewThreadDiffViewModel implements GHPRReviewThreadDiffViewModel {
    private final GHPRCompactReviewThreadViewModel mySharedVm;

    private final MutableObservableValue<MappingData> myMapping;

    public MappedGHPRReviewThreadDiffViewModel(Disposable parentCs,
                                               GHPRCompactReviewThreadViewModel sharedVm,
                                               ObservableValue<Map<String, MappingData>> mapping) {
        mySharedVm = sharedVm;
        MappingData initial = mapping.get().get(sharedVm.getId());
        myMapping = MutableObservableValue.of(initial != null ? initial : new MappingData(false, null, null));
        Runnable unsubscribe = mapping.addListener(mappings -> {
            MappingData data = mappings.get(sharedVm.getId());
            if (data != null) {
                myMapping.set(data);
            }
        });
        Disposer.register(parentCs, unsubscribe::run);
    }

    @Override
    public ObservableValue<MappingData> getMapping() {
        return myMapping;
    }

    @Override
    public ObservableValue<Boolean> isBusy() {
        return mySharedVm.isBusy();
    }

    @Override
    public String getId() {
        return mySharedVm.getId();
    }

    @Override
    public String getTrackingId() {
        return mySharedVm.getTrackingId();
    }

    @Override
    public IconsProvider<String> getAvatarIconsProvider() {
        return mySharedVm.getAvatarIconsProvider();
    }

    @Override
    public ObservableValue<List<CommentItem>> getCommentItems() {
        return mySharedVm.getCommentItems();
    }

    @Override
    public ObservableValue<List<CodeReviewCommentViewModel>> getComments() {
        return mySharedVm.getComments();
    }

    @Override
    public ObservableValue<Boolean> getCanCreateReplies() {
        return mySharedVm.getCanCreateReplies();
    }

    @Override
    public ObservableValue<Boolean> isWritingReply() {
        return mySharedVm.isWritingReply();
    }

    @Override
    public GHPRNewThreadCommentViewModel getNewReplyVm() {
        return mySharedVm.getNewReplyVm();
    }

    @Override
    public @Nullable CodeReviewNewCommentViewModel getReplyVm() {
        return mySharedVm.getReplyVm();
    }

    @Override
    public ObservableValue<Boolean> isResolved() {
        return mySharedVm.isResolved();
    }

    @Override
    public ObservableValue<Boolean> getCanChangeResolvedState() {
        return mySharedVm.getCanChangeResolvedState();
    }

    @Override
    public void changeResolvedState() {
        mySharedVm.changeResolvedState();
    }

    @Override
    public ObservableValue<RepliesStateData> getRepliesState() {
        return mySharedVm.getRepliesState();
    }

    @Override
    public ObservableValue<Boolean> getRepliesFolded() {
        return mySharedVm.getRepliesFolded();
    }

    @Override
    public void unfoldReplies() {
        mySharedVm.unfoldReplies();
    }

    @Override
    public @Nullable String getLocation() {
        return mySharedVm.getLocation();
    }

    @Override
    public boolean canNavigate() {
        return mySharedVm.canNavigate();
    }

    @RequiredUIAccess
    @Override
    public void navigate() {
        mySharedVm.navigate();
    }

    @RequiredUIAccess
    @Override
    public @Nullable Component createDiffComponent(Disposable parentDisposable) {
        return mySharedVm.createDiffComponent(parentDisposable);
    }

    @Override
    public @Nullable AsyncHtmlImageLoader getHtmlImageLoader() {
        return mySharedVm.getHtmlImageLoader();
    }

    @Override
    public void addFocusRequestListener(Disposable parentDisposable, Runnable listener) {
        mySharedVm.addFocusRequestListener(parentDisposable, listener);
    }

    @Override
    public void requestFocus() {
        mySharedVm.requestFocus();
    }

    @Override
    public void startWritingReply() {
        mySharedVm.startWritingReply();
    }

    @Override
    public void stopWritingReply() {
        mySharedVm.stopWritingReply();
    }
}
