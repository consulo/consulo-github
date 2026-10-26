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

import consulo.diff.util.LineRange;
import consulo.review.editor.CodeReviewEditorGutterControlsState;

import java.util.List;
import java.util.Set;

public record GHPRReviewEditorGutterControlsState(Set<Integer> linesWithComments,
                                                  Set<Integer> linesWithNewComments,
                                                  List<LineRange> commentableLines) implements CodeReviewEditorGutterControlsState {
    @Override
    public Set<Integer> getLinesWithComments() {
        return linesWithComments;
    }

    @Override
    public Set<Integer> getLinesWithNewComments() {
        return linesWithNewComments;
    }

    @Override
    public boolean isLineCommentable(int lineIdx) {
        for (LineRange it : commentableLines) {
            int end = it.start() == it.end() ? it.end() + 1 : it.end();
            if (lineIdx >= it.start() && lineIdx < end) {
                return true;
            }
        }
        return false;
    }
}
