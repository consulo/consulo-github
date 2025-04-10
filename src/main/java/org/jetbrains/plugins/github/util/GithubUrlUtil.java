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
package org.jetbrains.plugins.github.util;

import consulo.util.lang.StringUtil;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubFullPath;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Aleksey Pivovarov
 */
public class GithubUrlUtil {
    @Nonnull
    public static String removeProtocolPrefix(String url) {
        int index = url.indexOf('@');
        if (index != -1) {
            return url.substring(index + 1).replace(':', '/');
        }
        index = url.indexOf("://");
        if (index != -1) {
            return url.substring(index + 3);
        }
        return url;
    }

    @Nonnull
    public static String removeTrailingSlash(@Nonnull String s) {
        if (s.endsWith("/")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    @Nonnull
    public static String getApiUrl(@Nonnull String urlFromSettings) {
        return "https://" + getApiUrlWithoutProtocol(urlFromSettings);
    }

    /**
     * E.g.: https://api.github.com
     * https://my.company.url/api/v3
     */
    @Nonnull
    public static String getApiUrl() {
        return getApiUrl(GithubSettings.getInstance().getHost());
    }

    /**
     * Returns the "host" part of Git URLs.
     * E.g.: https://github.com
     * https://my.company.url
     * Note: there is no trailing slash in the returned url.
     */
    @Nonnull
    public static String getGitHost() {
        return "https://" + getGitHostWithoutProtocol();
    }

    /**
     * E.g.: github.com
     * my.company.url
     */
    @Nonnull
    public static String getGitHostWithoutProtocol() {
        return removeTrailingSlash(removeProtocolPrefix(GithubSettings.getInstance().getHost()));
    }

    /*
         All API access is over HTTPS, and accessed from the api.github.com domain
         (or through yourdomain.com/api/v3/ for enterprise).
         http://developer.github.com/api/v3/
        */
    @Nonnull
    public static String getApiUrlWithoutProtocol(@Nonnull String urlFromSettings) {
        String url = removeTrailingSlash(removeProtocolPrefix(urlFromSettings.toLowerCase()));
        final String API_PREFIX = "api.";
        final String ENTERPRISE_API_SUFFIX = "/api/v3";

        if (url.equals(GithubApiUtil.DEFAULT_GITHUB_HOST)) {
            return API_PREFIX + url;
        }
        else if (url.equals(API_PREFIX + GithubApiUtil.DEFAULT_GITHUB_HOST)) {
            return url;
        }
        else if (url.endsWith(ENTERPRISE_API_SUFFIX)) {
            return url;
        }
        else {
            return url + ENTERPRISE_API_SUFFIX;
        }
    }

    public static boolean isGithubUrl(@Nonnull String url) {
        return isGithubUrl(url, GithubSettings.getInstance().getHost());
    }

    public static boolean isGithubUrl(@Nonnull String url, @Nonnull String host) {
        url = removeProtocolPrefix(url);
        if (StringUtil.startsWithIgnoreCase(url, host)) {
            return url.length() <= host.length() || ":/".indexOf(url.charAt(host.length())) != -1;
        }
        return false;
    }

    /**
     * assumed isGithubUrl(remoteUrl)
     * <p/>
     * git@github.com:user/repo.git -> user/repo
     */
    @Nullable
    public static GithubFullPath getUserAndRepositoryFromRemoteUrl(@Nonnull String remoteUrl) {
        remoteUrl = removeProtocolPrefix(removeEndingDotGit(remoteUrl));
        int index1 = remoteUrl.lastIndexOf('/');
        if (index1 == -1) {
            return null;
        }
        String url = remoteUrl.substring(0, index1);
        int index2 = Math.max(url.lastIndexOf('/'), url.lastIndexOf(':'));
        if (index2 == -1) {
            return null;
        }
        final String username = remoteUrl.substring(index2 + 1, index1);
        final String reponame = remoteUrl.substring(index1 + 1);
        if (username.isEmpty() || reponame.isEmpty()) {
            return null;
        }
        return new GithubFullPath(username, reponame);
    }

    /**
     * assumed isGithubUrl(remoteUrl)
     * <p/>
     * git@github.com:user/repo -> https://github.com/user/repo
     */
    @Nullable
    public static String makeGithubRepoUrlFromRemoteUrl(@Nonnull String remoteUrl) {
        return makeGithubRepoUrlFromRemoteUrl(remoteUrl, getGitHost());
    }

    @Nullable
    public static String makeGithubRepoUrlFromRemoteUrl(@Nonnull String remoteUrl, @Nonnull String host) {
        GithubFullPath repo = getUserAndRepositoryFromRemoteUrl(remoteUrl);
        if (repo == null) {
            return null;
        }
        return host + '/' + repo.getUser() + '/' + repo.getRepository();
    }

    @Nonnull
    private static String removeEndingDotGit(@Nonnull String url) {
        url = removeTrailingSlash(url);
        final String DOT_GIT = ".git";
        if (url.endsWith(DOT_GIT)) {
            return url.substring(0, url.length() - DOT_GIT.length());
        }
        return url;
    }
}
