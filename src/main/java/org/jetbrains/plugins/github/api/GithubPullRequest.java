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
@SuppressWarnings("UnusedDeclaration")
public class GithubPullRequest
{
	private final long myNumber;
	@Nonnull
	private final String myState;
	@Nonnull
	private final String myTitle;
	@Nonnull
	private final String myBodyHtml;

	@Nonnull
	private final String myHtmlUrl;
	@Nonnull
	private final String myDiffUrl;
	@Nonnull
	private final String myPatchUrl;
	@Nonnull
	private final String myIssueUrl;

	@Nonnull
	private final Date myCreatedAt;
	@Nonnull
	private final Date myUpdatedAt;
	@Nullable
	private final Date myClosedAt;
	@Nullable
	private final Date myMergedAt;

	@Nonnull
	private final GithubUser myUser;

	@Nonnull
	private final Link myHead;
	@Nonnull
	private final Link myBase;

	public static class Link
	{
		@Nonnull
		private final String myLabel;
		@Nonnull
		private final String myRef;
		@Nonnull
		private final String mySha;

		@Nonnull
		private final GithubRepo myRepo;
		@Nonnull
		private final GithubUser myUser;

		public Link(@Nonnull String label,
				@Nonnull String ref,
				@Nonnull String sha,
				@Nonnull GithubRepo repo,
				@Nonnull GithubUser user)
		{
			myLabel = label;
			myRef = ref;
			mySha = sha;
			myRepo = repo;
			myUser = user;
		}

		@Nonnull
		public String getLabel()
		{
			return myLabel;
		}

		@Nonnull
		public String getRef()
		{
			return myRef;
		}

		@Nonnull
		public String getSha()
		{
			return mySha;
		}

		@Nonnull
		public GithubRepo getRepo()
		{
			return myRepo;
		}

		@Nonnull
		public GithubUser getUser()
		{
			return myUser;
		}
	}

	public GithubPullRequest(long number,
			@Nonnull String state,
			@Nonnull String title,
			@Nullable String bodyHtml,
			@Nonnull String htmlUrl,
			@Nonnull String diffUrl,
			@Nonnull String patchUrl,
			@Nonnull String issueUrl,
			@Nonnull Date createdAt,
			@Nonnull Date updatedAt,
			@Nullable Date closedAt,
			@Nullable Date mergedAt,
			@Nonnull GithubUser user,
			@Nonnull Link head,
			@Nonnull Link base)
	{
		myNumber = number;
		myState = state;
		myTitle = title;
		myBodyHtml = StringUtil.notNullize(bodyHtml);
		myHtmlUrl = htmlUrl;
		myDiffUrl = diffUrl;
		myPatchUrl = patchUrl;
		myIssueUrl = issueUrl;
		myCreatedAt = createdAt;
		myUpdatedAt = updatedAt;
		myClosedAt = closedAt;
		myMergedAt = mergedAt;
		myUser = user;
		myHead = head;
		myBase = base;
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
	public String getBodyHtml()
	{
		return myBodyHtml;
	}

	@Nonnull
	public String getHtmlUrl()
	{
		return myHtmlUrl;
	}

	@Nonnull
	public String getDiffUrl()
	{
		return myDiffUrl;
	}

	@Nonnull
	public String getPatchUrl()
	{
		return myPatchUrl;
	}

	@Nonnull
	public String getIssueUrl()
	{
		return myIssueUrl;
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

	@Nullable
	public Date getClosedAt()
	{
		return myClosedAt;
	}

	@Nullable
	public Date getMergedAt()
	{
		return myMergedAt;
	}

	@Nonnull
	public GithubUser getUser()
	{
		return myUser;
	}

	@Nonnull
	public Link getHead()
	{
		return myHead;
	}

	@Nonnull
	public Link getBase()
	{
		return myBase;
	}
}
