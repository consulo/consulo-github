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
package org.jetbrains.plugins.github.extensions;

import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import org.jetbrains.plugins.github.exceptions.GHAPIExceptionUtil;

import java.util.List;
import java.util.Locale;

final class GHGitErrorMessagesUtils {
    private GHGitErrorMessagesUtils() {
    }

    static LocalizeValue oauthRestrictionMessage(List<String> errorOutput) {
        for (String errorOutputLine : errorOutput) {
            String line = errorOutputLine.toLowerCase(Locale.ROOT);
            if (line.contains("fatal: repository") && line.contains("not found")
                || line.contains("fatal: unable to access") && line.contains("the requested url returned error: 403")) {
                return GithubLocalize.gitOperationOauthRestrictionsError(GHAPIExceptionUtil.OAUTH_ERROR_SUPPORT_LINK);
            }
        }
        return LocalizeValue.empty();
    }
}
