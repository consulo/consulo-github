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
import consulo.localize.LocalizeValue;
import consulo.ui.RenderItem;
import consulo.ui.TextAttribute;
import consulo.ui.TextItemPresentation;
import consulo.ui.TextItemRenderer;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.style.ComponentColors;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.data.GithubUser;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class GHRepositoryListCellRenderer implements TextItemRenderer<GHRepositoryListItem> {
    private static final TextAttribute LINK_ATTRIBUTES = new TextAttribute(TextAttribute.STYLE_PLAIN, ComponentColors.LINK_FOREGROUND);

    private final ErrorHandler myErrorHandler;
    private final Supplier<Collection<Account>> myAccountsSupplier;

    public GHRepositoryListCellRenderer(ErrorHandler errorHandler, Supplier<Collection<Account>> accountsSupplier) {
        myErrorHandler = errorHandler;
        myAccountsSupplier = accountsSupplier;
    }

    public List<GHRepositoryListItem> withAccountNames(List<GHRepositoryListItem> items) {
        List<GHRepositoryListItem> result = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            GHRepositoryListItem value = items.get(index);
            if (showAccountNameAbove(items, index)) {
                result.add(new AccountNameItem(value.getAccount(), GHCloneDialogExtensionBase.getNameWithServer(value.getAccount())));
            }
            result.add(value);
        }
        return result;
    }

    private boolean showAccountNameAbove(List<GHRepositoryListItem> items, int index) {
        return myAccountsSupplier.get().size() > 1 &&
            (index == 0 || !items.get(index).getAccount().getId().equals(items.get(index - 1).getAccount().getId()));
    }

    public static boolean isAccountName(GHRepositoryListItem item) {
        return item instanceof AccountNameItem;
    }

    @Override
    @RequiredUIAccess
    public void render(TextItemPresentation presentation, RenderItem<GHRepositoryListItem> item) {
        GHRepositoryListItem value = item.getValue();
        if (value instanceof AccountNameItem accountName) {
            presentation.append(accountName.getName(), TextAttribute.GRAY);
        }
        else if (value instanceof GHRepositoryListItem.Repo repoItem) {
            GithubUser user = repoItem.getUser();
            GithubRepo repo = repoItem.getRepo();

            String description = repo.getDescription();
            if (description != null && !description.isEmpty()) {
                presentation.withTooltip(LocalizeValue.of(description));
            }
            presentation.append(repo.getOwner().getLogin().equals(user.getLogin()) ? repo.getName() : repo.getFullName());
        }
        else if (value instanceof GHRepositoryListItem.Error errorItem) {
            Throwable error = errorItem.getError();

            presentation.append(myErrorHandler.getPresentableText(error), TextAttribute.ERROR);
            ErrorAction action = myErrorHandler.getAction(errorItem.getAccount(), error);
            presentation.append(" ");
            presentation.append(action.getName(), LINK_ATTRIBUTES);
        }
    }

    public static class AccountNameItem extends GHRepositoryListItem {
        private final String myName;

        public AccountNameItem(Account account, String name) {
            super(account);
            myName = name;
        }

        public String getName() {
            return myName;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return super.equals(other) && myName.equals(((AccountNameItem) other).myName);
        }

        @Override
        public int hashCode() {
            return 31 * super.hashCode() + myName.hashCode();
        }
    }

    public static class ErrorAction {
        private final LocalizeValue myName;
        private final Runnable myAction;

        public ErrorAction(LocalizeValue name, @RequiredUIAccess Runnable action) {
            myName = name;
            myAction = action;
        }

        public LocalizeValue getName() {
            return myName;
        }

        @RequiredUIAccess
        public void actionPerformed() {
            myAction.run();
        }
    }

    public interface ErrorHandler {
        LocalizeValue getPresentableText(Throwable error);

        ErrorAction getAction(Account account, Throwable error);
    }
}
