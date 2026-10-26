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
package org.jetbrains.plugins.github.tasks;

import consulo.application.util.DateFormatUtil;
import consulo.github.localize.GithubLocalize;
import consulo.task.SimpleComment;
import org.jspecify.annotations.Nullable;

import java.util.Date;

final class GithubComment extends SimpleComment {
    private final @Nullable String myAvatarUrl;
    private final String myUserHtmlUrl;

    public GithubComment(@Nullable Date date,
                         @Nullable String author,
                         String text,
                         @Nullable String avatarUrl,
                         String userHtmlUrl) {
        super(date, author, text);
        myAvatarUrl = avatarUrl;
        myUserHtmlUrl = userHtmlUrl;
    }

    @Override
    public void appendTo(StringBuilder builder) {
        builder.append("<hr>");
        builder.append("<table>");
        builder.append("<tr><td>");
        if (myAvatarUrl != null) {
            builder.append("<img src=\"").append(myAvatarUrl).append("\" height=\"40\" width=\"40\"/><br>");
        }
        builder.append("</td><td>");
        if (getAuthor() != null) {
            builder.append("<b>").append(GithubLocalize.taskCommentAuthor().get()).append("</b> <a href=\"").append(myUserHtmlUrl)
                .append("\">").append(getAuthor()).append("</a><br>");
        }
        if (getDate() != null) {
            builder.append("<b>").append(GithubLocalize.taskCommentDate().get()).append("</b> ")
                .append(DateFormatUtil.formatDateTime(getDate())).append("<br>");
        }
        builder.append("</td></tr></table>");

        builder.append(getText()).append("<br>");
    }
}
