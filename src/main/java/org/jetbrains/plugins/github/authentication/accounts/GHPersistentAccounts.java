// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.authentication.accounts;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.collaboration.auth.AccountsRepository;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import jakarta.inject.Singleton;

import java.util.LinkedHashSet;
import java.util.Set;

@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@State(name = "GithubAccounts", storages = @Storage("github.xml"))
public class GHPersistentAccounts
    implements AccountsRepository<GithubAccount>, PersistentStateComponent<GithubAccount[]> {

    private Set<GithubAccount> myAccounts = new LinkedHashSet<>();

    @Override
    public Set<GithubAccount> getAccounts() {
        return Set.copyOf(myAccounts);
    }

    @Override
    public void setAccounts(Set<GithubAccount> accounts) {
        myAccounts = new LinkedHashSet<>(accounts);
    }

    @Override
    public GithubAccount[] getState() {
        return myAccounts.toArray(new GithubAccount[0]);
    }

    @Override
    public void loadState(GithubAccount[] state) {
        myAccounts = new LinkedHashSet<>(Set.of(state));
    }
}
