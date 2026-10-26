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
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRMentionableUsersProvider;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRReviewDataProvider;

import java.util.function.Consumer;

public class GHViewModelWithTextCompletionImpl implements GHViewModelWithTextCompletion {
    private final Disposable myCs;
    private final GHPRMentionableUsersProvider myMentionableUsersProvider;
    private final GHPRReviewDataProvider myReviewDataProvider;
    private final IconsProvider<String> myAvatarIconsProvider;

    public GHViewModelWithTextCompletionImpl(Disposable parentCs,
                                             GHPRMentionableUsersProvider mentionableUsersProvider,
                                             GHPRReviewDataProvider reviewData,
                                             IconsProvider<String> avatarIconsProvider) {
        myCs = Disposable.newDisposable(getClass().getName());
        Disposer.register(parentCs, myCs);
        myMentionableUsersProvider = mentionableUsersProvider;
        myReviewDataProvider = reviewData;
        myAvatarIconsProvider = avatarIconsProvider;
    }

    @Override
    public void withMentionCompletionModel(Consumer<GHViewModelMentionCompletion> consumer) {
        Disposable vmCs = Disposable.newDisposable(GHViewModelMentionCompletionImpl.class.getName());
        if (!Disposer.tryRegister(myCs, vmCs)) {
            return;
        }
        GHViewModelMentionCompletionImpl vm =
            new GHViewModelMentionCompletionImpl(vmCs, myMentionableUsersProvider, myReviewDataProvider, myAvatarIconsProvider);
        try {
            consumer.accept(vm);
        }
        finally {
            Disposer.dispose(vmCs);
        }
    }
}
