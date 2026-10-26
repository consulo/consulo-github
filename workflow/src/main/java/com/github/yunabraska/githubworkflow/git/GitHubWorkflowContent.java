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
package com.github.yunabraska.githubworkflow.git;

import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-26
 */
public class GitHubWorkflowContent {
    private String name = "";
    private String path = "";
    private String type = "";
    private @Nullable String content;
    private @Nullable String encoding;
    private @Nullable String downloadUrl;

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getType() {
        return type;
    }

    public @Nullable String getContent() {
        return content;
    }

    public @Nullable String getEncoding() {
        return encoding;
    }

    public @Nullable String getDownloadUrl() {
        return downloadUrl;
    }
}
