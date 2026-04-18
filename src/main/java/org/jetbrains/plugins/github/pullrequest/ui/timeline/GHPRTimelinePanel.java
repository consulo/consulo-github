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
package org.jetbrains.plugins.github.pullrequest.ui.timeline;

import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBUI;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.List;

/**
 * Panel rendering a PR timeline with comments, reviews, events.
 */
public class GHPRTimelinePanel extends JPanel {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM d, yyyy HH:mm");

    public GHPRTimelinePanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(8));
    }

    public void setTimelineItems(List<GHPRTimelineItem> items) {
        removeAll();

        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));

        for (GHPRTimelineItem item : items) {
            itemsPanel.add(createItemPanel(item));
            itemsPanel.add(Box.createVerticalStrut(8));
        }

        add(new consulo.ui.ex.awt.JBScrollPane(itemsPanel), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private JPanel createItemPanel(GHPRTimelineItem item) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 0, 0, 1, 0));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height + 200));

        // Header: author + date
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(JBUI.Borders.emptyBottom(4));

        String headerText = buildHeaderText(item);
        JBLabel headerLabel = new JBLabel(headerText);
        headerPanel.add(headerLabel, BorderLayout.WEST);

        if (item.getCreatedAt() != null) {
            JBLabel dateLabel = new JBLabel(DATE_FORMAT.format(item.getCreatedAt()));
            dateLabel.setForeground(JBUI.CurrentTheme.Label.disabledForeground());
            headerPanel.add(dateLabel, BorderLayout.EAST);
        }

        panel.add(headerPanel, BorderLayout.NORTH);

        // Body
        if (item.getBody() != null && !item.getBody().isEmpty()) {
            JTextArea bodyArea = new JTextArea(item.getBody());
            bodyArea.setEditable(false);
            bodyArea.setLineWrap(true);
            bodyArea.setWrapStyleWord(true);
            bodyArea.setBackground(panel.getBackground());
            bodyArea.setBorder(JBUI.Borders.empty(4, 0));
            panel.add(bodyArea, BorderLayout.CENTER);
        }

        return panel;
    }

    private String buildHeaderText(GHPRTimelineItem item) {
        String author = item.getAuthorLogin() != null ? item.getAuthorLogin() : "Unknown";
        return switch (item.getType()) {
            case COMMENT -> "<html><b>" + author + "</b> commented</html>";
            case REVIEW -> "<html><b>" + author + "</b> reviewed</html>";
            case COMMIT -> "<html><b>" + author + "</b> pushed commits</html>";
            case EVENT -> {
                String eventType = item.getEventType() != null ? item.getEventType() : "event";
                yield "<html><b>" + author + "</b> " + eventType + "</html>";
            }
        };
    }
}
