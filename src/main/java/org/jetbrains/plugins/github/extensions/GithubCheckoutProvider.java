/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import consulo.annotation.component.ExtensionImpl;
import consulo.disposer.Disposable;
import consulo.github.icon.GitHubIconGroup;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.versionControlSystem.checkout.CheckoutPage;
import consulo.versionControlSystem.checkout.CheckoutProvider;
import consulo.versionControlSystem.distributed.localize.DistributedVcsLocalize;
import git4idea.actions.BasicAction;
import git4idea.commands.Git;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class GithubCheckoutProvider implements CheckoutProvider {
    @Override
    public Image getIcon() {
        return GitHubIconGroup.github();
    }

    @Override
    public LocalizeValue getName() {
        return GithubLocalize.settingsConfigurableDisplayName();
    }

    @Override
    public LocalizeValue getActionName() {
        return DistributedVcsLocalize.cloneButton();
    }

    @Override
    @RequiredUIAccess
    public CheckoutPage createPage(@Nonnull Project project, @Nonnull Disposable uiDisposable) {
        return new GithubCheckoutPage(project, uiDisposable, Git.getInstance());
    }
}
