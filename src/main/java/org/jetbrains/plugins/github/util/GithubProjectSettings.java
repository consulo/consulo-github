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
package org.jetbrains.plugins.github.util;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.project.Project;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
@State(name = "GithubProjectSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GithubProjectSettings implements PersistentStateComponent<GithubProjectSettings.SettingsState> {
    private SettingsState myState = new SettingsState();

    public static class SettingsState {
        public List<String> branchProtectionPatterns = new ArrayList<>();
    }

    public List<String> getBranchProtectionPatterns() {
        return Collections.unmodifiableList(myState.branchProtectionPatterns);
    }

    public void setBranchProtectionPatterns(List<String> value) {
        myState.branchProtectionPatterns = new ArrayList<>(value);
    }

    @Override
    public SettingsState getState() {
        return myState;
    }

    @Override
    public void loadState(SettingsState state) {
        myState = state;
    }

    public static GithubProjectSettings getInstance(Project project) {
        return project.getInstance(GithubProjectSettings.class);
    }
}
