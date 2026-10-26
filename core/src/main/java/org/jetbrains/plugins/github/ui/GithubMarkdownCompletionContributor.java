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
package org.jetbrains.plugins.github.ui;

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.application.progress.ProgressManager;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionInitializationContext;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.logging.Logger;
import consulo.review.icon.IconsProvider;
import consulo.review.util.IncrementallyComputedValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.pullrequest.ui.comment.GHViewModelWithTextCompletion;

import java.util.List;
import java.util.concurrent.TimeUnit;

@ExtensionImpl
public class GithubMarkdownCompletionContributor extends CompletionContributor implements DumbAware {
    private static final Logger LOG = Logger.getInstance(GithubMarkdownCompletionContributor.class);

    private static final int AVATAR_SIZE = 16;
    private static final long USERS_WAIT_MILLIS = TimeUnit.SECONDS.toMillis(10);

    @RequiredReadAction
    @Override
    public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        GHViewModelWithTextCompletion vmWithTextCompletion = parameters.getEditor().getUserData(GHViewModelWithTextCompletion.MENTIONS_COMPLETION_KEY);
        if (vmWithTextCompletion == null) {
            return;
        }

        String positionText = parameters.getPosition().getText();
        int dummyIndex = positionText.indexOf(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED);
        String beforeCaret = dummyIndex < 0 ? positionText : positionText.substring(0, dummyIndex);
        int start = beforeCaret.length();
        while (start > 0 && !Character.isWhitespace(beforeCaret.charAt(start - 1))) {
            start--;
        }
        String text = beforeCaret.substring(start);
        if (!text.startsWith("@")) {
            return;
        }

        CompletionResultSet customResult = result.withPrefixMatcher(text).caseInsensitive();

        vmWithTextCompletion.withMentionCompletionModel(vm -> {
            processUsers(vm.getPullRequestParticipants(), vm.getAvatarIconsProvider(), customResult, 10.0);
            processUsers(vm.getMentionableUsers(), vm.getAvatarIconsProvider(), customResult, 1.0);
        });
    }

    private static void processUsers(ObservableValue<IncrementallyComputedValue<List<GHUser>>> users,
                                     IconsProvider<String> avatarIconsProvider,
                                     CompletionResultSet customResult,
                                     double priority) {
        long deadline = System.currentTimeMillis() + USERS_WAIT_MILLIS;
        int consumed = 0;
        while (true) {
            ProgressManager.checkCanceled();

            IncrementallyComputedValue<List<GHUser>> value = users.get();
            List<GHUser> loaded = value.getValueOrNull();
            if (loaded != null && loaded.size() > consumed) {
                for (GHUser user : loaded.subList(consumed, loaded.size())) {
                    customResult.addElement(createLookupElement(avatarIconsProvider, user, priority));
                }
                consumed = loaded.size();
            }

            Exception error = value.getExceptionOrNull();
            if (error != null) {
                LOG.error("Error fetching GitHub users for completion", error);
                return;
            }
            if (value.isComplete() || System.currentTimeMillis() > deadline) {
                return;
            }

            try {
                Thread.sleep(50);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static LookupElement createLookupElement(IconsProvider<String> avatarIconsProvider, GHUser user, double priority) {
        LookupElementBuilder elementBuilder = LookupElementBuilder.create(user, "@" + user.getLogin())
            .withIcon(avatarIconsProvider.getIcon(user.getAvatarUrl(), AVATAR_SIZE))
            .withTailText(user.getName())
            .withPresentableText("@" + user.getLogin() + " ")
            .withInsertHandler((context, item) -> {
                int offset = context.getSelectionEndOffset();
                CharSequence chars = context.getDocument().getCharsSequence();
                if (offset == chars.length() || chars.charAt(offset) != ' ') {
                    context.getDocument().insertString(offset, " ");
                    context.getEditor().getCaretModel().moveToOffset(offset + 1);
                }
            });

        String name = user.getName();
        LookupElementBuilder element = name != null && !name.isBlank() ? elementBuilder.withLookupString("@" + name) : elementBuilder;
        return PrioritizedLookupElement.withPriority(element, priority);
    }

    @Override
    public Language getLanguage() {
        return Language.ANY;
    }
}
