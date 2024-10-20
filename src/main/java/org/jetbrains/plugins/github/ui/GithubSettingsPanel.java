/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import consulo.logging.Logger;
import consulo.platform.Platform;
import consulo.ui.ex.awt.ComboBox;
import consulo.ui.ex.awt.HyperlinkAdapter;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.github.api.GithubUser;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubNotifications;
import org.jetbrains.plugins.github.util.GithubSettings;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.io.IOException;

/**
 * @author oleg
 * @since 2010-10-20
 */
public class GithubSettingsPanel {
    private static final String DEFAULT_PASSWORD_TEXT = "************";
    private final static String AUTH_PASSWORD = "Password";
    private final static String AUTH_TOKEN = "Token";

    private static final Logger LOG = GithubUtil.LOG;

    private final GithubSettings mySettings;

    private JTextField myLoginTextField;
    private JPasswordField myPasswordField;
    private JPasswordField myTokenField;
    private JTextPane mySignupTextField;
    private JPanel myPane;
    private JButton myTestButton;
    private JTextField myHostTextField;
    private ComboBox<String> myAuthTypeComboBox;
    private JPanel myCardPanel;

    private boolean myCredentialsModified;

    public GithubSettingsPanel(@Nonnull final GithubSettings settings) {
        mySettings = settings;
        mySignupTextField.addHyperlinkListener(new HyperlinkAdapter() {
            @Override
            protected void hyperlinkActivated(final HyperlinkEvent e) {
                Platform.current().openInBrowser(e.getURL());
            }
        });
        mySignupTextField.setText("<html>Do not have an account at github.com? <a href=\"https://github.com\">" +
            "Sign up" + "</a></html>");
        mySignupTextField.setBackground(myPane.getBackground());
        mySignupTextField.setCursor(new Cursor(Cursor.HAND_CURSOR));
        myAuthTypeComboBox.addItem(AUTH_PASSWORD);
        myAuthTypeComboBox.addItem(AUTH_TOKEN);

        myTestButton.addActionListener(e -> {
            try {
                GithubUser user = GithubUtil.checkAuthData(getAuthData());
                if (GithubAuthData.AuthType.TOKEN.equals(getAuthType())) {
                    GithubNotifications.showInfoDialog(myPane, "Success", "Connection successful for user " + user.getLogin());
                }
                else {
                    GithubNotifications.showInfoDialog(myPane, "Success", "Connection successful");
                }
            }
            catch (GithubAuthenticationException ex) {
                GithubNotifications.showErrorDialog(
                    myPane,
                    "Login Failure",
                    "Can't login using given credentials: " + ex.getMessage()
                );
            }
            catch (IOException ex) {
                LOG.info(ex);
                GithubNotifications.showErrorDialog(
                    myPane,
                    "Login Failure",
                    "Can't login: " + GithubUtil.getErrorTextFromException(ex)
                );
            }
        });

        myPasswordField.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                myCredentialsModified = true;
            }
        });

        DocumentListener passwordEraser = new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                if (!myCredentialsModified) {
                    erasePassword();
                }
            }
        };
        myHostTextField.getDocument().addDocumentListener(passwordEraser);
        myLoginTextField.getDocument().addDocumentListener(passwordEraser);

        myPasswordField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (!myCredentialsModified && !getPassword().isEmpty()) {
                    erasePassword();
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
            }
        });

        myAuthTypeComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                String item = e.getItem().toString();
                if (AUTH_PASSWORD.equals(item)) {
                    ((CardLayout)myCardPanel.getLayout()).show(myCardPanel, AUTH_PASSWORD);
                }
                else if (AUTH_TOKEN.equals(item)) {
                    ((CardLayout)myCardPanel.getLayout()).show(myCardPanel, AUTH_TOKEN);
                }
                erasePassword();
            }
        });

        reset();
    }

    private void erasePassword() {
        setPassword("");
        myCredentialsModified = true;
    }

    public JComponent getPanel() {
        return myPane;
    }

    @Nonnull
    public String getHost() {
        return myHostTextField.getText().trim();
    }

    @Nonnull
    public String getLogin() {
        return myLoginTextField.getText().trim();
    }

    public void setHost(@Nonnull final String host) {
        myHostTextField.setText(host);
    }

    public void setLogin(@Nullable final String login) {
        myLoginTextField.setText(login);
    }

    @Nonnull
    private String getPassword() {
        return String.valueOf(myPasswordField.getPassword());
    }

    private void setPassword(@Nonnull final String password) {
        // Show password as blank if password is empty
        myPasswordField.setText(StringUtil.isEmpty(password) ? null : password);
    }

    @Nonnull
    public GithubAuthData.AuthType getAuthType() {
        Object selected = myAuthTypeComboBox.getSelectedItem();
        if (AUTH_PASSWORD.equals(selected)) {
            return GithubAuthData.AuthType.BASIC;
        }
        if (AUTH_TOKEN.equals(selected)) {
            return GithubAuthData.AuthType.TOKEN;
        }
        LOG.error("GithubSettingsPanel: illegal selection: basic AuthType returned", selected.toString());
        return GithubAuthData.AuthType.BASIC;
    }

    public void setAuthType(@Nonnull final GithubAuthData.AuthType type) {
        switch (type) {
            case BASIC:
                myAuthTypeComboBox.setSelectedItem(AUTH_PASSWORD);
                break;
            case TOKEN:
                myAuthTypeComboBox.setSelectedItem(AUTH_TOKEN);
                break;
            case ANONYMOUS:
            default:
                myAuthTypeComboBox.setSelectedItem(AUTH_PASSWORD);
        }
    }

    @Nonnull
    public GithubAuthData getAuthData() {
        if (!myCredentialsModified) {
            return mySettings.getAuthData();
        }
        Object selected = myAuthTypeComboBox.getSelectedItem();
        if (AUTH_PASSWORD.equals(selected)) {
            return GithubAuthData.createBasicAuth(getHost(), getLogin(), getPassword());
        }
        if (AUTH_TOKEN.equals(selected)) {
            return GithubAuthData.createTokenAuth(getHost(), getPassword());
        }
        LOG.error("GithubSettingsPanel: illegal selection: anonymous AuthData created", selected.toString());
        return GithubAuthData.createAnonymous(getHost());
    }

    public void reset() {
        setHost(mySettings.getHost());
        setLogin(mySettings.getLogin());
        setPassword(mySettings.isAuthConfigured() ? DEFAULT_PASSWORD_TEXT : "");
        setAuthType(mySettings.getAuthType());
        resetCredentialsModification();
    }

    public boolean isModified() {
        return !Comparing.equal(mySettings.getHost(), getHost()) || myCredentialsModified;
    }

    public void resetCredentialsModification() {
        myCredentialsModified = false;
    }

    private void createUIComponents() {
        Document doc = new PlainDocument();
        myPasswordField = new JPasswordField(doc, null, 0);
        myTokenField = new JPasswordField(doc, null, 0);
    }
}
