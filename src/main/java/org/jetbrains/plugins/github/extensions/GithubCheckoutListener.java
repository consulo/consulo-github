package org.jetbrains.plugins.github.extensions;

import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.project.startup.StartupManager;
import consulo.task.TaskManager;
import consulo.task.TaskRepository;
import consulo.versionControlSystem.checkout.CompletedCheckoutListener;
import git4idea.repo.GitRepository;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.tasks.GithubRepository;
import org.jetbrains.plugins.github.tasks.GithubRepositoryType;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.annotation.Nullable;
import java.io.File;

// TODO: remove ?

/**
 * @author oleg
 * @date 10/26/10
 */
@ExtensionImpl
public class GithubCheckoutListener implements CompletedCheckoutListener
{
	@Override
	public boolean processCheckedOutDirectory(Project project, File directory)
	{
		return false;
	}

	@Override
	public void processOpenedProject(final Project lastOpenedProject)
	{
		//final GithubFullPath info = getGithubProjectInfo(lastOpenedProject);
		//if (info != null) {
		//  processProject(lastOpenedProject, info.getUser(), info.getRepository());
		//}
	}

	@Nullable
	private static GithubFullPath getGithubProjectInfo(final Project project)
	{
		final GitRepository gitRepository = GithubUtil.getGitRepository(project, null);
		if(gitRepository == null)
		{
			return null;
		}

		// Check that given repository is properly configured git repository
		String url = GithubUtil.findGithubRemoteUrl(gitRepository);
		if(url == null)
		{
			return null;
		}
		return GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(url);
	}

	private static void processProject(final Project openedProject, final String author, final String name)
	{
		// try to enable git tasks integration
		final Runnable taskInitializationRunnable = new Runnable()
		{
			public void run()
			{
				try
				{
					enableGithubTrackerIntegration(openedProject, author, name);
				}
				catch(Exception e)
				{
					// Ignore it
				}
			}
		};
		if(openedProject.isInitialized())
		{
			taskInitializationRunnable.run();
		}
		else
		{
			StartupManager.getInstance(openedProject).runWhenProjectIsInitialized(taskInitializationRunnable);
		}
	}

	private static void enableGithubTrackerIntegration(final Project project, final String author, final String name)
	{
		// Look for github repository type
		final TaskManager  manager =  TaskManager.getManager(project);
		final TaskRepository[] allRepositories = manager.getAllRepositories();
		for(TaskRepository repository : allRepositories)
		{
			if(repository instanceof GithubRepository)
			{
				return;
			}
		}
		// Create new one if not found exists
		final GithubRepository repository = new GithubRepository(new GithubRepositoryType());
		repository.setToken("");
		repository.setRepoAuthor(author);
		repository.setRepoName(name);

		manager.addRepository(repository);
	}

}