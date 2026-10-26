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
package org.jetbrains.plugins.github.ui;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.configurable.StandardConfigurableIds;
import consulo.disposer.Disposable;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.IntBox;
import consulo.ui.Label;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.style.ComponentColors;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.VerticalLayout;
import jakarta.inject.Inject;
import org.jetbrains.plugins.github.util.GithubSettings;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class GithubSettingsConfigurable extends SimpleConfigurableByProperties implements ApplicationConfigurable {
    public static final String ID = "org.jetbrains.plugins.github.ui.GithubSettingsConfigurable";

    private final GithubSettings myGithubSettings;

    @Inject
    public GithubSettingsConfigurable(GithubSettings githubSettings) {
        myGithubSettings = githubSettings;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public @Nullable String getParentId() {
        return StandardConfigurableIds.VCS_GROUP;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return GithubLocalize.settingsConfigurableDisplayName();
    }

    @RequiredUIAccess
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
        VerticalLayout root = VerticalLayout.create();

        CheckBox cloneUsingSsh = CheckBox.create(GithubLocalize.settingsCloneSsh());
        propertyBuilder.add(cloneUsingSsh, myGithubSettings::isCloneGitUsingSsh, myGithubSettings::setCloneGitUsingSsh);
        root.add(cloneUsingSsh);

        CheckBox markAsViewed = CheckBox.create(GithubLocalize.settingsAutomaticallyMarkAsViewed());
        propertyBuilder.add(markAsViewed, myGithubSettings::isAutomaticallyMarkAsViewed, myGithubSettings::setAutomaticallyMarkAsViewed);
        root.add(markAsViewed);

        CheckBox seenMarkers = CheckBox.create(GithubLocalize.settingsEnablePrSeenMarkers());
        propertyBuilder.add(seenMarkers, myGithubSettings::isSeenMarkersEnabled, myGithubSettings::setIsSeenMarkersEnabled);
        root.add(seenMarkers);

        IntBox timeout = IntBox.create();
        timeout.setRange(0, 60);
        propertyBuilder.add(timeout,
            () -> myGithubSettings.getConnectionTimeout() / 1000,
            value -> myGithubSettings.setConnectionTimeout(value * 1000));
        HorizontalLayout timeoutInput = HorizontalLayout.create();
        timeoutInput.add(timeout);
        timeoutInput.add(Label.create(GithubLocalize.settingsTimeoutSeconds()));
        root.add(DockLayout.create().left(Label.create(GithubLocalize.settingsTimeout())).right(timeoutInput));

        TextBox hostAliases = TextBox.create();
        hostAliases.setPlaceholder(LocalizeValue.of(GithubSettings.DEFAULT_HOST_ALIASES));
        propertyBuilder.add(hostAliases, myGithubSettings::getHostAliases, myGithubSettings::setHostAliases);
        root.add(DockLayout.create().left(Label.create(GithubLocalize.advancedSettingGithubAliases())).right(hostAliases));
        Label hostAliasesDescription = Label.create(GithubLocalize.advancedSettingGithubAliasesDescription());
        hostAliasesDescription.setForegroundColor(ComponentColors.DISABLED_TEXT);
        root.add(hostAliasesDescription);

        return root;
    }
}
