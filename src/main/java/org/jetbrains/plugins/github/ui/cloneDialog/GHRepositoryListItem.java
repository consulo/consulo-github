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
package org.jetbrains.plugins.github.ui.cloneDialog;

import consulo.accountManagement.Account;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.data.GithubUser;
import org.jspecify.annotations.Nullable;

public abstract class GHRepositoryListItem {
    private final Account myAccount;

    protected GHRepositoryListItem(Account account) {
        myAccount = account;
    }

    public Account getAccount() {
        return myAccount;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        GHRepositoryListItem item = (GHRepositoryListItem) other;

        return myAccount.getId().equals(item.myAccount.getId());
    }

    @Override
    public int hashCode() {
        return myAccount.getId().hashCode();
    }

    public static class Repo extends GHRepositoryListItem {
        private final GithubUser myUser;
        private final GithubRepo myRepo;

        public Repo(Account account, GithubUser user, GithubRepo repo) {
            super(account);
            myUser = user;
            myRepo = repo;
        }

        public GithubUser getUser() {
            return myUser;
        }

        public GithubRepo getRepo() {
            return myRepo;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            if (!super.equals(other)) {
                return false;
            }

            Repo repo = (Repo) other;

            if (!myUser.equals(repo.myUser)) {
                return false;
            }
            if (!myRepo.equals(repo.myRepo)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + myUser.hashCode();
            result = 31 * result + myRepo.hashCode();
            return result;
        }
    }

    public static class Error extends GHRepositoryListItem {
        private final Throwable myError;

        public Error(Account account, Throwable error) {
            super(account);
            myError = error;
        }

        public Throwable getError() {
            return myError;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            if (!super.equals(other)) {
                return false;
            }

            Error error = (Error) other;

            return myError.equals(error.myError);
        }

        @Override
        public int hashCode() {
            int result = super.hashCode();
            result = 31 * result + myError.hashCode();
            return result;
        }
    }
}
