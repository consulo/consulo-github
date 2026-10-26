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
package org.jetbrains.plugins.github.extensions;

import consulo.language.psi.PsiFile;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

public final class GithubYamlFileDetection {
    private GithubYamlFileDetection() {
    }

    public static boolean isGithubActionsFile(@Nullable PsiFile psiFile) {
        VirtualFile virtualFile = psiFile == null ? null : psiFile.getOriginalFile().getVirtualFile();
        if (virtualFile == null) {
            return false;
        }
        return isGithubActionsFile(virtualFile);
    }

    public static boolean isGithubActionsFile(VirtualFile virtualFile) {
        return isGithubActionYamlFile(virtualFile) || isGithubWorkflowYamlFile(virtualFile);
    }

    public static boolean isGithubActionFile(@Nullable VirtualFile virtualFile) {
        if (virtualFile == null) {
            return false;
        }
        return isGithubActionYamlFile(virtualFile);
    }

    public static boolean isGithubWorkflowFile(@Nullable PsiFile psiFile) {
        VirtualFile virtualFile = psiFile == null ? null : psiFile.getOriginalFile().getVirtualFile();
        if (virtualFile == null) {
            return false;
        }
        return isGithubWorkflowYamlFile(virtualFile);
    }

    public static boolean isGithubWorkflowFile(@Nullable VirtualFile virtualFile) {
        if (virtualFile == null) {
            return false;
        }
        return isGithubWorkflowYamlFile(virtualFile);
    }

    private static boolean isGithubActionYamlFile(VirtualFile virtualFile) {
        String fileName = virtualFile.getName();
        return !virtualFile.isDirectory()
            && (FileUtil.extensionEquals(fileName, "yml") || FileUtil.extensionEquals(fileName, "yaml"))
            && virtualFile.getNameWithoutExtension().equals("action");
    }

    private static boolean isGithubWorkflowYamlFile(VirtualFile virtualFile) {
        String fileName = virtualFile.getName();
        String filePath = virtualFile.getPath();
        int workflowDirIndex = filePath.indexOf("/workflows");
        int githubDirIndex = filePath.indexOf(".github/");
        return !virtualFile.isDirectory()
            && (FileUtil.extensionEquals(fileName, "yml") || FileUtil.extensionEquals(fileName, "yaml"))
            && workflowDirIndex != -1
            && githubDirIndex != -1
            && workflowDirIndex > githubDirIndex;
    }
}
