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

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
public class GithubFile
{
	@Nonnull
	private final String myFilename;

	private final int myAdditions;
	private final int myDeletions;
	private final int myChanges;
	@Nonnull
	private final String myStatus;

	@Nonnull
	private final String myRawUrl;
	@Nonnull
	private final String myPatch;

	public GithubFile(@Nonnull String filename,
			int additions,
			int deletions,
			int changes,
			@Nonnull String status,
			@Nonnull String rawUrl,
			@Nonnull String patch)
	{
		myFilename = filename;
		myAdditions = additions;
		myDeletions = deletions;
		myChanges = changes;
		myStatus = status;
		myRawUrl = rawUrl;
		myPatch = patch;
	}

	@Nonnull
	public String getFilename()
	{
		return myFilename;
	}

	public int getAdditions()
	{
		return myAdditions;
	}

	public int getDeletions()
	{
		return myDeletions;
	}

	public int getChanges()
	{
		return myChanges;
	}

	@Nonnull
	public String getStatus()
	{
		return myStatus;
	}

	@Nonnull
	public String getRawUrl()
	{
		return myRawUrl;
	}

	@Nonnull
	public String getPatch()
	{
		return myPatch;
	}
}
