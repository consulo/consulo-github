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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Comparator;

import javax.annotation.Nonnull;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import javax.annotation.Nullable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SortedComboBoxModel;
import com.intellij.util.Consumer;

/**
 * @author Aleksey Pivovarov
 */
public class GithubCreatePullRequestPanel
{
	private JTextField myTitleTextField;
	private JTextArea myDescriptionTextArea;
	private ComboBox myBranchComboBox;
	private SortedComboBoxModel<String> myBranchModel;
	private JPanel myPanel;
	private JButton myShowDiffButton;

	public GithubCreatePullRequestPanel(@Nonnull final Consumer<String> showDiff)
	{
		myDescriptionTextArea.setBorder(BorderFactory.createEtchedBorder());
		myBranchModel = new SortedComboBoxModel<String>(new Comparator<String>()
		{
			@Override
			public int compare(String o1, String o2)
			{
				return StringUtil.naturalCompare(o1, o2);
			}
		});
		myBranchComboBox.setModel(myBranchModel);
		myShowDiffButton.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				showDiff.consume(getBranch());
			}
		});
	}

	@Nonnull
	public String getTitle()
	{
		return myTitleTextField.getText();
	}

	@Nonnull
	public String getDescription()
	{
		return myDescriptionTextArea.getText();
	}

	@Nonnull
	public String getBranch()
	{
		return myBranchComboBox.getSelectedItem().toString();
	}

	public void setSelectedBranch(@Nullable String branch)
	{
		if(StringUtil.isEmptyOrSpaces(branch))
		{
			myBranchComboBox.setSelectedItem("");
			return;
		}

		if(myBranchModel.indexOf(branch) == -1)
		{
			myBranchModel.add(branch);
		}
		myBranchComboBox.setSelectedItem(branch);
	}

	public void setBranches(@Nonnull Collection<String> branches)
	{
		myBranchModel.clear();
		myBranchModel.addAll(branches);
	}

	public JPanel getPanel()
	{
		return myPanel;
	}

	@Nonnull
	public JComponent getPreferredComponent()
	{
		return myTitleTextField;
	}

	public JComponent getBranchEditor()
	{
		return myBranchComboBox;
	}

	public JComponent getTitleTextField()
	{
		return myTitleTextField;
	}

	public void setTitle(String title)
	{
		myTitleTextField.setText(title);
	}
}
