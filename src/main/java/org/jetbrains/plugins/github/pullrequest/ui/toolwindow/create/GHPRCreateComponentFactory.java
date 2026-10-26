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
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create;

import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.review.create.CodeReviewCreateReviewLayoutBuilder;
import consulo.review.create.CodeReviewTitleDescriptionComponentFactory;
import consulo.review.details.CommitPresentation;
import consulo.review.icon.IconsProvider;
import consulo.review.list.error.ErrorStatusAlignment;
import consulo.review.list.error.ErrorStatusPanelFactory;
import consulo.review.list.error.ErrorStatusPresenter;
import consulo.review.list.search.ChooserPopupUtil;
import consulo.review.list.search.PopupConfig;
import consulo.review.list.search.ShowDirection;
import consulo.review.localize.ReviewLocalize;
import consulo.review.util.FutureUtil;
import consulo.review.util.IncrementallyComputedValue;
import consulo.review.util.ReviewUIUtil;
import consulo.review.util.popup.PopupItemPresentation;
import consulo.ui.Button;
import consulo.ui.ButtonStyle;
import consulo.ui.Component;
import consulo.ui.DialogCancelledException;
import consulo.ui.HorizontalAlignment;
import consulo.ui.HtmlLabel;
import consulo.ui.Hyperlink;
import consulo.ui.InputBoxBuilder;
import consulo.ui.Label;
import consulo.ui.OptionButton;
import consulo.ui.OptionButtonItem;
import consulo.ui.PopupPosition;
import consulo.ui.RelativePoint2D;
import consulo.ui.Space;
import consulo.ui.StaticPosition;
import consulo.ui.UIAccess;
import consulo.ui.VerticalAlignment;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.color.RGBColor;
import consulo.ui.event.ClickEvent;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.FlowLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.layout.SplitLayoutPosition;
import consulo.ui.layout.TableLayout;
import consulo.ui.layout.TwoComponentSplitLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.style.ComponentColors;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import consulo.versionControlSystem.log.VcsCommitMetadata;
import consulo.versionControlSystem.log.util.VcsUserUtil;
import consulo.versionControlSystem.review.changes.CodeReviewChangeListComponentFactory;
import consulo.versionControlSystem.review.details.CodeReviewDetailsCommitInfoComponentFactory;
import consulo.versionControlSystem.review.details.CodeReviewDetailsCommitsComponentFactory;
import consulo.versionControlSystem.review.details.model.CodeReviewChangeListViewModelBase;
import git4idea.GitBranch;
import git4idea.GitRemoteBranch;
import git4idea.ui.branch.MergeDirectionComponentFactory;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.data.GHLabel;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel.BranchesCheckResult;
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create.GHPRCreateViewModel.CreationState;
import org.jetbrains.plugins.github.ui.component.LabeledListPanelViewModel;
import org.jetbrains.plugins.github.ui.util.GHUIUtil;
import org.jetbrains.plugins.github.util.GHGitRepositoryMapping;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public final class GHPRCreateComponentFactory {
    private static final Space SIDE_GAPS_L = Space.LARGE;
    private static final Space SIDE_GAPS_M = Space.MEDIUM;
    private static final Space BUTTONS_GAP = Space.LARGE;
    private static final Space STATUS_GAP = Space.MEDIUM;
    private static final Space TEXT_LINK_GAP = Space.MEDIUM;

    private static final int AVATAR_SIZE = 20;

    private GHPRCreateComponentFactory() {
    }

    @RequiredUIAccess
    public static Component createIn(Disposable cs, GHPRCreateViewModel vm) {
        Application application = vm.getProject().getApplication();

        Component topPanel = CodeReviewCreateReviewLayoutBuilder.create()
            .addComponent(directionSelector(cs, vm))
            .addSeparator()
            .addComponent(changesPanel(cs, vm, application), true, true)
            .build();

        VerticalLayout statusAndActions = VerticalLayout.create(Space.LARGE);
        statusAndActions.add(statusPanel(cs, vm));
        statusAndActions.add(actionsPanel(cs, vm));

        Component bottomPanel = CodeReviewCreateReviewLayoutBuilder.create()
            .addComponent(textPanel(cs, vm, application), true, false)
            .addSeparator()
            .addComponent(metadataPanel(cs, vm))
            .addSeparator()
            .addComponent(statusAndActions)
            .build();

        UIAccess uiAccess = UIAccess.current();
        vm.setRemoteBranchNameCallback(suggestedName ->
            CompletableFuture.supplyAsync(() -> getRemoteBranchName(suggestedName), uiAccess).thenCompose(it -> it));
        Disposer.register(cs, () -> vm.setRemoteBranchNameCallback(null));

        return TwoComponentSplitLayout.create(SplitLayoutPosition.VERTICAL)
            .withProportion(50)
            .withFirstComponent(topPanel)
            .withSecondComponent(bottomPanel);
    }

    @RequiredUIAccess
    private static CompletableFuture<String> getRemoteBranchName(String suggestedName) {
        LocalizeValue text = LocalizeValue.join(GithubLocalize.pullRequestCreateInputRemoteBranchName(),
            LocalizeValue.of("\n"),
            GithubLocalize.pullRequestCreateInputRemoteBranchComment());
        return InputBoxBuilder.text()
            .title(GithubLocalize.pullRequestCreateInputRemoteBranchTitle())
            .text(text)
            .value(suggestedName)
            .showAsync()
            .handle((name, error) -> {
                if (error != null) {
                    Throwable cause = FutureUtil.unwrap(error);
                    if (cause instanceof DialogCancelledException) {
                        throw new CancellationException();
                    }
                    throw error instanceof CompletionException ce ? ce : new CompletionException(error);
                }
                if (name == null) {
                    throw new CancellationException();
                }
                return name;
            });
    }

    @RequiredUIAccess
    private static Component textPanel(Disposable cs, GHPRCreateViewModel vm, Application application) {
        CodeReviewTitleDescriptionComponentFactory factory = application.getInstance(CodeReviewTitleDescriptionComponentFactory.class);
        Component titleEditor = factory.createTitleEditorIn(cs, vm.getProject(), vm, GithubLocalize.pullRequestCreateTitle());
        Component descriptionEditor =
            factory.createDescriptionEditorIn(cs, vm.getProject(), vm, GithubLocalize.pullRequestCreateDescription());
        return factory.createIn(cs, vm, titleEditor, descriptionEditor);
    }

    @RequiredUIAccess
    private static Component directionSelector(Disposable cs, GHPRCreateViewModel vm) {
        GHPRCreateMergeDirectionModel directionModel = new GHPRCreateMergeDirectionModel(cs, vm);
        return new MergeDirectionComponentFactory<>(
            directionModel,
            model -> {
                GitRemoteBranch branch = model.getBaseBranch();
                if (branch == null) {
                    return null;
                }
                GHGitRepositoryMapping headRepo = model.getHeadRepo();
                GHRepositoryPath headRepoPath = headRepo == null ? null : headRepo.getRepository().getRepositoryPath();
                GHRepositoryPath baseRepoPath = model.getBaseRepo().getRepository().getRepositoryPath();
                boolean showOwner = headRepoPath != null && !baseRepoPath.equals(headRepoPath);
                return LocalizeValue.of(baseRepoPath.toString(showOwner) + ":" + branch.getNameForRemoteOperations());
            },
            model -> {
                GitBranch branch = model.getHeadBranch();
                if (branch == null) {
                    return null;
                }
                GHGitRepositoryMapping headRepo = model.getHeadRepo();
                if (headRepo == null) {
                    return null;
                }
                GHRepositoryPath headRepoPath = headRepo.getRepository().getRepositoryPath();
                GHRepositoryPath baseRepoPath = model.getBaseRepo().getRepository().getRepositoryPath();
                boolean showOwner = !baseRepoPath.equals(headRepoPath);
                return LocalizeValue.of(headRepoPath.toString(showOwner) + ":" + branch.getName());
            }
        ).create();
    }

    @RequiredUIAccess
    private static Component changesPanel(Disposable cs, GHPRCreateViewModel vm, Application application) {
        DockLayout wrapper = DockLayout.create(Space.NONE);
        ErrorStatusPanelFactory errorStatusPanelFactory = application.getInstance(ErrorStatusPanelFactory.class);
        ErrorStatusPresenter<Throwable> errorStatusPresenter =
            ErrorStatusPresenter.simpleHTML(GithubLocalize.pullRequestCreateFailedToLoadCommits(), null, error -> null);
        bindScoped(cs, vm.getChangesVm(), (scope, changesResult) -> {
            Component content;
            if (changesResult == null) {
                content = moveToCenter(HtmlLabel.create(GithubLocalize.pullRequestCreateSelectBranches()));
            }
            else if (changesResult.isInProgress()) {
                content = moveToCenter(loadingLabel(ReviewLocalize.reviewPopupDataLoading()));
            }
            else {
                Throwable error = changesResult.exceptionOrNull();
                if (error != null) {
                    content = moveToCenter(errorStatusPanelFactory.create(error, errorStatusPresenter));
                }
                else {
                    content = createChangesPanel(scope, changesResult.getOrNull(), application);
                }
            }
            wrapper.center(content);
        });
        return wrapper;
    }

    @RequiredUIAccess
    private static Component createChangesPanel(Disposable cs, @Nullable GHPRCreateChangesViewModel changesVm, Application application) {
        if (changesVm == null) {
            return moveToCenter(hintPane(GithubLocalize.pullRequestDoesNotContainCommits()));
        }

        Component commits = application.getInstance(CodeReviewDetailsCommitsComponentFactory.class)
            .create(cs, changesVm, GHPRCreateComponentFactory::createCommitsPopupPresenter);
        Component commitsDetails = application.getInstance(CodeReviewDetailsCommitInfoComponentFactory.class)
            .create(cs, changesVm.getSelectedCommit(), GHPRCreateComponentFactory::createCommitsPopupPresenter);

        VerticalLayout commitsPanel = VerticalLayout.create(SIDE_GAPS_M);
        commitsPanel.add(commits);
        commitsPanel.add(commitsDetails);
        commitsPanel.paddingBuilder().verticalSet(SIDE_GAPS_M).horizontalSet(SIDE_GAPS_L).apply();

        DockLayout panel = DockLayout.create(Space.NONE);
        panel.top(commitsPanel);

        ErrorStatusPanelFactory errorStatusPanelFactory = application.getInstance(ErrorStatusPanelFactory.class);
        ErrorStatusPresenter<Throwable> errorStatusPresenter =
            ErrorStatusPresenter.simpleHTML(GithubLocalize.pullRequestCreateFailedToLoadChanges(), null, error -> null);
        CodeReviewChangeListComponentFactory changeListComponentFactory =
            application.getInstance(CodeReviewChangeListComponentFactory.class);
        bindScoped(cs, changesVm.getCommitChangesVm(), (commitScope, commitChangesVm) ->
            bindScoped(commitScope, commitChangesVm.getChangeListVm(), (changeListScope, changeListVmResult) -> {
                Component content;
                if (changeListVmResult.isInProgress()) {
                    content = moveToCenter(loadingLabel(ReviewLocalize.reviewPopupDataLoading()));
                }
                else {
                    Throwable error = changeListVmResult.exceptionOrNull();
                    CodeReviewChangeListViewModelBase changeListVm = changeListVmResult.getOrNull();
                    if (error != null || changeListVm == null) {
                        content = moveToCenter(errorStatusPanelFactory.create(
                            error != null ? error : new IllegalStateException(), errorStatusPresenter));
                    }
                    else {
                        content = changeListComponentFactory.createIn(changeListScope, changeListVm, null,
                            GithubLocalize.pullRequestCommitDoesNotContainChanges());
                    }
                }
                panel.center(content);
            }));
        return panel;
    }

    @RequiredUIAccess
    private static Component metadataPanel(Disposable cs, GHPRCreateViewModel vm) {
        IconsProvider<String> avatarIconsProvider = vm.getAvatarIconsProvider();

        TableLayout grid = TableLayout.create(StaticPosition.TOP);
        int row = 0;
        addListRow(grid, row++, createReviewersListPanelHandle(cs, vm.getReviewersVm(), avatarIconsProvider));
        addListRow(grid, row++, createAssigneesListPanelHandle(cs, vm.getAssigneesVm(), avatarIconsProvider));
        addListRow(grid, row, createLabelsListPanelHandle(cs, vm.getLabelsVm()));
        grid.paddingBuilder().verticalSet(SIDE_GAPS_M).horizontalSet(SIDE_GAPS_L).apply();
        return grid;
    }

    @RequiredUIAccess
    private static void addListRow(TableLayout grid, int row, Component[] labelAndList) {
        grid.add(labelAndList[0], TableLayout.cell(row, 0));
        grid.add(labelAndList[1], TableLayout.cell(row, 1).fill());
    }

    @RequiredUIAccess
    private static Component statusPanel(Disposable cs, GHPRCreateViewModel vm) {
        VerticalLayout panel = VerticalLayout.create(STATUS_GAP);
        HorizontalLayout branchesCheckState = HorizontalLayout.create(TEXT_LINK_GAP);
        HorizontalLayout progressState = HorizontalLayout.create(TEXT_LINK_GAP);
        panel.add(branchesCheckState);
        panel.add(progressState);
        Runnable updateVisibility = () -> panel.setVisible(branchesCheckState.isVisible() || progressState.isVisible());
        createBranchesCheckState(cs, vm, branchesCheckState, updateVisibility);
        createProgressState(cs, vm, progressState, updateVisibility);
        return panel;
    }

    @RequiredUIAccess
    private static void createBranchesCheckState(Disposable cs,
                                                 GHPRCreateViewModel vm,
                                                 HorizontalLayout panel,
                                                 Runnable updateVisibility) {
        ReviewUIUtil.bind(cs, vm.getBranchesCheckState(), computedResult -> {
            panel.removeAll();
            if (computedResult == null) {
                panel.setVisible(false);
            }
            else if (computedResult.isInProgress()) {
                panel.add(loadingLabel(GithubLocalize.pullRequestCreateCheckingBranches()));
                panel.setVisible(true);
            }
            else {
                Throwable error = computedResult.exceptionOrNull();
                BranchesCheckResult result = computedResult.getOrNull();
                if (error != null) {
                    panel.add(errorLabel(GithubLocalize.pullRequestCreateFailedToCheckBranches()));
                    panel.add(errorLink(vm, error));
                    panel.add(refreshIconLabelButton(vm));
                    panel.setVisible(true);
                }
                else if (result instanceof BranchesCheckResult.AlreadyExists alreadyExists) {
                    panel.add(errorLabel(GithubLocalize.pullRequestCreateAlreadyExists()));
                    panel.add(Hyperlink.create(GithubLocalize.pullRequestCreateAlreadyExistsView(), event -> alreadyExists.open().run()));
                    panel.setVisible(true);
                }
                else if (result instanceof BranchesCheckResult.NoChanges noChanges) {
                    panel.add(errorLabel(GithubLocalize.pullRequestCreateNoChanges(noChanges.baseBranch().getNameForRemoteOperations(),
                        noChanges.headBranch().getName())));
                    panel.setVisible(true);
                }
                else {
                    panel.setVisible(false);
                }
            }
            updateVisibility.run();
        });
    }

    @RequiredUIAccess
    private static void createProgressState(Disposable cs, GHPRCreateViewModel vm, HorizontalLayout panel, Runnable updateVisibility) {
        ReviewUIUtil.bind(cs, vm.getCreationProgress(), state -> {
            panel.removeAll();
            if (state == null || state instanceof CreationState.CollectingData || state instanceof CreationState.Created) {
                panel.setVisible(false);
                updateVisibility.run();
                return;
            }
            panel.setVisible(true);
            switch (state) {
                case CreationState.Pushing pushing -> panel.add(loadingLabel(GithubLocalize.pullRequestCreatePushing()));
                case CreationState.CallingAPI callingAPI -> panel.add(loadingLabel(GithubLocalize.pullRequestCreateCreating()));
                case CreationState.SettingMetadata settingMetadata ->
                    panel.add(loadingLabel(GithubLocalize.pullRequestCreateSettingMetadata()));
                case CreationState.Error error -> {
                    panel.add(errorLabel(GithubLocalize.pullRequestCreateError()));
                    panel.add(errorLink(vm, error.error()));
                }
                default -> {
                }
            }
            updateVisibility.run();
        });
    }

    @RequiredUIAccess
    private static Component actionsPanel(Disposable cs, GHPRCreateViewModel vm) {
        OptionButton createButton = OptionButton.create(GithubLocalize.pullRequestCreateAction(), event -> vm.create(false));
        createButton.addStyle(ButtonStyle.PRIMARY);
        createButton.setEnabled(false);

        ReviewUIUtil.bind(cs, vm.getBranchesCheckState(), vm.getCreationProgress(), (check, progress) -> {
            boolean enabled = check != null
                && check.getOrNull() instanceof BranchesCheckResult.OK
                && (progress == null || progress instanceof CreationState.Error);
            createButton.setEnabled(enabled);
            createButton.setOptions(List.of(
                new OptionButtonItem(GithubLocalize.pullRequestCreateDraftAction(), null, enabled, event -> vm.create(true))));
        });

        return HorizontalLayout.create(BUTTONS_GAP).add(createButton);
    }

    private static CommitPresentation createCommitsPopupPresenter(VcsCommitMetadata commit) {
        String[] messageParts = commit.getFullMessage().split("\n\n");
        return new CommitPresentation(
            commit.getSubject(),
            messageParts.length > 1 ? messageParts[1] : "",
            VcsUserUtil.getShortPresentation(commit.getAuthor()),
            new Date(commit.getAuthorTime())
        );
    }

    @RequiredUIAccess
    private static Component[] createReviewersListPanelHandle(Disposable cs,
                                                              LabeledListPanelViewModel<GHPullRequestRequestedReviewer> vm,
                                                              IconsProvider<String> avatarIconsProvider) {
        Component label = createLabelPanel(cs, vm.getItems(),
            GithubLocalize.pullRequestNoReviewers(),
            GithubLocalize.pullRequestReviewers());

        Component list = createListPanel(cs, vm,
            GHUIUtil.SelectionPresenters.PRReviewers(avatarIconsProvider),
            it -> userLabel(it, avatarIconsProvider));

        return new Component[]{label, list};
    }

    @RequiredUIAccess
    private static Component[] createAssigneesListPanelHandle(Disposable cs,
                                                              LabeledListPanelViewModel<GHUser> vm,
                                                              IconsProvider<String> avatarIconsProvider) {
        Component label = createLabelPanel(cs, vm.getItems(),
            GithubLocalize.pullRequestUnassigned(),
            GithubLocalize.pullRequestAssignees());

        Component list = createListPanel(cs, vm,
            GHUIUtil.SelectionPresenters.Users(avatarIconsProvider),
            it -> userLabel(it, avatarIconsProvider));

        return new Component[]{label, list};
    }

    @RequiredUIAccess
    private static Component[] createLabelsListPanelHandle(Disposable cs, LabeledListPanelViewModel<GHLabel> vm) {
        Component label = createLabelPanel(cs, vm.getItems(),
            GithubLocalize.pullRequestNoLabels(),
            GithubLocalize.pullRequestLabels());

        Component list = createListPanel(cs, vm,
            GHUIUtil.SelectionPresenters.Labels(),
            GHPRCreateComponentFactory::labelLabel);

        return new Component[]{label, list};
    }

    @RequiredUIAccess
    private static <T> Component createLabelPanel(Disposable cs,
                                                  ObservableValue<List<T>> items,
                                                  LocalizeValue emptyText,
                                                  LocalizeValue notEmptyText) {
        Label label = Label.create();
        label.setForegroundColor(ComponentColors.DISABLED_TEXT);
        label.paddingBuilder().verticalSet(Space.SMALL).rightSet(Space.SMALL).apply();
        ReviewUIUtil.bind(cs, items, list -> label.setText(list.isEmpty() ? emptyText : notEmptyText));
        return label;
    }

    @RequiredUIAccess
    private static <T> Component createListPanel(Disposable cs,
                                                 LabeledListPanelViewModel<T> vm,
                                                 Function<T, PopupItemPresentation> getItemPresentation,
                                                 @RequiredUIAccess Function<T, Component> itemComponentFactory) {
        Button editButton = Button.create(LocalizeValue.empty(), event -> editList(event, vm, getItemPresentation));
        editButton.setIcon(PlatformIconGroup.generalInline_edit());
        editButton.addStyle(ButtonStyle.INPLACE);

        FlowLayout panel = FlowLayout.create(Space.NONE);
        ReviewUIUtil.bind(cs, vm.getItems(), newList -> {
            panel.removeAll();
            for (T item : newList) {
                panel.add(itemComponentFactory.apply(item));
            }
            panel.add(editButton);
        });
        return panel;
    }

    @RequiredUIAccess
    private static Component userLabel(GHPullRequestRequestedReviewer user, IconsProvider<String> avatarIconsProvider) {
        Label label = Label.create(LocalizeValue.of(user.getShortName()));
        label.setImage(avatarIconsProvider.getIcon(user.getAvatarUrl(), AVATAR_SIZE));
        label.paddingBuilder().verticalSet(Space.SMALL).horizontalSet(Space.X_SMALL).apply();
        return label;
    }

    @RequiredUIAccess
    private static Component labelLabel(GHLabel label) {
        Label component = Label.create(LocalizeValue.of(label.getName()));
        component.setImage(ImageEffects.colorFilled(Image.DEFAULT_ICON_SIZE, Image.DEFAULT_ICON_SIZE,
            RGBColor.decode("#" + label.getColor())));
        component.paddingBuilder().verticalSet(Space.SMALL).horizontalSet(Space.X_SMALL).apply();
        return component;
    }

    @RequiredUIAccess
    private static Component errorLabel(LocalizeValue text) {
        Label label = Label.create(text);
        label.setImage(PlatformIconGroup.ideFatalerror());
        return label;
    }

    @RequiredUIAccess
    private static <T> void editList(ClickEvent event,
                                     LabeledListPanelViewModel<T> vm,
                                     Function<T, PopupItemPresentation> getItemPresentation) {
        List<T> current = vm.getItems().get();
        MutableObservableValue<IncrementallyComputedValue<List<T>>> selectableItems =
            MutableObservableValue.of(mergeSelectableItems(current, vm.getSelectableItems().get()));
        Runnable unsubscribe = vm.getSelectableItems().addListener(state -> selectableItems.set(mergeSelectableItems(current, state)));

        ChooserPopupUtil.showMultipleChooserPopupWithIncrementalLoading(
            RelativePoint2D.of(event),
            current,
            selectableItems,
            getItemPresentation,
            PopupConfig.DEFAULT.withShowDirection(ShowDirection.ABOVE)
        ).whenComplete((newList, error) -> {
            unsubscribe.run();
            if (newList != null) {
                vm.adjustList(newList);
            }
        });
    }

    private static <T> IncrementallyComputedValue<List<T>> mergeSelectableItems(List<T> current,
                                                                               IncrementallyComputedValue<List<T>> state) {
        List<T> loaded = state.getValueOrNull();
        if (loaded == null) {
            return state;
        }
        List<T> merged = new ArrayList<>(current);
        for (T item : loaded) {
            if (!current.contains(item)) {
                merged.add(item);
            }
        }
        Exception exception = state.getExceptionOrNull();

        if (exception != null) {
            return IncrementallyComputedValue.partialFailure(merged, exception);
        }
        else if (state.isComplete()) {
            return IncrementallyComputedValue.success(merged);
        }
        else {
            return IncrementallyComputedValue.partialSuccess(merged);
        }
    }

    @RequiredUIAccess
    private static Component errorLink(GHPRCreateViewModel vm, Throwable error) {
        Hyperlink link = Hyperlink.create(GithubLocalize.pullRequestCreateErrorDetails());
        link.addHyperlinkListener(event -> {
            ErrorStatusPresenter<Throwable> errorStatusPresenter =
                ErrorStatusPresenter.simpleHTML(GithubLocalize.pullRequestCreateError(), null, it -> null);
            Component errorTextPane = vm.getProject().getApplication().getInstance(ErrorStatusPanelFactory.class)
                .create(error, errorStatusPresenter, ErrorStatusAlignment.LEFT);
            Component scrollPane = ScrollableLayout.create(errorTextPane);
            JBPopupFactory.getInstance().createComponentPopupBuilder(scrollPane, null)
                .setResizable(true)
                .setCancelOnClickOutside(true)
                .createPopup()
                .showBy(link, null, PopupPosition.TOP);
        });
        return link;
    }

    @RequiredUIAccess
    private static Component refreshIconLabelButton(GHPRCreateViewModel vm) {
        Button button = Button.create(LocalizeValue.empty(), event -> vm.refreshBranchesState());
        button.setIcon(PlatformIconGroup.actionsRefresh());
        button.addStyle(ButtonStyle.INPLACE);
        button.setToolTipText(GithubLocalize.pullRequestCreateErrorRefresh());
        return button;
    }

    @RequiredUIAccess
    private static Component hintPane(LocalizeValue message) {
        HtmlLabel label = HtmlLabel.create(message);
        label.setForegroundColor(ComponentColors.DISABLED_TEXT);
        return label;
    }

    @RequiredUIAccess
    private static Component loadingLabel(LocalizeValue text) {
        Label label = Label.create(text);
        label.setImage(Image.busy());
        return label;
    }

    @RequiredUIAccess
    private static Component moveToCenter(Component component) {
        VerticalLayout layout = VerticalLayout.create(Space.NONE, HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        layout.add(component);
        return layout;
    }

    @RequiredUIAccess
    private static <T extends @Nullable Object> void bindScoped(Disposable parent,
                                                                ObservableValue<T> value,
                                                                @RequiredUIAccess BiConsumer<Disposable, T> consumer) {
        Disposable[] current = new Disposable[1];
        Consumer<T> handler = newValue -> {
            Disposable previous = current[0];
            current[0] = null;
            if (previous != null) {
                Disposer.dispose(previous);
            }
            Disposable scope = Disposable.newDisposable("GHPRCreateComponentFactory.bindScoped");
            if (!Disposer.tryRegister(parent, scope)) {
                return;
            }
            current[0] = scope;
            consumer.accept(scope, newValue);
        };
        ReviewUIUtil.bind(parent, value, handler::accept);
    }
}
