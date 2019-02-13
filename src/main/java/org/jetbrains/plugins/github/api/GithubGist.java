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

import java.util.ArrayList;
import java.util.List;

/**
 * @author Aleksey Pivovarov
 */
public class GithubGist
{
	@Nonnull
	private final String myId;
	@Nonnull
	private final String myDescription;

	private final boolean myIsPublic;

	@Nonnull
	private final String myHtmlUrl;

	@Nonnull
	private final List<GistFile> myFiles;

	@Nullable
	private final GithubUser myUser;

	public static class GistFile
	{
		@Nonnull
		private final String myFilename;
		@Nonnull
		private final String myContent;

		@Nonnull
		private final String myRawUrl;

		public GistFile(@Nonnull String filename, @Nonnull String content, @Nonnull String rawUrl)
		{
			myFilename = filename;
			myContent = content;
			myRawUrl = rawUrl;
		}

		@Nonnull
		public String getFilename()
		{
			return myFilename;
		}

		@Nonnull
		public String getContent()
		{
			return myContent;
		}

		@Nonnull
		public String getRawUrl()
		{
			return myRawUrl;
		}
	}

	@Nonnull
	public List<FileContent> getContent()
	{
		List<FileContent> ret = new ArrayList<FileContent>();
		for(GistFile file : getFiles())
		{
			ret.add(new FileContent(file.getFilename(), file.getContent()));
		}
		return ret;
	}

	public GithubGist(@Nonnull String id,
			@Nullable String description,
			boolean isPublic,
			@Nonnull String htmlUrl,
			@Nonnull List<GistFile> files,
			@Nullable GithubUser user)
	{
		myId = id;
		myDescription = StringUtil.notNullize(description);
		myIsPublic = isPublic;
		myHtmlUrl = htmlUrl;
		myFiles = files;
		myUser = user;
	}

	@Nonnull
	public String getId()
	{
		return myId;
	}

	@Nonnull
	public String getDescription()
	{
		return myDescription;
	}

	public boolean isPublic()
	{
		return myIsPublic;
	}

	@Nonnull
	public String getHtmlUrl()
	{
		return myHtmlUrl;
	}

	@Nonnull
	public List<GistFile> getFiles()
	{
		return myFiles;
	}

	@Nullable
	public GithubUser getUser()
	{
		return myUser;
	}

	public static class FileContent
	{
		@Nonnull
		private final String myFileName;
		@Nonnull
		private final String myContent;

		public FileContent(@Nonnull String fileName, @Nonnull String content)
		{
			myFileName = fileName;
			myContent = content;
		}

		@Nonnull
		public String getFileName()
		{
			return myFileName;
		}

		@Nonnull
		public String getContent()
		{
			return myContent;
		}

		@Override
		public boolean equals(Object o)
		{
			if(this == o)
				return true;
			if(o == null || getClass() != o.getClass())
				return false;

			FileContent that = (FileContent) o;

			if(!myContent.equals(that.myContent))
				return false;
			if(!myFileName.equals(that.myFileName))
				return false;

			return true;
		}

		@Override
		public int hashCode()
		{
			int result = myFileName.hashCode();
			result = 31 * result + myContent.hashCode();
			return result;
		}
	}
}
