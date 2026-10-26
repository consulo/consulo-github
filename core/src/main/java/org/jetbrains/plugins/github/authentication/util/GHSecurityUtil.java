// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.authentication.util;

import consulo.application.Application;
import consulo.util.io.URLUtil;
import org.jetbrains.plugins.github.api.GithubServerPath;

import java.util.List;

public final class GHSecurityUtil {
    private static final String REPO_SCOPE = "repo";
    private static final String GIST_SCOPE = "gist";
    private static final String READ_ORG_SCOPE = "read:org";
    private static final String WORKFLOW_SCOPE = "workflow";
    private static final String USER_READ_SCOPE = "read:user";
    private static final String USER_EMAIL_SCOPE = "user:email";

    public static final List<String> MASTER_SCOPES = List.of(
        REPO_SCOPE,
        GIST_SCOPE,
        READ_ORG_SCOPE,
        WORKFLOW_SCOPE,
        USER_READ_SCOPE,
        USER_EMAIL_SCOPE
    );

    private GHSecurityUtil() {
    }

    public static String buildNewTokenUrl(GithubServerPath server) {
        return buildNewTokenUrl(server, MASTER_SCOPES);
    }

    public static String buildNewTokenUrl(GithubServerPath server, List<String> scopes) {
        String productName = Application.get().getName().get();

        return append(server, "settings/tokens/new")
            + "?description=" + URLUtil.encodeURIComponent(productName + " GitHub integration plugin")
            + "&scopes=" + URLUtil.encodeURIComponent(String.join(",", scopes));
    }

    private static String append(GithubServerPath server, String path) {
        Integer port = server.getPort();
        String suffix = server.getSuffix();
        return server.getSchema() + "://" + server.getHost() + (port == null ? "" : ":" + port)
            + (suffix == null ? "" : suffix) + "/" + path;
    }
}
