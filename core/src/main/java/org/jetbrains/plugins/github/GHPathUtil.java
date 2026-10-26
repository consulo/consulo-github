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
package org.jetbrains.plugins.github;

import consulo.http.client.URIUtil;
import consulo.util.io.URLUtil;
import consulo.util.lang.Range;
import org.jspecify.annotations.Nullable;

import java.net.URI;

public final class GHPathUtil {
    private GHPathUtil() {
    }

    public static URI getWebURI(URI repository, String revisionOrBranch) {
        return URIUtil.resolveRelative(URIUtil.resolveRelative(repository, "commit"), revisionOrBranch);
    }

    public static URI getWebURI(URI repository, String revisionOrBranch, String relativePath, @Nullable Range<Integer> lineRange) {
        URI fileUri = URIUtil.resolveRelative(URIUtil.resolveRelative(URIUtil.resolveRelative(repository, "blob"), revisionOrBranch),
            URLUtil.encodePath(relativePath));

        if (lineRange != null) {
            StringBuilder fragmentBuilder = new StringBuilder();
            fragmentBuilder.append("L").append(lineRange.getFrom() + 1);
            if (!lineRange.getTo().equals(lineRange.getFrom())) {
                fragmentBuilder.append("-L").append(lineRange.getTo() + 1);
            }
            return URIUtil.withFragment(fileUri, fragmentBuilder.toString());
        }
        else {
            return fileUri;
        }
    }
}
