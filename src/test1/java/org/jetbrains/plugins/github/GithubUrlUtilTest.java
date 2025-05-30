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
package org.jetbrains.plugins.github;

import com.intellij.openapi.util.Pair;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.Convertor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.plugins.github.api.GithubFullPath;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.plugins.github.util.GithubUrlUtil.*;

/**
 * @author Aleksey Pivovarov
 */
public class GithubUrlUtilTest extends UsefulTestCase {
  private static class TestCase<T> {
    @Nonnull
	final public List<Pair<String, T>> tests = new ArrayList<Pair<String, T>>();

    public void add(@Nonnull String in, @Nullable T out) {
      tests.add(Pair.create(in, out));
    }

  }

  private static <T> void runTestCase(@Nonnull TestCase<T> tests, @Nonnull Convertor<String, T> func) {
    for (Pair<String, T> test : tests.tests) {
      assertEquals(test.getFirst(), test.getSecond(), func.convert(test.getFirst()));
    }
  }

  public void testRemoveTrailingSlash() throws Throwable {
    TestCase<String> tests = new TestCase<String>();

    tests.add("http://github.com/", "http://github.com");
    tests.add("http://github.com", "http://github.com");

    tests.add("http://github.com/user/repo/", "http://github.com/user/repo");
    tests.add("http://github.com/user/repo", "http://github.com/user/repo");

    runTestCase(tests, new Convertor<String, String>() {
      @Override
      public String convert(String in) {
        return removeTrailingSlash(in);
      }
    });
  }

  public void testRemoveProtocolPrefix() throws Throwable {
    TestCase<String> tests = new TestCase<String>();

    tests.add("github.com/user/repo/", "github.com/user/repo/");
    tests.add("api.github.com/user/repo/", "api.github.com/user/repo/");

    tests.add("http://github.com/user/repo/", "github.com/user/repo/");
    tests.add("https://github.com/user/repo/", "github.com/user/repo/");
    tests.add("git://github.com/user/repo/", "github.com/user/repo/");
    tests.add("git@github.com:user/repo/", "github.com/user/repo/");

    tests.add("git@github.com:username/repo/", "github.com/username/repo/");
    tests.add("https://username:password@github.com/user/repo/", "github.com/user/repo/");
    tests.add("https://username@github.com/user/repo/", "github.com/user/repo/");
    tests.add("https://github.com:2233/user/repo/", "github.com:2233/user/repo/");

    tests.add("HTTP://GITHUB.com/user/repo/", "GITHUB.com/user/repo/");
    tests.add("HttP://GitHub.com/user/repo/", "GitHub.com/user/repo/");

    runTestCase(tests, new Convertor<String, String>() {
      @Override
      public String convert(String in) {
        return removeProtocolPrefix(in);
      }
    });
  }

  public void testIsGithubUrl() throws Throwable {
    TestCase<Boolean> tests = new TestCase<Boolean>();

    tests.add("http://github.com/user/repo", true);
    tests.add("https://github.com/user/repo", true);
    tests.add("git://github.com/user/repo", true);
    tests.add("git@github.com:user/repo", true);

    tests.add("https://github.com/", true);
    tests.add("github.com", true);

    tests.add("https://user@github.com/user/repo", true);
    tests.add("https://user:password@github.com/user/repo", true);
    tests.add("git@github.com:user/repo", true);

    tests.add("https://github.com:2233/", true);

    tests.add("HTTPS://GitHub.com:2233/", true);

    tests.add("google.com", false);
    tests.add("github.com.site.ua", false);
    tests.add("sf@hskfh../.#fwenj 32#$", false);
    tests.add("api.github.com", false);
    tests.add("site.com//github.com", false);

    runTestCase(tests, new Convertor<String, Boolean>() {
      @Override
      public Boolean convert(String in) {
        return isGithubUrl(in, removeTrailingSlash(removeProtocolPrefix("https://github.com/")));
      }
    });
    runTestCase(tests, new Convertor<String, Boolean>() {
      @Override
      public Boolean convert(String in) {
        return isGithubUrl(in, removeTrailingSlash(removeProtocolPrefix("http://GitHub.com")));
      }
    });
  }

