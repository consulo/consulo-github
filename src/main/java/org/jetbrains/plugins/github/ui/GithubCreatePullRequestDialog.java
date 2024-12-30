/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.ValidationInfo;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.github.util.GithubSettings;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.swing.*;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestDialog extends DialogWrapper {
    private final GithubCreatePullRequestPanel myGithubCreatePullRequestPanel;
    private static final Pattern GITHUB_REPO_PATTERN = Pattern.compile("[a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+");

    public GithubCreatePullRequestDialog(
        @Nonnull final Project project,
        @Nonnull Collection<String> branches,
        @Nullable String suggestedBranch,
        @Nonnull Consumer<String> showDiff
    ) {
        super(project, true);
        myGithubCreatePullRequestPanel = new GithubCreatePullRequestPanel(showDiff);

        myGithubCreatePullRequestPanel.setBranches(branches);

        String configBranch = GithubSettings.getInstance().getCreatePullRequestDefaultBranch();
        myGithubCreatePullRequestPanel.setSelectedBranch(configBranch != null ? configBranch : suggestedBranch);

        setTitle("Create Pull Request");
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return myGithubCreatePullRequestPanel.getPanel();
    }

    @Nullable
    @Override
    @RequiredUIAccess
    public JComponent getPreferredFocusedComponent() {
        return myGithubCreatePullRequestPanel.getPreferredComponent();
    }

    @Override
    protected String getHelpId() {
        return "github.create.pull.request.dialog";
    }

    @Override
    protected String getDimensionServiceKey() {
        return "Github.CreatePullRequestDialog";
    }

    @Nonnull
    public String getRequestTitle() {
        return myGithubCreatePullRequestPanel.getTitle();
    }

    @Nonnull
    public String getDescription() {
        return myGithubCreatePullRequestPanel.getDescription();
    }

    @Nonnull
    public String getTargetBranch() {
        return myGithubCreatePullRequestPanel.getBranch();
    }

    @Override
    protected void doOKAction() {
        super.doOKAction();
        GithubSettings.getInstance().setCreatePullRequestDefaultBranch(getTargetBranch());
    }

    @Nullable
    @Override
    @RequiredUIAccess
    protected ValidationInfo doValidate() {
        if (StringUtil.isEmptyOrSpaces(getRequestTitle())) {
            return new ValidationInfo("Title can't be empty'", myGithubCreatePullRequestPanel.getTitleTextField());
        }

        if (!GITHUB_REPO_PATTERN.matcher(getTargetBranch()).matches()) {
            return new ValidationInfo(
                "Branch must be specified like 'username:branch'",
                myGithubCreatePullRequestPanel.getBranchEditor()
            );
        }

        return null;
    }

    @TestOnly
    public void setRequestTitle(String title) {
        myGithubCreatePullRequestPanel.setTitle(title);
    }

    @TestOnly
    public void setBranch(String branch) {
        myGithubCreatePullRequestPanel.setSelectedBranch(branch);
    }
}
