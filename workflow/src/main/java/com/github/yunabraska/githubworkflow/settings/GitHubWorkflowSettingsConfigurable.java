/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.settings;

import org.jetbrains.plugins.github.ui.GithubSettingsConfigurable;
import com.github.yunabraska.githubworkflow.state.GitHubActionCache;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.Configurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserFactory;
import consulo.fileChooser.FileSaverDescriptor;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.Button;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.MessageBoxes;
import consulo.ui.Table;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.ScrollableLayout;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@ExtensionImpl
public class GitHubWorkflowSettingsConfigurable extends SimpleConfigurableByProperties implements ApplicationConfigurable, Configurable.NoScroll {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault());

    private final GitHubActionCache myCache;

    @Inject
    public GitHubWorkflowSettingsConfigurable(GitHubActionCache cache) {
        myCache = cache;
    }

    @Override
    public String getId() {
        return "github.workflow.settings";
    }

    @Override
    public @Nullable String getParentId() {
        return GithubSettingsConfigurable.ID;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return GitHubWorkflowLocalize.settingsDisplayname();
    }

    @RequiredUIAccess
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
        MutableFlatDataModel<GitHubActionCache.CacheEntry> model = FlatDataModel.of(List.of());
        Table<GitHubActionCache.CacheEntry> table = Table.create(model);
        table.setAllowMultipleSelect(true);
        table.addColumn(GitHubWorkflowLocalize.settingsCacheColumnKey(), GitHubActionCache.CacheEntry::key);
        table.addColumn(GitHubWorkflowLocalize.settingsCacheColumnName(), GitHubActionCache.CacheEntry::name);
        table.addColumn(GitHubWorkflowLocalize.settingsCacheColumnKind(), entry -> (entry.local()
            ? GitHubWorkflowLocalize.settingsCacheKindLocal()
            : GitHubWorkflowLocalize.settingsCacheKindRemote()).get());
        table.addColumn(GitHubWorkflowLocalize.settingsCacheColumnState(), entry -> stateText(entry).get());
        table.addColumn(GitHubWorkflowLocalize.settingsCacheColumnExpires(), entry -> entry.expiryTime() <= 0 ? "" : DATE_TIME.format(Instant.ofEpochMilli(entry.expiryTime())));

        Label summary = Label.create();
        Runnable reload = () -> reloadTable(model, summary);

        HorizontalLayout buttons = HorizontalLayout.create();
        buttons.add(Button.create(GitHubWorkflowLocalize.settingsCacheRefresh(), event -> reload.run()));
        buttons.add(Button.create(GitHubWorkflowLocalize.settingsCacheDeleteselected(), event -> deleteSelected(table, reload)));
        buttons.add(Button.create(GitHubWorkflowLocalize.settingsCacheDeleteall(), event -> deleteAll(table, reload)));
        buttons.add(Button.create(GitHubWorkflowLocalize.settingsCacheExport(), event -> exportCache(table)));
        buttons.add(Button.create(GitHubWorkflowLocalize.settingsCacheImport(), event -> importCache(table, reload)));

        DockLayout cacheContent = DockLayout.create();
        cacheContent.top(summary);
        cacheContent.center(ScrollableLayout.create(table));
        cacheContent.bottom(buttons);

        DockLayout root = DockLayout.create();
        root.center(LabeledLayout.create(GitHubWorkflowLocalize.settingsCacheTitle(), cacheContent));

        reload.run();
        return root;
    }

    @RequiredUIAccess
    private void reloadTable(MutableFlatDataModel<GitHubActionCache.CacheEntry> model, Label summary) {
        model.replaceAll(myCache.entries());
        GitHubActionCache.CacheSummary cacheSummary = myCache.summary();
        long cacheKb = Math.max(0, (myCache.estimatedSizeBytes() + 1023) / 1024);
        summary.setText(GitHubWorkflowLocalize.settingsCacheSummary(
            cacheSummary.total(),
            cacheSummary.resolved(),
            cacheSummary.remote(),
            cacheSummary.expired(),
            cacheSummary.suppressed(),
            cacheKb
        ));
    }

    private static LocalizeValue stateText(GitHubActionCache.CacheEntry entry) {
        if (entry.suppressed()) {
            return GitHubWorkflowLocalize.settingsCacheStateSuppressed();
        }
        if (entry.expired()) {
            return GitHubWorkflowLocalize.settingsCacheStateExpired();
        }
        return entry.resolved()
            ? GitHubWorkflowLocalize.settingsCacheStateResolved()
            : GitHubWorkflowLocalize.settingsCacheStatePending();
    }

    @RequiredUIAccess
    private void deleteSelected(Table<GitHubActionCache.CacheEntry> table, Runnable reload) {
        List<String> keys = table.getSelectedItems().stream().map(GitHubActionCache.CacheEntry::key).toList();
        if (keys.isEmpty()) {
            showInfo(table, GitHubWorkflowLocalize.settingsCacheNoneselected());
            return;
        }
        myCache.removeAll(keys);
        reload.run();
        showInfo(table, GitHubWorkflowLocalize.settingsCacheDeleteselectedDone(keys.size()));
    }

    @RequiredUIAccess
    private void deleteAll(Table<GitHubActionCache.CacheEntry> table, Runnable reload) {
        UIAccess uiAccess = UIAccess.current();
        MessageBoxes.yesNo()
            .title(getDisplayName())
            .text(GitHubWorkflowLocalize.settingsCacheDeleteallConfirm())
            .showAsync(table)
            .whenCompleteAsync((confirmed, error) -> {
                if (!Boolean.TRUE.equals(confirmed)) {
                    return;
                }
                myCache.clear();
                reload.run();
                showInfo(table, GitHubWorkflowLocalize.settingsCacheDeleteallDone());
            }, uiAccess);
    }

    @RequiredUIAccess
    private void exportCache(Table<GitHubActionCache.CacheEntry> table) {
        UIAccess uiAccess = UIAccess.current();
        FileSaverDescriptor descriptor = new FileSaverDescriptor(GitHubWorkflowLocalize.settingsCacheExport().get(), "", "txt");
        FileChooserFactory.getInstance().newSaverBuilder(descriptor)
            .fileName("github-workflow-cache.txt")
            .showAsync()
            .thenApplyAsync(this::exportTo, AppExecutorUtil.getAppExecutorService())
            .whenCompleteAsync((exported, error) -> {
                if (error != null) {
                    showError(table, error);
                }
                else if (exported != null) {
                    showInfo(table, GitHubWorkflowLocalize.settingsCacheExportDone(exported.total()));
                }
            }, uiAccess);
    }

    @RequiredUIAccess
    private void importCache(Table<GitHubActionCache.CacheEntry> table, Runnable reload) {
        UIAccess uiAccess = UIAccess.current();
        FileChooserFactory.getInstance().newBuilder(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
            .showSingle()
            .thenApplyAsync(this::importFrom, AppExecutorUtil.getAppExecutorService())
            .whenCompleteAsync((imported, error) -> {
                if (error != null) {
                    showError(table, error);
                }
                else if (imported != null) {
                    reload.run();
                    showInfo(table, GitHubWorkflowLocalize.settingsCacheImportDone());
                }
            }, uiAccess);
    }

    private GitHubActionCache.@Nullable CacheSummary exportTo(@Nullable Path path) {
        if (path == null) {
            return null;
        }
        try {
            return myCache.exportCache(path);
        }
        catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private GitHubActionCache.@Nullable CacheSummary importFrom(@Nullable Path path) {
        if (path == null) {
            return null;
        }
        try {
            return myCache.importCache(path);
        }
        catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @RequiredUIAccess
    private CompletableFuture<?> showInfo(Component component, LocalizeValue text) {
        return MessageBoxes.okInfo(text).title(getDisplayName()).showAsync(component);
    }

    @RequiredUIAccess
    private void showError(Component component, Throwable error) {
        Throwable reason = error;
        while (reason.getCause() != null && (reason instanceof CompletionException || reason instanceof IllegalStateException)) {
            reason = reason.getCause();
        }
        if (reason instanceof CancellationException) {
            return;
        }
        MessageBoxes.okError(LocalizeValue.of(String.valueOf(reason.getMessage()))).title(getDisplayName()).showAsync(component);
    }
}
