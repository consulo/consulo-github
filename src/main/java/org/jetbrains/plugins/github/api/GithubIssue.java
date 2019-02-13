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

import com.intellij.openapi.util.text.StringUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Date;

/**
 * @author Aleksey Pivovarov
 */
public class GithubIssue
{
	@Nonnull
	private final String myHtmlUrl;
	private final long myNumber;
	@Nonnull
	private final String myState;
	@Nonnull
	private final String myTitle;
	@Nonnull
	private final String myBody;

	@Nonnull
	private final GithubUser myUser;
	@Nullable
	private final GithubUser myAssignee;

	@Nullable
	private final Date myClosedAt;
	@Nonnull
	private final Date myCreatedAt;
	@Nonnull
	private final Date myUpdatedAt;

	public GithubIssue(@Nonnull String htmlUrl,
			long number,
			@Nonnull String state,
			@Nonnull String title,
			@Nullable String body,
			@Nonnull GithubUser user,
			@Nullable GithubUser assignee,
			@Nullable Date closedAt,
			@Nonnull Date createdAt,
			@Nonnull Date updatedAt)
	{
		myHtmlUrl = htmlUrl;
		myNumber = number;
		myState = state;
		myTitle = title;
		myBody = StringUtil.notNullize(body);
		myUser = user;
		myAssignee = assignee;
		myClosedAt = closedAt;
		myCreatedAt = createdAt;
		myUpdatedAt = updatedAt;
	}

	@Nonnull
	public String getHtmlUrl()
	{
		return myHtmlUrl;
	}

	public long getNumber()
	{
		return myNumber;
	}

	@Nonnull
	public String getState()
	{
		return myState;
	}

	@Nonnull
	public String getTitle()
	{
		return myTitle;
	}

	@Nonnull
	public String getBody()
	{
		return myBody;
	}

	@Nonnull
	public GithubUser getUser()
	{
		return myUser;
	}

	@Nullable
	public GithubUser getAssignee()
	{
		return myAssignee;
	}

	@Nullable
	public Date getClosedAt()
	{
		return myClosedAt;
	}

	@Nonnull
	public Date getCreatedAt()
	{
		return myCreatedAt;
	}

	@Nonnull
	public Date getUpdatedAt()
	{
		return myUpdatedAt;
	}
}
