// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.pullrequest.comment;

import consulo.project.Project;
import consulo.versionControlSystem.review.issue.IssueIdsProcessor;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: fix bug with CRLF line endings from markdown library
public class GHMarkdownToHtmlConverter {
    public static final String OPEN_PR_LINK_PREFIX = "ghpullrequest:";

    private static final Pattern CAPTURE_USER_ID_REGEX =
        Pattern.compile("(?<=^|[\\s\\p{Punct}])(@[a-zA-Z0-9-]+)(?=[\\s\\p{Punct}]|$)", Pattern.MULTILINE);
    private static final Pattern CAPTURE_PR_ID_REGEX =
        Pattern.compile("(?<=^|[\\s\\p{Punct}])(#\\d+)(?=[\\s\\p{Punct}]|$)", Pattern.MULTILINE);
    private static final String CODE_FENCE = "```";

    private final @Nullable Project myProject;

    public GHMarkdownToHtmlConverter(@Nullable Project project) {
        myProject = project;
    }

    public String convertMarkdown(String markdownText) {
        return convertMarkdown(markdownText, null);
    }

    /**
     * Marks links to other pull requests (recognized by a prefixed '#') as special links.
     */
    public String convertMarkdown(String markdownText, @Nullable GithubServerPath server) {
        String text = markdownText.replace("\r", "");
        URI baseURI = server == null ? null : URI.create(server.toUrl() + "/");

        StringBuilder result = new StringBuilder(text.length());
        boolean inCodeFence = false;
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0) {
                result.append('\n');
            }
            if (line.strip().startsWith(CODE_FENCE)) {
                inCodeFence = !inCodeFence;
                result.append(line);
                continue;
            }
            if (inCodeFence) {
                result.append(line);
                continue;
            }
            result.append(processLine(line, baseURI));
        }
        return result.toString();
    }

    private static String processLine(String line, @Nullable URI baseURI) {
        StringBuilder result = new StringBuilder(line.length());
        int start = 0;
        while (start < line.length()) {
            int codeStart = line.indexOf('`', start);
            if (codeStart < 0) {
                result.append(processText(line.substring(start), baseURI));
                break;
            }
            int codeEnd = line.indexOf('`', codeStart + 1);
            if (codeEnd < 0) {
                result.append(processText(line.substring(start), baseURI));
                break;
            }
            result.append(processText(line.substring(start, codeStart), baseURI));
            result.append(line, codeStart, codeEnd + 1);
            start = codeEnd + 1;
        }
        return result.toString();
    }

    private static String processText(String text, @Nullable URI baseURI) {
        if (text.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length());
        int wordStart = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i == text.length() || Character.isWhitespace(text.charAt(i))) {
                String word = text.substring(wordStart, i);
                if (word.contains("://") || word.contains("](")) {
                    result.append(word);
                }
                else {
                    result.append(preprocessUserIds(preprocessPullRequestIds(word), baseURI));
                }
                if (i < text.length()) {
                    result.append(text.charAt(i));
                }
                wordStart = i + 1;
            }
        }
        return result.toString();
    }

    private static String preprocessPullRequestIds(String text) {
        if (text.indexOf('#') < 0) {
            return text;
        }
        Matcher matcher = CAPTURE_PR_ID_REGEX.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String pr = matcher.group();
            String prId = pr.substring(1);
            String url = OPEN_PR_LINK_PREFIX + prId;
            matcher.appendReplacement(result, Matcher.quoteReplacement(IssueIdsProcessor.markdownLink(pr, url)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String preprocessUserIds(String text, @Nullable URI baseURI) {
        if (text.indexOf('@') < 0 || text.contains("](" + OPEN_PR_LINK_PREFIX)) {
            return text;
        }
        Matcher matcher = CAPTURE_USER_ID_REGEX.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String mention = matcher.group();
            String userLogin = mention.substring(1);
            String replacement = baseURI == null ? mention : IssueIdsProcessor.markdownLink(mention, baseURI.resolve(userLogin).toString());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static String convertToHtml(String markdown, Project project) {
        return convertToHtml(markdown, project, null);
    }

    public static String convertToHtml(String markdown, Project project, @Nullable GithubServerPath server) {
        String processedText = IssueIdsProcessor.processIssueIdsMarkdown(project, markdown);
        return new GHMarkdownToHtmlConverter(project).convertMarkdown(processedText, server);
    }
}
