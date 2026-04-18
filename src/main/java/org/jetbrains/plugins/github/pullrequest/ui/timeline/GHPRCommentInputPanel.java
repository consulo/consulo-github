// Copyright 2013-2025 consulo.io
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
package org.jetbrains.plugins.github.pullrequest.ui.timeline;

import consulo.ui.ex.awt.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Simple comment input panel with a text area and submit/cancel buttons.
 */
public class GHPRCommentInputPanel extends JPanel {
    private final JTextArea myTextArea;
    private final JButton mySubmitButton;
    private final JButton myCancelButton;

    public GHPRCommentInputPanel(Consumer<String> onSubmit) {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(8));

        myTextArea = new JTextArea(3, 40);
        myTextArea.setLineWrap(true);
        myTextArea.setWrapStyleWord(true);
        myTextArea.setBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()));
        add(new JScrollPane(myTextArea), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        myCancelButton = new JButton("Cancel");
        myCancelButton.addActionListener(e -> {
            myTextArea.setText("");
        });
        buttonPanel.add(myCancelButton);

        mySubmitButton = new JButton("Comment");
        mySubmitButton.addActionListener(e -> {
            String text = myTextArea.getText().trim();
            if (!text.isEmpty()) {
                onSubmit.accept(text);
                myTextArea.setText("");
            }
        });
        buttonPanel.add(mySubmitButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    public void setEnabled(boolean enabled) {
        myTextArea.setEnabled(enabled);
        mySubmitButton.setEnabled(enabled);
        myCancelButton.setEnabled(enabled);
    }

    public void requestTextFocus() {
        myTextArea.requestFocusInWindow();
    }
}
