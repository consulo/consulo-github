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
package org.jetbrains.plugins.github.pullrequest.ui.editor;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.review.comment.CodeReviewCommentSubmitAction;
import consulo.review.icon.IconsProvider;
import consulo.review.util.ComputedResult;
import consulo.ui.UIAccess;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.review.diff.DiffLineLocation;
import org.jetbrains.plugins.github.api.data.GHActor;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentLocation;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRReviewCommentPosition;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelMentionCompletion;
import org.jspecify.annotations.Nullable;

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

class GHPRReviewFileEditorNewCommentViewModelImpl implements GHPRReviewFileEditorNewCommentViewModel {
    private final ObservableValue<GHPRReviewCommentPosition> myLoc;
    private final GHPRReviewNewCommentEditorViewModel mySharedVm;
    private final List<Runnable> myFocusRequestListeners = new CopyOnWriteArrayList<>();
    private final MutableObservableValue<GHPRReviewCommentLocation> myLocation;
    private volatile boolean myFocusRequested;

    GHPRReviewFileEditorNewCommentViewModelImpl(Disposable cs,
                                                ObservableValue<GHPRReviewCommentPosition> loc,
                                                GHPRReviewNewCommentEditorViewModel sharedVm) {
        myLoc = loc;
        mySharedVm = sharedVm;
        myLocation = MutableObservableValue.of(loc.get().location());
        Disposer.register(cs, loc.addListener(it -> myLocation.set(it.location()))::run);
        Disposer.register(cs, myFocusRequestListeners::clear);
    }

    ObservableValue<GHPRReviewCommentPosition> getLoc() {
        return myLoc;
    }

    @Override
    public void addFocusRequestListener(Disposable parentDisposable, Runnable listener) {
        myFocusRequestListeners.add(listener);
        Disposer.register(parentDisposable, () -> myFocusRequestListeners.remove(listener));
        if (myFocusRequested && UIAccess.isUIThread()) {
            myFocusRequested = false;
            UIAccess.current().give(listener);
        }
    }

    @Override
    public ObservableValue<GHPRReviewCommentLocation> getLocation() {
        return myLocation;
    }

    @Override
    public void requestFocus() {
        if (myFocusRequestListeners.isEmpty()) {
            myFocusRequested = true;
            return;
        }
        for (Runnable listener : myFocusRequestListeners) {
            listener.run();
        }
    }

    @Override
    public ObservableValue<GHPRReviewCommentPosition> getPosition() {
        return mySharedVm.getPosition();
    }

    @Override
    public GHActor getCurrentUser() {
        return mySharedVm.getCurrentUser();
    }

    @Override
    public IconsProvider<String> getAvatarIconsProvider() {
        return mySharedVm.getAvatarIconsProvider();
    }

    @Override
    public ObservableValue<List<SubmitAction>> getSubmitActions() {
        return mySharedVm.getSubmitActions();
    }

    @Override
    public ObservableValue<List<CodeReviewCommentSubmitAction>> getCommentSubmitActions() {
        return mySharedVm.getCommentSubmitActions();
    }

    @Override
    public Date getCreatedAt() {
        return mySharedVm.getCreatedAt();
    }

    @Override
    public void cancel() {
        mySharedVm.cancel();
    }

    @Override
    public void updateLineRange(@Nullable DiffLineLocation newStartLocation, @Nullable DiffLineLocation newEndLocation) {
        mySharedVm.updateLineRange(newStartLocation, newEndLocation);
    }

    @Override
    public void submit() {
        mySharedVm.submit();
    }

    @Override
    public boolean canSuggestChange() {
        return mySharedVm.canSuggestChange();
    }

    @Override
    public String createSuggestedChange() {
        return mySharedVm.createSuggestedChange();
    }

    @Override
    public Project getProject() {
        return mySharedVm.getProject();
    }

    @Override
    public MutableObservableValue<String> getText() {
        return mySharedVm.getText();
    }

    @Override
    public ObservableValue<@Nullable ComputedResult<?>> getState() {
        return mySharedVm.getState();
    }

    @Override
    public String getTrackingId() {
        return mySharedVm.getTrackingId();
    }

    @Override
    public void withMentionCompletionModel(Consumer<GHViewModelMentionCompletion> consumer) {
        mySharedVm.withMentionCompletionModel(consumer);
    }
}
