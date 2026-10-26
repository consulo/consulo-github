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

import consulo.review.reactions.CodeReviewReactionComponentFactory;
import consulo.ui.Component;
import consulo.ui.PopupPosition;
import consulo.ui.Space;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.popup.JBPopup;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.image.Image;
import consulo.ui.layout.HorizontalLayout;
import org.jetbrains.plugins.github.api.data.GHReactionContent;

import java.util.List;
import java.util.function.Consumer;

public final class GHReactionsPickerComponentFactory {
    private static final int EMOJI_SIZE = 26;

    private GHReactionsPickerComponentFactory() {
    }

    @RequiredUIAccess
    public static void showPopup(GHReactionsViewModel reactionsVm, Component parentComponent) {
        JBPopup[] popup = new JBPopup[1];
        Component emojiPicker = create(reactionsVm, List.of(GHReactionContent.values()), reaction -> {
            reactionsVm.toggle(reaction);
            JBPopup shown = popup[0];
            if (shown != null) {
                shown.cancel();
            }
        });
        popup[0] = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(emojiPicker, emojiPicker)
            .setResizable(false)
            .setFocusable(true)
            .setRequestFocus(true)
            .createPopup();

        popup[0].showBy(parentComponent, null, PopupPosition.BOTTOM);
    }

    @RequiredUIAccess
    private static Component create(GHReactionsViewModel reactionsVm,
                                    List<GHReactionContent> reactions,
                                    Consumer<GHReactionContent> onClick) {
        HorizontalLayout panel = HorizontalLayout.create(Space.MEDIUM);
        for (GHReactionContent reaction : reactions) {
            panel.add(createEmojiLabel(reactionsVm, reaction, onClick));
        }
        return panel;
    }

    @RequiredUIAccess
    private static Component createEmojiLabel(GHReactionsViewModel reactionsVm,
                                              GHReactionContent reaction,
                                              Consumer<GHReactionContent> onClick) {
        Image icon = reactionsVm.getReactionIconsProvider().getIcon(reaction, EMOJI_SIZE);
        return CodeReviewReactionComponentFactory.getInstance().createPickReactionButton(icon, () -> onClick.accept(reaction));
    }
}
