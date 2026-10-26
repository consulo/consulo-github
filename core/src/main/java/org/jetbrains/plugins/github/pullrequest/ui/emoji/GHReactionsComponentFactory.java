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
import consulo.localize.LocalizeValue;
import consulo.review.comment.CodeReviewCommentBodyBlock;
import consulo.review.comment.CodeReviewComponentCommentBodyBlock;
import consulo.review.comment.CodeReviewMarkdownCommentBodyBlock;
import consulo.review.reactions.CodeReviewReactionComponentFactory;
import consulo.review.reactions.CodeReviewReactionPillPresentation;
import consulo.review.util.ReviewUIUtil;
import consulo.ui.Component;
import consulo.ui.Space;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.ui.layout.HorizontalLayout;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import org.jetbrains.plugins.github.api.data.GHReactionContent;
import org.jetbrains.plugins.github.api.data.GHUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class GHReactionsComponentFactory {
    private GHReactionsComponentFactory() {
    }

    @RequiredUIAccess
    public static Component create(Disposable parentDisposable, GHReactionsViewModel reactionsVm) {
        return create(parentDisposable, reactionsVm, false);
    }

    @RequiredUIAccess
    public static Component create(Disposable parentDisposable, GHReactionsViewModel reactionsVm, boolean canReact) {
        CodeReviewReactionComponentFactory factory = CodeReviewReactionComponentFactory.getInstance();
        HorizontalLayout panel = HorizontalLayout.create(Space.MEDIUM);
        Component reactionPicker = createReactionPickerButton(factory, reactionsVm);

        List<GHReactionContent> shown = new ArrayList<>();
        Disposable[] itemsDisposable = {Disposable.newDisposable("GitHub reactions")};
        Disposer.register(parentDisposable, () -> Disposer.dispose(itemsDisposable[0]));

        ReviewUIUtil.bind(parentDisposable, reactionsVm.getReactionsWithInfo(), reactionsWithInfo -> {
            List<GHReactionContent> reactions = List.copyOf(reactionsWithInfo.keySet());
            if (!reactions.equals(shown)) {
                shown.clear();
                shown.addAll(reactions);
                Disposer.dispose(itemsDisposable[0]);
                itemsDisposable[0] = Disposable.newDisposable("GitHub reactions");
                panel.removeAll();
                for (GHReactionContent reaction : reactions) {
                    panel.add(createReactionLabel(itemsDisposable[0], factory, reactionsVm, reaction));
                }
                panel.add(reactionPicker);
            }
            reactionPicker.setVisible(canReact || !reactionsWithInfo.isEmpty());
        });
        return panel;
    }

    public static List<CodeReviewCommentBodyBlock> appendReactions(List<CodeReviewCommentBodyBlock> blocks,
                                                                   String markdown,
                                                                   GHReactionsViewModel reactionsVm,
                                                                   boolean canReact) {
        if (!canReact && reactionsVm.getReactionsWithInfo().get().isEmpty()) {
            return blocks;
        }
        List<CodeReviewCommentBodyBlock> result = new ArrayList<>();
        if (blocks.isEmpty()) {
            result.add(new CodeReviewMarkdownCommentBodyBlock(markdown));
        }
        else {
            result.addAll(blocks);
        }
        CodeReviewComponentCommentBodyBlock reactionsBlock = parentDisposable -> create(parentDisposable, reactionsVm, canReact);
        result.add(reactionsBlock);
        return List.copyOf(result);
    }

    @RequiredUIAccess
    private static Component createReactionLabel(Disposable parentDisposable,
                                                 CodeReviewReactionComponentFactory factory,
                                                 GHReactionsViewModel reactionsVm,
                                                 GHReactionContent reaction) {
        ReactionInfo initialInfo = Objects.requireNonNull(reactionsVm.getReactionsWithInfo().get().get(reaction));
        MutableObservableValue<CodeReviewReactionPillPresentation> state =
            MutableObservableValue.of(new Presentation(reactionsVm, reaction, initialInfo));
        Runnable unsubscribe = reactionsVm.getReactionsWithInfo().addListener(reactionsWithInfo -> {
            ReactionInfo info = reactionsWithInfo.get(reaction);
            if (info != null) {
                state.set(new Presentation(reactionsVm, reaction, info));
            }
        });
        Disposer.register(parentDisposable, unsubscribe::run);
        return factory.createReactionButton(parentDisposable, state, () -> reactionsVm.toggle(reaction));
    }

    @RequiredUIAccess
    private static Component createReactionPickerButton(CodeReviewReactionComponentFactory factory, GHReactionsViewModel reactionsVm) {
        return factory.createNewReactionButton(anchor -> GHReactionsPickerComponentFactory.showPopup(reactionsVm, anchor));
    }

    private static final class Presentation implements CodeReviewReactionPillPresentation {
        private final GHReactionsViewModel myReactionsVm;
        private final GHReactionContent myReaction;
        private final ReactionInfo myReactionInfo;

        private Presentation(GHReactionsViewModel reactionsVm, GHReactionContent reaction, ReactionInfo reactionInfo) {
            myReactionsVm = reactionsVm;
            myReaction = reaction;
            myReactionInfo = reactionInfo;
        }

        @Override
        public LocalizeValue getReactionName() {
            return LocalizeValue.of(myReaction.getPresentableName());
        }

        @Override
        public List<LocalizeValue> getReactors() {
            List<LocalizeValue> reactors = new ArrayList<>();
            for (GHUser user : myReactionInfo.users()) {
                reactors.add(LocalizeValue.of(user.getPresentableName()));
            }
            return reactors;
        }

        @Override
        public boolean isOwnReaction() {
            return myReactionInfo.isReactedByCurrentUser();
        }

        @RequiredUIAccess
        @Override
        public Image getIcon(int size) {
            return myReactionsVm.getReactionIconsProvider().getIcon(myReaction, size);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Presentation that)) {
                return false;
            }
            return myReaction == that.myReaction && myReactionInfo.equals(that.myReactionInfo);
        }

        @Override
        public int hashCode() {
            int result = myReaction.hashCode();
            result = 31 * result + myReactionInfo.hashCode();
            return result;
        }
    }
}
