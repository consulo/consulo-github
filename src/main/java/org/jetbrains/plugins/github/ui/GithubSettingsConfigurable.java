package org.jetbrains.plugins.github.ui;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.versionControlSystem.VcsConfigurableProvider;
import jakarta.inject.Inject;
import org.jetbrains.plugins.github.util.GithubSettings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;

/**
 * @author oleg
 */
@ExtensionImpl
public class GithubSettingsConfigurable implements SearchableConfigurable, VcsConfigurableProvider {
    private GithubSettingsPanel mySettingsPane;
    private final GithubSettings mySettings;

    @Inject
    public GithubSettingsConfigurable(GithubSettings githubSettings) {
        mySettings = githubSettings;
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return "GitHub";
    }

    @Nonnull
    @Override
    public String getHelpTopic() {
        return "settings.github";
    }

    @Nonnull
    @Override
    @RequiredUIAccess
    public JComponent createComponent() {
        if (mySettingsPane == null) {
            mySettingsPane = new GithubSettingsPanel(mySettings);
        }
        return mySettingsPane.getPanel();
    }

    @Override
    @RequiredUIAccess
    public boolean isModified() {
        return mySettingsPane != null && mySettingsPane.isModified();
    }

    @Override
    @RequiredUIAccess
    public void apply() throws ConfigurationException {
        if (mySettingsPane != null) {
            mySettings.setCredentials(mySettingsPane.getHost(), mySettingsPane.getAuthData(), true);
            mySettingsPane.resetCredentialsModification();
        }
    }

    @Override
    @RequiredUIAccess
    public void reset() {
        if (mySettingsPane != null) {
            mySettingsPane.reset();
        }
    }

    @Override
    @RequiredUIAccess
    public void disposeUIResources() {
        mySettingsPane = null;
    }

    @Override
    @Nonnull
    public String getId() {
        return getHelpTopic();
    }

    @Override
    public Runnable enableSearch(String option) {
        return null;
    }

    @Nullable
    @Override
    public Configurable getConfigurable(Project project) {
        return this;
    }
}
