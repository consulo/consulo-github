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
package org.jetbrains.plugins.github.ui.util;

import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.plugins.github.pullrequest.comment.GHMarkdownToHtmlConverter;

import java.util.function.LongConsumer;

public final class GHHtmlPaneUtil {
    private GHHtmlPaneUtil() {
    }

    /**
     * @return whether the link was a pull request opened in the IDE - any other link opens outside
     */
    @RequiredUIAccess
    public static boolean handleGithubHyperlink(String href, LongConsumer openPR) {
        if (href.startsWith(GHMarkdownToHtmlConverter.OPEN_PR_LINK_PREFIX)) {
            String prOrIssueId = href.substring(GHMarkdownToHtmlConverter.OPEN_PR_LINK_PREFIX.length());
            try {
                openPR.accept(Long.parseLong(prOrIssueId));
            }
            catch (NumberFormatException ignored) {
            }
            return true;
        }
        return false;
    }
}
