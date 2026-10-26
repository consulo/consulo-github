// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.github.icon.GitHubIconGroup;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.virtualFileSystem.VirtualFile;

@ExtensionImpl
public class GithubYamlIconProvider implements IconDescriptorUpdater {
    @RequiredReadAction
    @Override
    public void updateIcon(IconDescriptor iconDescriptor, PsiElement element, int flags) {
        if (!(element instanceof PsiFile file)) {
            return;
        }
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null && GithubYamlFileDetection.isGithubActionsFile(virtualFile)) {
            iconDescriptor.setMainIcon(GitHubIconGroup.github());
        }
    }
}
