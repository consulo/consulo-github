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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBList;
import consulo.ui.ex.awt.JBUI;
import consulo.ui.ex.awt.JBScrollPane;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState;
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jspecify.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Panel showing a list of pull requests for the current repository.
 */
public class GHPRListPanel {
    private static final Logger LOG = Logger.getInstance(GHPRListPanel.class);

    private final Project myProject;
    private final Set<GithubAccount> myAccounts;
    private final DefaultListModel<PRListItem> myListModel;
    private final JBList<PRListItem> myList;
    private final JPanel myMainPanel;

    public GHPRListPanel(Project project, Set<GithubAccount> accounts) {
        myProject = project;
        myAccounts = accounts;
        myListModel = new DefaultListModel<>();
        myList = new JBList<>(myListModel);
        myList.setCellRenderer(new PRListCellRenderer());
        myList.setEmptyText("No pull requests");

        myMainPanel = new JPanel(new BorderLayout());

        // Header with refresh button
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(JBUI.Borders.emptyBottom(4));
        JBLabel titleLabel = new JBLabel("Pull Requests");
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> loadPullRequests());
        headerPanel.add(refreshButton, BorderLayout.EAST);

        myMainPanel.add(headerPanel, BorderLayout.NORTH);
        myMainPanel.add(new JBScrollPane(myList), BorderLayout.CENTER);

        // Load PRs on creation
        loadPullRequests();
    }

    public JComponent getComponent() {
        return myMainPanel;
    }

    private void loadPullRequests() {
        myListModel.clear();
        myList.setEmptyText("Loading...");

        CompletableFuture.runAsync(() -> {
            GithubAccount account = myAccounts.iterator().next();
            GHAccountManager accountManager = myProject.getApplication().getInstance(GHAccountManager.class);
            String token = accountManager.findCredentials(account);
            if (token == null) {
                SwingUtilities.invokeLater(() -> myList.setEmptyText("No credentials for account: " + account.getName()));
                return;
            }

            GithubServerPath serverPath = account.getServer();
            // TODO: detect repository from git remotes
            // For now use a placeholder — this needs to be connected to the actual git remote
            String apiUrl = serverPath.toApiUrl() + "/repos/{owner}/{repo}/pulls";

            try {
                GithubApiRequestExecutor executor = GithubApiRequestExecutor.create(serverPath, token);
                // TODO: Replace with actual repository detection
                // For now, show a message that repo detection is needed
                SwingUtilities.invokeLater(() -> myList.setEmptyText("Configure repository to view pull requests"));
            }
            catch (Exception e) {
                LOG.warn("Failed to load pull requests", e);
                SwingUtilities.invokeLater(() -> myList.setEmptyText("Error: " + e.getMessage()));
            }
        });
    }

    /**
     * Load PRs from a specific repository.
     */
    public void loadPullRequests(String owner, String repo) {
        myListModel.clear();
        myList.setEmptyText("Loading...");

        CompletableFuture.runAsync(() -> {
            GithubAccount account = myAccounts.iterator().next();
            GHAccountManager accountManager = myProject.getApplication().getInstance(GHAccountManager.class);
            String token = accountManager.findCredentials(account);
            if (token == null) {
                SwingUtilities.invokeLater(() -> myList.setEmptyText("No credentials"));
                return;
            }

            try {
                GithubApiRequestExecutor executor = GithubApiRequestExecutor.create(account.getServer(), token);
                String url = account.getServer().toApiUrl() + "/repos/" + owner + "/" + repo + "/pulls?state=open&per_page=30";
                JsonElement response = executor.executeGet(url);

                SwingUtilities.invokeLater(() -> {
                    if (response.isJsonArray()) {
                        JsonArray prs = response.getAsJsonArray();
                        for (JsonElement prElement : prs) {
                            JsonObject pr = prElement.getAsJsonObject();
                            PRListItem item = parsePullRequest(pr);
                            if (item != null) {
                                myListModel.addElement(item);
                            }
                        }
                        if (myListModel.isEmpty()) {
                            myList.setEmptyText("No open pull requests");
                        }
                    }
                });
            }
            catch (IOException e) {
                LOG.warn("Failed to load pull requests", e);
                SwingUtilities.invokeLater(() -> myList.setEmptyText("Error: " + e.getMessage()));
            }
        });
    }

    private @Nullable PRListItem parsePullRequest(JsonObject pr) {
        try {
            long number = pr.get("number").getAsLong();
            String title = pr.get("title").getAsString();
            String state = pr.get("state").getAsString();
            boolean draft = pr.has("draft") && pr.get("draft").getAsBoolean();
            String authorLogin = "";
            if (pr.has("user") && !pr.get("user").isJsonNull()) {
                authorLogin = pr.getAsJsonObject("user").get("login").getAsString();
            }

            GHPullRequestState prState = switch (state) {
                case "open" -> GHPullRequestState.OPEN;
                case "closed" -> GHPullRequestState.CLOSED;
                default -> GHPullRequestState.OPEN;
            };

            return new PRListItem(number, title, prState, draft, authorLogin);
        }
        catch (Exception e) {
            LOG.warn("Failed to parse PR", e);
            return null;
        }
    }

    /**
     * Simple PR list item for display.
     */
    public record PRListItem(long number, String title, GHPullRequestState state, boolean draft, String author) {
        @Override
        public String toString() {
            return "#" + number + " " + title;
        }
    }

    /**
     * Cell renderer for PR list items.
     */
    private static class PRListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof PRListItem item) {
                String stateLabel = item.draft() ? "DRAFT" : item.state().name();
                setText("<html><b>#" + item.number() + "</b> " + item.title()
                    + " <font color='gray'>(" + stateLabel + " by " + item.author() + ")</font></html>");
                setBorder(JBUI.Borders.empty(4, 8));
            }
            return component;
        }
    }
}