  public void testGetApiUrlWithoutProtocol() throws Throwable {
    TestCase<String> tests = new TestCase<String>();

    tests.add("github.com", "api.github.com");
    tests.add("https://github.com/", "api.github.com");
    tests.add("api.github.com/", "api.github.com");

    tests.add("http://my.site.com/", "my.site.com/api/v3");
    tests.add("http://api.site.com/", "api.site.com/api/v3");
    tests.add("http://url.github.com/", "url.github.com/api/v3");

    tests.add("HTTP://GITHUB.com", "api.github.com");
    tests.add("HttP://GitHub.com/", "api.github.com");

    runTestCase(tests, new Convertor<String, String>() {
      @Override
      public String convert(String in) {
        return getApiUrlWithoutProtocol(in);
      }
    });
  }

  public void testGetUserAndRepositoryFromRemoteUrl() throws Throwable {
    TestCase<GithubFullPath> tests = new TestCase<GithubFullPath>();

    tests.add("http://github.com/username/reponame/", new GithubFullPath("username", "reponame"));
    tests.add("https://github.com/username/reponame/", new GithubFullPath("username", "reponame"));
    tests.add("git://github.com/username/reponame/", new GithubFullPath("username", "reponame"));
    tests.add("git@github.com:username/reponame/", new GithubFullPath("username", "reponame"));

    tests.add("https://github.com/username/reponame", new GithubFullPath("username", "reponame"));
    tests.add("https://github.com/username/reponame.git", new GithubFullPath("username", "reponame"));
    tests.add("https://github.com/username/reponame.git/", new GithubFullPath("username", "reponame"));
    tests.add("git@github.com:username/reponame.git/", new GithubFullPath("username", "reponame"));

    tests.add("http://login:passsword@github.com/username/reponame/", new GithubFullPath("username", "reponame"));

    tests.add("HTTPS://GitHub.com/username/reponame/", new GithubFullPath("username", "reponame"));
    tests.add("https://github.com/UserName/RepoName/", new GithubFullPath("UserName", "RepoName"));

    tests.add("https://github.com/RepoName/", null);
    tests.add("git@github.com:user/", null);
    tests.add("https://user:pass@github.com/", null);

    runTestCase(tests, new Convertor<String, GithubFullPath>() {
      @Override
      @Nullable
      public GithubFullPath convert(String in) {
        return getUserAndRepositoryFromRemoteUrl(in);
      }
    });
  }

  public void testMakeGithubRepoFromRemoteUrl() throws Throwable {
    TestCase<String> tests = new TestCase<String>();

    tests.add("http://github.com/username/reponame/", "https://github.com/username/reponame");
    tests.add("https://github.com/username/reponame/", "https://github.com/username/reponame");
    tests.add("git://github.com/username/reponame/", "https://github.com/username/reponame");
    tests.add("git@github.com:username/reponame/", "https://github.com/username/reponame");

    tests.add("https://github.com/username/reponame", "https://github.com/username/reponame");
    tests.add("https://github.com/username/reponame.git", "https://github.com/username/reponame");
    tests.add("https://github.com/username/reponame.git/", "https://github.com/username/reponame");
    tests.add("git@github.com:username/reponame.git/", "https://github.com/username/reponame");

    tests.add("git@github.com:username/reponame/", "https://github.com/username/reponame");
    tests.add("http://login:passsword@github.com/username/reponame/", "https://github.com/username/reponame");

    tests.add("HTTPS://GitHub.com/username/reponame/", "https://github.com/username/reponame");
    tests.add("https://github.com/UserName/RepoName/", "https://github.com/UserName/RepoName");

    tests.add("https://github.com/RepoName/", null);
    tests.add("git@github.com:user/", null);
    tests.add("https://user:pass@github.com/", null);

    runTestCase(tests, new Convertor<String, String>() {
      @Override
      @Nullable
      public String convert(String in) {
        return makeGithubRepoUrlFromRemoteUrl(in, "https://github.com");
      }
    });
  }
}
