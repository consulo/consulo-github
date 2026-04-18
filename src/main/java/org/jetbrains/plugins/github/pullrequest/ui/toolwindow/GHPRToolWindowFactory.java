// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2025 consulo.io
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

import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.github.icon.GitHubIconGroup;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.content.Content;
import consulo.ui.ex.content.ContentFactory;
import consulo.ui.ex.toolWindow.ToolWindow;
import consulo.ui.ex.toolWindow.ToolWindowAnchor;
import consulo.ui.image.Image;
import consulo.ui.image.ImageKey;

import javax.swing.*;

@ExtensionImpl
public class GHPRToolWindowFactory implements consulo.project.ui.wm.ToolWindowFactory, DumbAware {
    public static final String ID = "Pull Requests";

    @Override
    public String getId() {
        return ID;
    }

    @RequiredUIAccess
    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        GHPRToolWindowContent toolWindowContent = new GHPRToolWindowContent(project);
        JComponent component = toolWindowContent.createComponent();

        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(component, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public ToolWindowAnchor getAnchor() {
        return ToolWindowAnchor.LEFT;
    }

    @Override
    public Image getIcon() {
        return GitHubIconGroup.github();
    }

    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Pull Requests");
    }

    @Override
    public boolean isDoNotActivateOnStart() {
        return true;
    }

    @Override
    public boolean canCloseContents() {
        return true;
    }
}
