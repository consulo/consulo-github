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
package org.jetbrains.plugins.github.ui.cloneDialog;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountListener;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.accountManagement.ui.AccountComponentFactory;
import consulo.application.Application;
import consulo.dataContext.DataManager;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.review.localize.ReviewLocalize;
import consulo.ui.Avatar;
import consulo.ui.AvatarStyle;
import consulo.ui.Button;
import consulo.ui.Component;
import consulo.ui.ImageBox;
import consulo.ui.Label;
import consulo.ui.ListBox;
import consulo.ui.PopupPosition;
import consulo.ui.TextBox;
import consulo.ui.TextBoxStyle;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.DefaultActionGroup;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.ui.image.Image;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.ui.style.StandardColors;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import consulo.versionControlSystem.checkout.CheckoutCallback;
import consulo.versionControlSystem.checkout.CheckoutPage;
import consulo.versionControlSystem.distributed.localize.DistributedVcsLocalize;
import consulo.versionControlSystem.distributed.repository.ClonePathProvider;
import consulo.versionControlSystem.distributed.ui.CloneDvcsValidationUtils;
import git4idea.GitUtil;
import git4idea.checkout.GitCloneUtils;
import git4idea.remote.GitRememberedInputs;
import git4idea.ui.GitShallowCloneComponentFactory;
import git4idea.ui.GitShallowCloneViewModel;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubMissingTokenException;
import org.jetbrains.plugins.github.util.GithubGitHelper;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class GHCloneDialogExtensionComponentBase implements CheckoutPage {
    protected final Project myProject;
    protected final Disposable myDisposable;
    protected final AccountManager myAccountManager;

    private final GithubGitHelper myGithubGitHelper = GithubGitHelper.getInstance();
    private final UIAccess myUiAccess;

    // UI
    private final DockLayout myWrapper;
    private final DockLayout myRepositoriesPanel;
    private final HorizontalLayout myAccountsActionsPanel;
    private final ListBox<GHRepositoryListItem> myRepositoryList;
    private final MutableFlatDataModel<GHRepositoryListItem> myRepositoryListModel;
    private final GHRepositoryListCellRenderer myRepositoryListRenderer;
    private final ErrorHandler myErrorHandler = new ErrorHandler();

    private final TextBox mySearchField;
    private final FileChooserTextBoxBuilder.Controller myDirectoryField;
    private final Label myDirectoryErrorLabel;
    private final HorizontalLayout myAccountsPanel;
    private @Nullable Disposable myAccountsPanelDisposable;

    private final Path myDefaultParentPath;
    private boolean myDirectoryModifiedByUser;

    // state
    private final GHCloneDialogRepositoryListLoaderImpl myLoader;
    private boolean myInLoginState;
    private @Nullable String mySelectedUrl;
    private boolean myUpdatingList;
    private boolean myDisposed;
    private @Nullable Component myContent;
    private @Nullable Context myContext;

    private List<Account> myAccounts = List.of();

    private final GitShallowCloneViewModel myShallowCloneModel = new GitShallowCloneViewModel();

    @RequiredUIAccess
    protected GHCloneDialogExtensionComponentBase(Project project, Disposable parentDisposable, AccountManager accountManager) {
        myProject = project;
        myDisposable = parentDisposable;
        myAccountManager = accountManager;
        myUiAccess = UIAccess.current();

        Disposer.register(parentDisposable, () -> myDisposed = true);

        myLoader = new GHCloneDialogRepositoryListLoaderImpl(parentDisposable, accountManager);

        myRepositoryListRenderer = new GHRepositoryListCellRenderer(myErrorHandler, this::getAccounts);

        myRepositoryListModel = FlatDataModel.lazyOf(List.of());
        myRepositoryList = ListBox.create(myRepositoryListModel);
        myRepositoryList.setRenderer(myRepositoryListRenderer);
        myRepositoryList.isSeparator(GHRepositoryListCellRenderer::isAccountName);
        myRepositoryList.addValueListener(event -> {
            if (myUpdatingList) {
                return;
            }
            GHRepositoryListItem value = event.getValue();
            if (value instanceof GHRepositoryListItem.Error error) {
                myUiAccess.give(() -> {
                    myRepositoryList.setValue(null, false);
                    updateSelectedUrl();
                });
                myErrorHandler.getAction(error.getAccount(), error.getError()).actionPerformed();
                return;
            }
            updateSelectedUrl();
        });
        myLoader.getListModel().addChangeListener(parentDisposable, () -> {
            updateRepositoryList();
            updateSelectedUrl();
        });

        ImageBox busyIcon = ImageBox.create(Image.busy());
        busyIcon.setVisible(false);
        myLoader.addLoadingStateListener(() -> busyIcon.setVisible(myLoader.getLoading()));

        mySearchField = TextBox.create();
        mySearchField.addStyle(TextBoxStyle.HAS_CLEAR_BUTTON);
        mySearchField.addValueListener(event -> {
            updateRepositoryList();
            updateSelectedUrl();
        });

        FileChooserTextBoxBuilder directoryBuilder = FileChooserTextBoxBuilder.create(project);
        directoryBuilder.uiDisposable(parentDisposable);
        directoryBuilder.fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withShowFileSystemRoots(true)
            .withHideIgnored(false));
        directoryBuilder.dialogTitle(DistributedVcsLocalize.cloneDestinationDirectoryTitle());
        directoryBuilder.dialogDescription(DistributedVcsLocalize.cloneDestinationDirectoryDescription());
        myDirectoryField = directoryBuilder.build();

        myDefaultParentPath = Paths.get(ClonePathProvider.defaultParentDirectoryPath(project, GitRememberedInputs.getInstance())).toAbsolutePath();
        myDirectoryField.setValue(myDefaultParentPath.toString(), false);
        myDirectoryField.getComponent().addValueListener(event -> {
            myDirectoryModifiedByUser = true;
            validate();
        });

        myDirectoryErrorLabel = Label.create();
        myDirectoryErrorLabel.setForegroundColor(StandardColors.RED);

        myAccountsPanel = HorizontalLayout.create();
        myAccountsActionsPanel = HorizontalLayout.create();
        myAccountsActionsPanel.add(busyIcon);
        myAccountsActionsPanel.add(myAccountsPanel);

        DockLayout searchPanel = DockLayout.create();
        searchPanel.center(mySearchField);
        searchPanel.right(myAccountsActionsPanel);

        FormBuilder bottomPanel = FormBuilder.create();
        bottomPanel.addLabeled(ReviewLocalize.cloneDialogDirectoryToCloneLabelText(), myDirectoryField.getComponent());
        bottomPanel.addBottom(GitShallowCloneComponentFactory.createShallowCloneRow(myShallowCloneModel));
        bottomPanel.addBottom(myDirectoryErrorLabel);

        myRepositoriesPanel = DockLayout.create();
        myRepositoriesPanel.top(searchPanel);
        myRepositoriesPanel.center(ScrollableLayout.create(myRepositoryList));
        myRepositoriesPanel.bottom(bottomPanel.build());

        myWrapper = DockLayout.create();
    }

    private class ErrorHandler implements GHRepositoryListCellRenderer.ErrorHandler {
        @Override
        public LocalizeValue getPresentableText(Throwable error) {
            if (error instanceof GithubMissingTokenException) {
                return ReviewLocalize.accountTokenMissing();
            }
            if (error instanceof GithubAuthenticationException) {
                return GithubLocalize.credentialsInvalidAuthData("");
            }
            return ReviewLocalize.cloneDialogErrorLoadRepositories();
        }

        @Override
        public GHRepositoryListCellRenderer.ErrorAction getAction(Account account, Throwable error) {
            if (error instanceof GithubAuthenticationException) {
                return new GHRepositoryListCellRenderer.ErrorAction(GithubLocalize.accountsRelogin(), () -> switchToLogin(account));
            }
            return new GHRepositoryListCellRenderer.ErrorAction(GithubLocalize.retryLink(), () -> {
                myLoader.clear(account);
                myLoader.loadRepositories(account);
            });
        }
    }

    protected abstract boolean isAccountHandled(Account account);

    protected Collection<Account> getAccounts() {
        return myAccounts;
    }

    @RequiredUIAccess
    protected abstract Component createLoginPanel(@Nullable Account account, @RequiredUIAccess Runnable cancelHandler);

    @RequiredUIAccess
    protected abstract Collection<AnAction> createAccountMenuLoginActions(@Nullable Account account);

    @Override
    @RequiredUIAccess
    public Component createComponent(Context context) {
        myContext = context;

        Button loginActionsButton = Button.create(LocalizeValue.empty());
        loginActionsButton.setIcon(PlatformIconGroup.generalAdd());
        loginActionsButton.addClickListener(event -> {
            ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
                null,
                new DefaultActionGroup(List.copyOf(createAccountMenuLoginActions(null))),
                DataManager.getInstance().getDataContext(loginActionsButton),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
            );
            popup.showBy(loginActionsButton, null, PopupPosition.BOTTOM);
        });
        myAccountsActionsPanel.add(loginActionsButton);

        setupAccountsListeners();
        return myWrapper;
    }

    @RequiredUIAccess
    private void setupAccountsListeners() {
        setCurrentAccounts(loadHandledAccounts(), true);

        myAccountManager.addListener(new AccountListener() {
            @Override
            public void accountsChanged() {
                myUiAccess.execute(() -> {
                    if (!myDisposed) {
                        setCurrentAccounts(loadHandledAccounts(), false);
                    }
                });
            }

            @Override
            public void accountChanged(Account account) {
                myUiAccess.execute(() -> {
                    if (!myDisposed) {
                        onAccountChanged(account);
                    }
                });
            }
        }, myDisposable);
    }

    private List<Account> loadHandledAccounts() {
        AccountType<?> type = myAccountManager.findAccountType(GithubAccountType.ID);
        if (type == null) {
            return List.of();
        }
        List<Account> result = new ArrayList<>();
        for (Account account : myAccountManager.getAccounts(type, myProject)) {
            if (isAccountHandled(account)) {
                result.add(account);
            }
        }
        return result;
    }

    @RequiredUIAccess
    private void setCurrentAccounts(List<Account> newValue, boolean initial) {
        List<Account> oldValue = myAccounts;
        myAccounts = List.copyOf(newValue);

        Set<String> oldIds = collectIds(oldValue);
        Set<String> newIds = collectIds(newValue);

        boolean changed = initial;
        for (Account account : oldValue) {
            if (!newIds.contains(account.getId())) {
                myLoader.clear(account);
                changed = true;
            }
        }
        for (Account account : newValue) {
            if (!oldIds.contains(account.getId())) {
                myLoader.loadRepositories(account);
                changed = true;
            }
        }

        if (!changed) {
            return;
        }

        rebuildAccountsPanel();

        if (newValue.isEmpty()) {
            switchToLogin(null);
        }
        else {
            switchToRepositories();
        }
    }

    @RequiredUIAccess
    private void onAccountChanged(Account account) {
        if (account.isAuthFailed() || !isAccountHandled(account)) {
            return;
        }

        List<Account> accounts = new ArrayList<>(myAccounts);
        boolean found = false;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getId().equals(account.getId())) {
                accounts.set(i, account);
                found = true;
            }
        }
        if (!found) {
            return;
        }
        myAccounts = List.copyOf(accounts);

        myLoader.clear(account);
        myLoader.loadRepositories(account);
        rebuildAccountsPanel();
        switchToRepositories();
    }

    @RequiredUIAccess
    private void rebuildAccountsPanel() {
        Disposable oldDisposable = myAccountsPanelDisposable;
        if (oldDisposable != null) {
            Disposer.dispose(oldDisposable);
        }
        Disposable panelDisposable = Disposable.newDisposable();
        Disposer.register(myDisposable, panelDisposable);
        myAccountsPanelDisposable = panelDisposable;

        myAccountsPanel.removeAll();

        AccountComponentFactory accountComponentFactory = Application.get().getInstance(AccountComponentFactory.class);
        for (Account account : myAccounts) {
            Avatar avatar = accountComponentFactory.createAvatar(account, AvatarStyle.SMALL, panelDisposable);
            avatar.setToolTipText(LocalizeValue.of(GHCloneDialogExtensionBase.getNameWithServer(account)));
            myAccountsPanel.add(avatar);
        }
    }

    private static Set<String> collectIds(Collection<Account> accounts) {
        Set<String> ids = new HashSet<>();
        for (Account account : accounts) {
            ids.add(account.getId());
        }
        return ids;
    }

    @RequiredUIAccess
    protected void switchToLogin(@Nullable Account account) {
        setContent(createLoginPanel(account, this::switchToRepositories));
        myInLoginState = true;
        updateSelectedUrl();
    }

    @RequiredUIAccess
    private void switchToRepositories() {
        setContent(myRepositoriesPanel);
        myInLoginState = false;
        updateSelectedUrl();
    }

    protected @Nullable Component getContent() {
        return myContent;
    }

    @RequiredUIAccess
    private void setContent(Component content) {
        if (myContent == content) {
            return;
        }
        myContent = content;
        myWrapper.center(content);
    }

    @Override
    @RequiredUIAccess
    public void doCheckout(CheckoutCallback listener) {
        String selectedUrl = mySelectedUrl;
        if (selectedUrl == null) {
            throw new IllegalStateException("Clone button is enabled when repository is not selected");
        }
        GitCloneUtils.clone(myProject, selectedUrl, myDirectoryField.getValue(), myShallowCloneModel.getShallowCloneOptions(), listener);
    }

    @RequiredUIAccess
    private void updateRepositoryList() {
        String filter = StringUtil.notNullize(mySearchField.getValue()).trim();

        List<GHRepositoryListItem> filtered = new ArrayList<>();
        for (GHRepositoryListItem item : myLoader.getListModel().getItems()) {
            if (filter.isEmpty() || StringUtil.containsIgnoreCase(getSearchText(item), filter)) {
                filtered.add(item);
            }
        }
        List<GHRepositoryListItem> items = myRepositoryListRenderer.withAccountNames(filtered);

        GHRepositoryListItem selection = myRepositoryList.getValue();
        myUpdatingList = true;
        try {
            myRepositoryListModel.replaceAll(items);

            int newIdx = selection == null ? -1 : items.indexOf(selection);
            myRepositoryList.setValue(newIdx >= 0 ? items.get(newIdx) : null, false);
        }
        finally {
            myUpdatingList = false;
        }
    }

    private static String getSearchText(GHRepositoryListItem item) {
        if (item instanceof GHRepositoryListItem.Repo repo) {
            return repo.getRepo().getFullName();
        }
        return "";
    }

    @RequiredUIAccess
    private void updateSelectedUrl() {
        myRepositoryList.setPlaceholder(LocalizeValue.empty());
        if (myInLoginState) {
            setSelectedUrl(null);
            return;
        }
        GHRepositoryCoordinates githubRepoPath = getGithubRepoPath(StringUtil.notNullize(mySearchField.getValue()));
        if (githubRepoPath != null) {
            String selectedUrl = myGithubGitHelper.getRemoteUrl(githubRepoPath.getServerPath(),
                githubRepoPath.getRepositoryPath().getOwner(),
                githubRepoPath.getRepositoryPath().getRepository());
            setSelectedUrl(selectedUrl);
            myRepositoryList.setPlaceholder(ReviewLocalize.cloneDialogRepositoryUrlText(selectedUrl));
            return;
        }
        GHRepositoryListItem selectedValue = myRepositoryList.getValue();
        if (selectedValue instanceof GHRepositoryListItem.Repo repo) {
            GithubServerPath server = GithubAccountType.toServerPath(myAccountManager.getServerUrl(repo.getAccount()));
            setSelectedUrl(myGithubGitHelper.getRemoteUrl(server, repo.getRepo().getUserName(), repo.getRepo().getName()));
            return;
        }
        setSelectedUrl(null);
    }

    private static @Nullable GHRepositoryCoordinates getGithubRepoPath(String searchText) {
        String url = StringUtil.trimEnd(StringUtil.trimStart(searchText.trim(), "git clone"), ".git").trim();

        try {
            GithubServerPath parsedPath = GithubServerPath.from(url);
            String suffix = parsedPath.getSuffix();
            GithubServerPath serverPath = GithubServerPath.from(StringUtil.trimEnd(parsedPath.toUrl(), suffix == null ? "" : suffix));

            GHRepositoryPath githubFullPath = GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
            if (githubFullPath == null) {
                return null;
            }
            return new GHRepositoryCoordinates(serverPath, githubFullPath);
        }
        catch (Exception e) {
            return null;
        }
    }

    @RequiredUIAccess
    private void setSelectedUrl(@Nullable String selectedUrl) {
        mySelectedUrl = selectedUrl;
        onSelectedUrlChanged();
    }

    @RequiredUIAccess
    private void onSelectedUrlChanged() {
        String selectedUrl = mySelectedUrl;
        if (selectedUrl != null) {
            String path = StringUtil.trimEnd(ClonePathProvider.relativeDirectoryPathForVcsUrl(myProject, selectedUrl), GitUtil.DOT_GIT);
            trySetChildPath(path);
        }
        validate();
    }

    @RequiredUIAccess
    private void trySetChildPath(String child) {
        if (myDirectoryModifiedByUser) {
            return;
        }
        try {
            Path newPath = myDefaultParentPath.resolve(child);
            myDirectoryField.setValue(newPath.toString(), false);
        }
        catch (InvalidPathException ignored) {
        }
    }

    @RequiredUIAccess
    private void validate() {
        String directory = myDirectoryField.getValue();
        LocalizeValue error = CloneDvcsValidationUtils.checkDirectory(directory);
        myDirectoryErrorLabel.setText(error);

        Context context = myContext;
        if (context != null) {
            context.setCheckoutEnabled(mySelectedUrl != null && !directory.isEmpty() && error.isEmpty());
        }
    }
}
