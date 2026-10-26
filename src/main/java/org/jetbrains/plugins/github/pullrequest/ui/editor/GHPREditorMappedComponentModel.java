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

import consulo.review.comment.CodeReviewNewCommentViewModel;
import consulo.review.editor.CodeReviewAdjustableInlayModel;
import consulo.review.editor.CodeReviewNewCommentInlayModel;
import consulo.review.editor.CodeReviewRangedInlayModel;
import consulo.review.editor.CodeReviewThreadInlayModel;
import consulo.review.timeline.thread.CodeReviewThreadViewModel;
import consulo.review.util.Hideable;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHPRCompactReviewThreadViewModel;

public sealed interface GHPREditorMappedComponentModel extends CodeReviewRangedInlayModel {
    abstract non-sealed class Thread<VM extends GHPRCompactReviewThreadViewModel> implements GHPREditorMappedComponentModel, Hideable, CodeReviewThreadInlayModel {
        private final VM myVm;
        private final Object myKey;
        private final MutableObservableValue<Boolean> myHiddenState = MutableObservableValue.of(false);

        protected Thread(VM vm) {
            myVm = vm;
            myKey = vm.getId();
        }

        public VM getVm() {
            return myVm;
        }

        @Override
        public final Object getKey() {
            return myKey;
        }

        @Override
        public final ObservableValue<Boolean> getHiddenState() {
            return myHiddenState;
        }

        @Override
        public final void setHidden(boolean hidden) {
            myHiddenState.set(hidden);
        }

        @Override
        public CodeReviewThreadViewModel getThread() {
            return myVm;
        }
    }

    abstract non-sealed class NewComment<VM extends GHPRReviewNewCommentEditorViewModel> implements GHPREditorMappedComponentModel,
        CodeReviewAdjustableInlayModel,
        CodeReviewNewCommentInlayModel {
        private final VM myVm;

        protected NewComment(VM vm) {
            myVm = vm;
        }

        public VM getVm() {
            return myVm;
        }

        @Override
        public CodeReviewNewCommentViewModel getEditor() {
            return myVm;
        }
    }
}
