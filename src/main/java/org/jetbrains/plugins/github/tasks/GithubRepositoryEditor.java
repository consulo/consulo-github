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
package org.jetbrains.plugins.github.tasks;

import consulo.github.localize.GithubLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.task.localize.TaskLocalize;
import consulo.task.ui.BaseRepositoryEditor;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.awt.FormBuilder;
import consulo.ui.ex.awt.GridBag;
import consulo.ui.ex.awt.JBCheckBox;
import consulo.ui.ex.awt.JBLabel;
import consulo.ui.ex.awt.JBTextField;
import consulo.ui.ex.awt.event.DocumentAdapter;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.function.Consumer;

final class GithubRepositoryEditor extends BaseRepositoryEditor<GithubRepository> {
    private MyTextField myRepoAuthor;
    private MyTextField myRepoName;
    private MyTextField myToken;
    private JBCheckBox myShowNotAssignedIssues;
    private JButton myTokenButton;
    private JBLabel myHostLabel;
    private JBLabel myRepositoryLabel;
    private JBLabel myTokenLabel;

    GithubRepositoryEditor(final Project project, final GithubRepository repository, Consumer<GithubRepository> changeListener) {
        super(project, repository, changeListener);
        myUrlLabel.setVisible(false);
        myUsernameLabel.setVisible(false);
        myUserNameText.setVisible(false);
        myPasswordLabel.setVisible(false);
        myPasswordText.setVisible(false);
        myUseHttpAuthenticationCheckBox.setVisible(false);

        myRepoAuthor.setText(repository.getRepoAuthor());
        myRepoName.setText(repository.getRepoName());
        myToken.setText(repository.getPassword());
        myShowNotAssignedIssues.setSelected(!repository.isAssignedIssuesOnly());

        DocumentListener buttonUpdater = new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                updateTokenButton();
            }
        };

        myURLText.getDocument().addDocumentListener(buttonUpdater);
        myRepoAuthor.getDocument().addDocumentListener(buttonUpdater);
        myRepoName.getDocument().addDocumentListener(buttonUpdater);
    }

    @Override
    protected @Nullable JComponent createCustomPanel() {
        myHostLabel = new JBLabel(GithubLocalize.taskRepoHostField().get(), SwingConstants.RIGHT);

        JPanel myHostPanel = new JPanel(new BorderLayout(5, 0));
        myHostPanel.add(myURLText, BorderLayout.CENTER);
        myHostPanel.add(myShareUrlCheckBox, BorderLayout.EAST);

        myRepositoryLabel = new JBLabel(GithubLocalize.taskRepoRepositoryField().get(), SwingConstants.RIGHT);
        myRepoAuthor = new MyTextField(GithubLocalize.taskRepoOwnerFieldEmptyHint());
        myRepoName = new MyTextField(GithubLocalize.taskRepoNameFieldEmptyHint());
        myRepoAuthor.setPreferredSize("SomelongNickname");
        myRepoName.setPreferredSize("SomelongReponame-with-suffixes");

        JPanel myRepoPanel = new JPanel(new GridBagLayout());
        GridBag bag = new GridBag().setDefaultWeightX(1).setDefaultFill(GridBagConstraints.HORIZONTAL);
        myRepoPanel.add(myRepoAuthor, bag.nextLine().next());
        myRepoPanel.add(new JLabel("/"), bag.next().fillCellNone().insets(0, 5, 0, 5).weightx(0));
        myRepoPanel.add(myRepoName, bag.next());

        myTokenLabel = new JBLabel(GithubLocalize.taskRepoTokenField().get(), SwingConstants.RIGHT);
        myToken = new MyTextField(GithubLocalize.taskRepoTokenFieldEmptyHint());
        myTokenButton = new JButton(GithubLocalize.taskRepoTokenCreateButton().get());
        myTokenButton.addActionListener(e -> generateToken());

        JPanel myTokenPanel = new JPanel();
        myTokenPanel.setLayout(new BorderLayout(5, 5));
        myTokenPanel.add(myToken, BorderLayout.CENTER);
        myTokenPanel.add(myTokenButton, BorderLayout.EAST);

        myShowNotAssignedIssues = new JBCheckBox(TaskLocalize.checkboxIncludeIssuesNotAssignedToMe().get());

        installListener(myRepoAuthor);
        installListener(myRepoName);
        installListener(myToken);
        installListener(myShowNotAssignedIssues);

        return FormBuilder.createFormBuilder()
            .setAlignLabelOnRight(true)
            .addLabeledComponent(myHostLabel, myHostPanel)
            .addLabeledComponent(myRepositoryLabel, myRepoPanel)
            .addLabeledComponent(myTokenLabel, myTokenPanel)
            .addComponentToRightColumn(myShowNotAssignedIssues)
            .getPanel();
    }

    @Override
    public void apply() {
        super.apply();
        myRepository.setRepoName(getRepoName());
        myRepository.setRepoAuthor(getRepoAuthor());
        myRepository.setPassword(getToken());
        myRepository.setAssignedIssuesOnly(isAssignedIssuesOnly());
    }

    @RequiredUIAccess
    private void generateToken() {
        UIAccess uiAccess = UIAccess.current();
        GHRepositoryEditor.askToken(myProject, getHost()).whenCompleteAsync((token, error) -> {
            if (token != null) {
                myToken.setText(token);
            }
            doApply();
        }, uiAccess);
    }

    @Override
    public void setAnchor(final @Nullable JComponent anchor) {
        super.setAnchor(anchor);
        myHostLabel.setAnchor(anchor);
        myRepositoryLabel.setAnchor(anchor);
        myTokenLabel.setAnchor(anchor);
    }

    private void updateTokenButton() {
        if (StringUtil.isEmptyOrSpaces(getHost()) ||
            StringUtil.isEmptyOrSpaces(getRepoAuthor()) ||
            StringUtil.isEmptyOrSpaces(getRepoName())) {
            myTokenButton.setEnabled(false);
        }
        else {
            myTokenButton.setEnabled(true);
        }
    }

    private String getHost() {
        return myURLText.getText().trim();
    }

    private String getRepoAuthor() {
        return myRepoAuthor.getText().trim();
    }

    private String getRepoName() {
        return myRepoName.getText().trim();
    }

    private String getToken() {
        return myToken.getText().trim();
    }

    private boolean isAssignedIssuesOnly() {
        return !myShowNotAssignedIssues.isSelected();
    }

    public static class MyTextField extends JBTextField {
        private int myWidth = -1;

        public MyTextField(LocalizeValue hintCaption) {
            getEmptyText().setText(hintCaption);
        }

        public void setPreferredSize(String sampleSizeString) {
            myWidth = getFontMetrics(getFont()).stringWidth(sampleSizeString);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            if (myWidth != -1) {
                size.width = myWidth;
            }
            return size;
        }
    }
}
