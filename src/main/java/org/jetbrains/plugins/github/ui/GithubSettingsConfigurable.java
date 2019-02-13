package org.jetbrains.plugins.github.ui;

import javax.annotation.Nonnull;
import javax.swing.JComponent;

import javax.annotation.Nullable;
import org.jetbrains.plugins.github.util.GithubSettings;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsConfigurableProvider;

/**
 * @author oleg
 */
public class GithubSettingsConfigurable implements SearchableConfigurable, VcsConfigurableProvider
{
	private GithubSettingsPanel mySettingsPane;
	private final GithubSettings mySettings;

	public GithubSettingsConfigurable()
	{
		mySettings = GithubSettings.getInstance();
	}

	@Nonnull
	public String getDisplayName()
	{
		return "GitHub";
	}

	@Nonnull
	public String getHelpTopic()
	{
		return "settings.github";
	}

	@Nonnull
	public JComponent createComponent()
	{
		if(mySettingsPane == null)
		{
			mySettingsPane = new GithubSettingsPanel(mySettings);
		}
		return mySettingsPane.getPanel();
	}

	public boolean isModified()
	{
		return mySettingsPane != null && mySettingsPane.isModified();
	}

	public void apply() throws ConfigurationException
	{
		if(mySettingsPane != null)
		{
			mySettings.setCredentials(mySettingsPane.getHost(), mySettingsPane.getAuthData(), true);
			mySettingsPane.resetCredentialsModification();
		}
	}

	public void reset()
	{
		if(mySettingsPane != null)
		{
			mySettingsPane.reset();
		}
	}

	public void disposeUIResources()
	{
		mySettingsPane = null;
	}

	@Nonnull
	public String getId()
	{
		return getHelpTopic();
	}

	public Runnable enableSearch(String option)
	{
		return null;
	}

	@Nullable
	@Override
	public Configurable getConfigurable(Project project)
	{
		return this;
	}
}
