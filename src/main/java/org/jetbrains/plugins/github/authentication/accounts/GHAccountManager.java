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
import consulo.collaboration.auth.AccountManagerBase;
import consulo.collaboration.auth.AccountsRepository;
import consulo.collaboration.auth.CredentialsRepository;
import consulo.collaboration.auth.PasswordSafeCredentialsRepository;
import consulo.logging.Logger;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.util.GithubUtil;

/**
 * Handles application-level Github accounts.
 */
@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class GHAccountManager extends AccountManagerBase<GithubAccount, String> {
    private static final Logger LOG = Logger.getInstance(GHAccountManager.class);

    private final GHPersistentAccounts myPersistentAccounts;

    @Inject
    public GHAccountManager(GHPersistentAccounts persistentAccounts) {
        super(LOG);
        myPersistentAccounts = persistentAccounts;
    }

    @Override
    protected AccountsRepository<GithubAccount> accountsRepository() {
        return myPersistentAccounts;
    }

    @Override
    protected CredentialsRepository<GithubAccount, String> credentialsRepository() {
        return new PasswordSafeCredentialsRepository<>(
            GithubUtil.SERVICE_DISPLAY_NAME,
            PasswordSafeCredentialsRepository.CredentialsMapper.SIMPLE
        );
    }

    public static GithubAccount createAccount(String name, GithubServerPath server) {
        return new GithubAccount(name, server);
    }

    public boolean isAccountUnique(GithubServerPath server, String accountName) {
        for (GithubAccount account : getAccounts()) {
            if (account.getServer().equals(server, true) && account.getName().equals(accountName)) {
                return false;
            }
        }
        return true;
    }
}
