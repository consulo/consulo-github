/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.run;

import consulo.application.concurrent.ApplicationConcurrency;
import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.TextBox;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The workflow repository, file, ref and workflow_dispatch inputs of a {@link WorkflowRunConfiguration}.
 */
class WorkflowRunConfigurationFragment implements RunConfigurationFragment<WorkflowRunConfiguration> {
    private static final Logger LOG = Logger.getInstance(WorkflowRunConfigurationFragment.class);
    private static final WorkflowRun.DispatchInputs DISPATCH_INPUTS = new WorkflowRun.DispatchInputs();

    @Override
    public String getId() {
        return "githubWorkflow";
    }

    @Override
    public LocalizeValue getName() {
        return GitHubWorkflowLocalize.workflowRunFragmentName();
    }

    @Override
    public boolean isRemovable() {
        return false;
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<WorkflowRunConfiguration> createEditor(Project project, Disposable parent) {
        return new Editor(project);
    }

    private static class Editor implements RunConfigurationFragmentEditor<WorkflowRunConfiguration> {
        private final Project myProject;
        private final TextBox myServerUrl = TextBox.create();
        private final TextBox myOwner = TextBox.create();
        private final TextBox myRepo = TextBox.create();
        private final TextBox myWorkflowPath = TextBox.create();
        private final TextBox myRef = TextBox.create();
        private final VerticalLayout myInputsHolder = VerticalLayout.create();
        private final AtomicInteger myGeneration = new AtomicInteger();
        private final List<InputEditor> myInputEditors = new ArrayList<>();
        private Map<String, String> myValues = new LinkedHashMap<>();
        private @Nullable List<WorkflowRun.DispatchInputs.Input> myDefinitions;
        private String myLoadedPath = "";

        @RequiredUIAccess
        private Editor(Project project) {
            myProject = project;
            myWorkflowPath.addValueListener(event -> loadDefinitions(StringUtil.notNullize(myWorkflowPath.getValue()).trim()));
            rebuildInputs();
        }

        @RequiredUIAccess
        @Override
        public void addRows(RunConfigurationFragmentLayout layout) {
            layout.addLabeled(GitHubWorkflowLocalize.workflowRunFieldServer(), myServerUrl);
            layout.addLabeled(GitHubWorkflowLocalize.workflowRunFieldOwner(), myOwner);
            layout.addLabeled(GitHubWorkflowLocalize.workflowRunFieldRepo(), myRepo);
            layout.addLabeled(GitHubWorkflowLocalize.workflowRunFieldWorkflow(), myWorkflowPath);
            layout.addLabeled(GitHubWorkflowLocalize.workflowRunFieldRef(), myRef);
            layout.addBottom(LabeledLayout.create(GitHubWorkflowLocalize.workflowRunInputsTitle(), myInputsHolder));
        }

        @RequiredUIAccess
        @Override
        public void reset(WorkflowRunConfiguration target) {
            WorkflowRunConfigurationOptions options = target.getOptions();
            myServerUrl.setValue(options.getServerUrl(), false);
            myOwner.setValue(options.getOwner(), false);
            myRepo.setValue(options.getRepo(), false);
            myWorkflowPath.setValue(options.getWorkflowPath(), false);
            myRef.setValue(options.getRef(), false);
            myValues = new LinkedHashMap<>(options.getInputs());
            if (!options.getWorkflowPath().equals(myLoadedPath)) {
                myDefinitions = null;
            }
            rebuildInputs();
            loadDefinitions(options.getWorkflowPath());
        }

        @RequiredUIAccess
        @Override
        public void apply(WorkflowRunConfiguration target) {
            WorkflowRunConfigurationOptions options = target.getOptions();
            options.setServerUrl(myServerUrl.getValue());
            options.setOwner(myOwner.getValue());
            options.setRepo(myRepo.getValue());
            options.setWorkflowPath(myWorkflowPath.getValue());
            options.setRef(myRef.getValue());
            options.setInputs(currentInputs());
        }

        @RequiredUIAccess
        private Map<String, String> currentInputs() {
            Map<String, String> result = new LinkedHashMap<>(myValues);
            for (InputEditor editor : myInputEditors) {
                String value = editor.value().get();
                if (!Objects.equals(value, editor.initialValue())) {
                    result.put(editor.name(), value);
                }
            }
            return result;
        }

        @RequiredUIAccess
        private void loadDefinitions(String workflowPath) {
            if (workflowPath.equals(myLoadedPath) && myDefinitions != null) {
                return;
            }
            int generation = myGeneration.incrementAndGet();
            String basePath = myProject.getBasePath();
            UIAccess uiAccess = UIAccess.current();
            CompletableFuture.supplyAsync(() -> readDefinitions(basePath, workflowPath), myProject.getApplication().getInstance(ApplicationConcurrency.class).getExecutorService())
                .whenCompleteAsync((definitions, error) -> {
                    if (generation != myGeneration.get() || myProject.isDisposed()) {
                        return;
                    }
                    if (error != null) {
                        LOG.error(error);
                    }
                    myValues = currentInputs();
                    myLoadedPath = workflowPath;
                    myDefinitions = definitions == null ? List.of() : definitions;
                    rebuildInputs();
                }, uiAccess);
        }

        private static List<WorkflowRun.DispatchInputs.Input> readDefinitions(@Nullable String basePath, String workflowPath) {
            if (workflowPath.isBlank()) {
                return List.of();
            }
            try {
                Path path = Path.of(workflowPath);
                Path file = path.isAbsolute() || basePath == null ? path : Path.of(basePath).resolve(path);
                if (!Files.isRegularFile(file)) {
                    return List.of();
                }
                return DISPATCH_INPUTS.parse(Files.readString(file));
            }
            catch (IOException | RuntimeException e) {
                return List.of();
            }
        }

        @RequiredUIAccess
        private void rebuildInputs() {
            myInputEditors.clear();
            myInputsHolder.removeAll();

            List<WorkflowRun.DispatchInputs.Input> definitions = myDefinitions == null ? List.of() : myDefinitions;
            Set<String> defined = new LinkedHashSet<>();
            FormBuilder builder = FormBuilder.create();
            for (WorkflowRun.DispatchInputs.Input input : definitions) {
                defined.add(input.name());
                String value = myValues.getOrDefault(input.name(), input.defaultValue());
                InputEditor editor = createEditor(input, value);
                myInputEditors.add(editor);
                builder.addLabeled(LocalizeValue.of(input.name() + (input.required() ? " *" : "") + ":"), editor.component());
            }
            for (Map.Entry<String, String> entry : myValues.entrySet()) {
                if (defined.contains(entry.getKey())) {
                    continue;
                }
                TextBox textBox = TextBox.create(entry.getValue());
                myInputEditors.add(new InputEditor(entry.getKey(), entry.getValue(), textBox, () -> StringUtil.notNullize(textBox.getValue())));
                builder.addLabeled(LocalizeValue.of(entry.getKey() + ":"), textBox);
            }

            if (myInputEditors.isEmpty()) {
                myInputsHolder.add(Label.create(GitHubWorkflowLocalize.workflowRunInputsEmpty()));
            }
            else {
                myInputsHolder.add(builder.build());
            }
        }

        @RequiredUIAccess
        private static InputEditor createEditor(WorkflowRun.DispatchInputs.Input input, String value) {
            Component component;
            Supplier<String> reader;
            String initialValue = value;
            if ("boolean".equals(input.type())) {
                CheckBox checkBox = CheckBox.create(LocalizeValue.empty(), Boolean.parseBoolean(value));
                component = checkBox;
                reader = () -> String.valueOf(Boolean.TRUE.equals(checkBox.getValue()));
                initialValue = String.valueOf(Boolean.parseBoolean(value));
            }
            else if ("choice".equals(input.type()) && !input.options().isEmpty()) {
                List<String> options = new ArrayList<>(input.options());
                if (!value.isEmpty() && !options.contains(value)) {
                    options.add(value);
                }
                ComboBox<String> comboBox = ComboBox.create(options);
                comboBox.setValue(value.isEmpty() ? null : value, false);
                component = comboBox;
                reader = () -> StringUtil.notNullize(comboBox.getValue());
            }
            else {
                TextBox textBox = TextBox.create(value);
                component = textBox;
                reader = () -> StringUtil.notNullize(textBox.getValue());
            }
            if (!input.description().isBlank()) {
                component.setToolTipText(LocalizeValue.of(input.description()));
            }
            return new InputEditor(input.name(), initialValue, component, reader);
        }
    }

    private record InputEditor(String name, String initialValue, Component component, Supplier<String> value) {
    }
}
