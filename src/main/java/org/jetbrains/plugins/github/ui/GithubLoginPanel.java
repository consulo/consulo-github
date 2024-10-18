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

import consulo.ide.impl.idea.util.ui.table.ComponentsListFocusTraversalPolicy;
import consulo.platform.Platform;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.HyperlinkAdapter;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.util.collection.ContainerUtil;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 * @date 10/20/10
 */
public class GithubLoginPanel {
    private JPanel myPane;
    private JTextField myHostTextField;
    private JTextField myLoginTextField;
    private JPasswordField myPasswordField;
    private JTextPane mySignupTextField;
    private JCheckBox mySavePasswordCheckBox;
    private ComboBox myAuthTypeComboBox;
    private JLabel myPasswordLabel;
    private JLabel myLoginLabel;

    private final static String AUTH_PASSWORD = "Password";
    private final static String AUTH_TOKEN = "Token";

    public GithubLoginPanel(final GithubLoginDialog dialog) {
        DocumentListener listener = new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                dialog.clearErrors();
            }
        };
        myLoginTextField.getDocument().addDocumentListener(listener);
        myPasswordField.getDocument().addDocumentListener(listener);
        mySignupTextField.setText("<html>Do not have an account at github.com? <a href=\"https://github.com\">Sign " +
            "up</a>.</html>");
        mySignupTextField.setMargin(new Insets(5, 0, 0, 0));
        mySignupTextField.addHyperlinkListener(new HyperlinkAdapter() {
            @Override
            protected void hyperlinkActivated(final HyperlinkEvent e) {
                Platform.current().openInBrowser(e.getURL());
            }
        });
        mySignupTextField.setBackground(UIUtil.TRANSPARENT_COLOR);
        mySignupTextField.setCursor(new Cursor(Cursor.HAND_CURSOR));

        myAuthTypeComboBox.addItem(AUTH_PASSWORD);
        myAuthTypeComboBox.addItem(AUTH_TOKEN);

        myAuthTypeComboBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    String item = e.getItem().toString();
                    if (AUTH_PASSWORD.equals(item)) {
                        myPasswordLabel.setText("Password:");
                        mySavePasswordCheckBox.setText("Save password");
                        myLoginLabel.setVisible(true);
                        myLoginTextField.setVisible(true);
                    }
                    else if (AUTH_TOKEN.equals(item)) {
                        myPasswordLabel.setText("Token:");
                        mySavePasswordCheckBox.setText("Save token");
                        myLoginLabel.setVisible(false);
                        myLoginTextField.setVisible(false);
                    }
                    if (dialog.isShowing()) {
                        dialog.pack();
                    }
                }
            }
        });

        List<Component> order = new ArrayList<Component>();
        order.add(myHostTextField);
        order.add(myAuthTypeComboBox);
        order.add(myLoginTextField);
        order.add(myPasswordField);
        order.add(mySavePasswordCheckBox);
        myPane.setFocusTraversalPolicyProvider(true);
        myPane.setFocusTraversalPolicy(new MyFocusTraversalPolicy(order));
    }

    public JComponent getPanel() {
        return myPane;
    }

    public void setHost(@Nonnull String host) {
        myHostTextField.setText(host);
    }

    public void setLogin(@Nullable String login) {
        myLoginTextField.setText(login);
    }

    public void setAuthType(@Nonnull GithubAuthData.AuthType type) {
        switch (type) {
            case BASIC:
                myAuthTypeComboBox.setSelectedItem(AUTH_PASSWORD);
                break;
            case TOKEN:
                myAuthTypeComboBox.setSelectedItem(AUTH_TOKEN);
                break;
            case ANONYMOUS:
                myAuthTypeComboBox.setSelectedItem(AUTH_PASSWORD);
        }
    }

    public void lockAuthType(@Nonnull GithubAuthData.AuthType type) {
        setAuthType(type);
        myAuthTypeComboBox.setEnabled(false);
    }

    public void lockHost(@Nonnull String host) {
        setHost(host);
        myHostTextField.setEnabled(false);
    }

    public void setSavePasswordSelected(boolean savePassword) {
        mySavePasswordCheckBox.setSelected(savePassword);
    }

    public void setSavePasswordVisibleEnabled(boolean visible) {
        mySavePasswordCheckBox.setVisible(visible);
        mySavePasswordCheckBox.setEnabled(visible);
    }

    @Nonnull
    public String getHost() {
        return myHostTextField.getText().trim();
    }

    @Nonnull
    public String getLogin() {
        return myLoginTextField.getText().trim();
    }

    @Nonnull
    private String getPassword() {
        return String.valueOf(myPasswordField.getPassword());
    }

    public boolean isSavePasswordSelected() {
        return mySavePasswordCheckBox.isSelected();
    }

    public JComponent getPreferrableFocusComponent() {
        return myLoginTextField.isVisible() ? myLoginTextField : myPasswordField;
    }

    @Nonnull
    public GithubAuthData getAuthData() {
        Object selected = myAuthTypeComboBox.getSelectedItem();
        if (AUTH_PASSWORD.equals(selected)) {
            return GithubAuthData.createBasicAuth(getHost(), getLogin(), getPassword());
        }
        if (AUTH_TOKEN.equals(selected)) {
            return GithubAuthData.createTokenAuth(getHost(), getPassword());
        }
        GithubUtil.LOG.error("GithubLoginPanel illegal selection: anonymous AuthData created", selected.toString());
        return GithubAuthData.createAnonymous(getHost());
    }

    private static class MyFocusTraversalPolicy extends ComponentsListFocusTraversalPolicy {
        @Nonnull
        private List<Component> myOrder;

        private MyFocusTraversalPolicy(@Nonnull List<Component> order) {
            myOrder = order;
        }

        @Nonnull
        @Override
        protected List<Component> getOrderedComponents() {
            return ContainerUtil.filter(myOrder, component -> component.isVisible() && component.isEnabled());
        }
    }
}
