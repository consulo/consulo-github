// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow;

import consulo.review.toolwindow.ReviewTab;
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier;

public sealed interface GHPRToolWindowTab extends ReviewTab permits GHPRToolWindowTab.PullRequest, GHPRToolWindowTab.NewPullRequest {
    record PullRequest(GHPRIdentifier prId) implements GHPRToolWindowTab {
        @Override
        public String getId() {
            return "Review Details: " + prId.number();
        }

        @Override
        public boolean isReuseTabOnRequest() {
            return true;
        }
    }

    final class NewPullRequest implements GHPRToolWindowTab {
        public static final NewPullRequest INSTANCE = new NewPullRequest();

        private NewPullRequest() {
        }

        @Override
        public String getId() {
            return "New Pull Request";
        }

        @Override
        public boolean isReuseTabOnRequest() {
            return true;
        }
    }
}
