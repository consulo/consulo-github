// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow;

import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.JBLabel;
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

/**
 * Main content for the Pull Requests tool window.
 * Shows login prompt or PR list depending on account state.
 */
public class GHPRToolWindowContent {
    private static final Logger LOG = Logger.getInstance(GHPRToolWindowContent.class);

    private final Project myProject;

    public GHPRToolWindowContent(Project project) {
        myProject = project;
    }

    public JComponent createComponent() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBorder(JBUI.Borders.empty(8));

        GHAccountManager accountManager = myProject.getApplication().getInstance(GHAccountManager.class);
        Set<GithubAccount> accounts = accountManager.getAccounts();

        if (accounts.isEmpty()) {
            mainPanel.add(createNoAccountPanel(), BorderLayout.CENTER);
        }
        else {
            mainPanel.add(createPRListPanel(accounts), BorderLayout.CENTER);
        }

        // Listen for account changes
        accountManager.getAccountsState().addListener(newAccounts -> {
            SwingUtilities.invokeLater(() -> {
                mainPanel.removeAll();
                if (newAccounts == null || newAccounts.isEmpty()) {
                    mainPanel.add(createNoAccountPanel(), BorderLayout.CENTER);
                }
                else {
                    mainPanel.add(createPRListPanel(newAccounts), BorderLayout.CENTER);
                }
                mainPanel.revalidate();
                mainPanel.repaint();
            });
        });

        return mainPanel;
    }

    private JComponent createNoAccountPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JBLabel label = new JBLabel("No GitHub accounts configured. Add an account to view pull requests.");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private JComponent createPRListPanel(Set<GithubAccount> accounts) {
        GHPRListPanel listPanel = new GHPRListPanel(myProject, accounts);
        return listPanel.getComponent();
    }
}
