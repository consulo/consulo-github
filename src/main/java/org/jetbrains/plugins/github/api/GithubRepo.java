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
package org.jetbrains.plugins.github.api;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.intellij.openapi.util.text.StringUtil;

/**
 * @author Aleksey Pivovarov
 */
public class GithubRepo
{
	@Nonnull
	private final String myName;
	@Nonnull
	private final String myDescription;

	private final boolean myIsPrivate;
	private final boolean myIsFork;

	@Nonnull
	private final String myHtmlUrl;
	@Nonnull
	private final String myCloneUrl;

	@Nullable
	private final String myDefaultBranch;

	@Nonnull
	private final GithubUser myOwner;

	public GithubRepo(@Nonnull String name,
			@Nullable String description,
			boolean isPrivate,
			boolean isFork,
			@Nonnull String htmlUrl,
			@Nonnull String cloneUrl,
			@Nullable String defaultBranch,
			@Nonnull GithubUser owner)
	{
		myName = name;
		myDescription = StringUtil.notNullize(description);
		myIsPrivate = isPrivate;
		myIsFork = isFork;
		myHtmlUrl = htmlUrl;
		myCloneUrl = cloneUrl;
		myDefaultBranch = defaultBranch;
		myOwner = owner;
	}

	@Nonnull
	public String getName()
	{
		return myName;
	}

	@Nonnull
	public String getFullName()
	{
		return getUserName() + "/" + getName();
	}

	@Nonnull
	public String getDescription()
	{
		return myDescription;
	}

	public boolean isPrivate()
	{
		return myIsPrivate;
	}

	public boolean isFork()
	{
		return myIsFork;
	}

	@Nonnull
	public String getHtmlUrl()
	{
		return myHtmlUrl;
	}

	@Nonnull
	public String getCloneUrl()
	{
		return myCloneUrl;
	}

	@Nullable
	public String getDefaultBranch()
	{
		return myDefaultBranch;
	}

	@Nonnull
	public GithubUser getOwner()
	{
		return myOwner;
	}

	@Nonnull
	public String getUserName()
	{
		return getOwner().getLogin();
	}

	@Nonnull
	public GithubFullPath getFullPath()
	{
		return new GithubFullPath(getUserName(), getName());
	}
}

