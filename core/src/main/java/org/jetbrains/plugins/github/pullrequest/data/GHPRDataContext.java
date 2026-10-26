// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
/*
 * Copyright 2013-2026 consulo.io
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
package org.jetbrains.plugins.github.pullrequest.data;

import consulo.disposer.Disposable;
import consulo.http.graphql.GraphQLRequestPagination;
import consulo.review.icon.IconsProvider;
import consulo.review.util.ComputableSequence;
import consulo.review.util.SequenceItem;
import consulo.ui.messageList.AsyncHtmlImageLoader;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.data.GHReactionContent;
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort;
import org.jetbrains.plugins.github.api.data.request.search.GithubIssueSearchType;
import org.jetbrains.plugins.github.api.util.GithubApiSearchQueryBuilder;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRMentionableUsersProvider;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCreationService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRDetailsService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRPersistentInteractionState;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataService;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class GHPRDataContext {
    private static final int PR_PAGE_SIZE = 50;

    private final Disposable myScope;
    private final GithubApiRequestExecutor myRequestExecutor;
    private final GHPRDataProviderRepository myDataProviderRepository;
    private final GHPRSecurityService mySecurityService;
    private final GHPRRepositoryDataService myRepositoryDataService;
    private final GHPRCreationService myCreationService;
    private final GHPRDetailsService myDetailsService;
    private final GHReactionsService myReactionsService;
    private final AsyncHtmlImageLoader myHtmlImageLoader;
    private final IconsProvider<String> myAvatarIconsProvider;
    private final GHPRMentionableUsersProvider myMentionableUsersProvider;
    private final IconsProvider<GHReactionContent> myReactionIconsProvider;
    private final GHPRPersistentInteractionState myInteractionState;

    GHPRDataContext(Disposable scope,
                    GithubApiRequestExecutor requestExecutor,
                    GHPRDataProviderRepository dataProviderRepository,
                    GHPRSecurityService securityService,
                    GHPRRepositoryDataService repositoryDataService,
                    GHPRCreationService creationService,
                    GHPRDetailsService detailsService,
                    GHReactionsService reactionsService,
                    AsyncHtmlImageLoader htmlImageLoader,
                    IconsProvider<String> avatarIconsProvider,
                    GHPRMentionableUsersProvider mentionableUsersProvider,
                    IconsProvider<GHReactionContent> reactionIconsProvider,
                    GHPRPersistentInteractionState interactionState) {
        myScope = scope;
        myRequestExecutor = requestExecutor;
        myDataProviderRepository = dataProviderRepository;
        mySecurityService = securityService;
        myRepositoryDataService = repositoryDataService;
        myCreationService = creationService;
        myDetailsService = detailsService;
        myReactionsService = reactionsService;
        myHtmlImageLoader = htmlImageLoader;
        myAvatarIconsProvider = avatarIconsProvider;
        myMentionableUsersProvider = mentionableUsersProvider;
        myReactionIconsProvider = reactionIconsProvider;
        myInteractionState = interactionState;
    }

    public Disposable getScope() {
        return myScope;
    }

    public GHPRDataProviderRepository getDataProviderRepository() {
        return myDataProviderRepository;
    }

    public GHPRSecurityService getSecurityService() {
        return mySecurityService;
    }

    public GHPRRepositoryDataService getRepositoryDataService() {
        return myRepositoryDataService;
    }

    public GHPRCreationService getCreationService() {
        return myCreationService;
    }

    public GHPRDetailsService getDetailsService() {
        return myDetailsService;
    }

    public GHReactionsService getReactionsService() {
        return myReactionsService;
    }

    public AsyncHtmlImageLoader getHtmlImageLoader() {
        return myHtmlImageLoader;
    }

    public IconsProvider<String> getAvatarIconsProvider() {
        return myAvatarIconsProvider;
    }

    public GHPRMentionableUsersProvider getMentionableUsersProvider() {
        return myMentionableUsersProvider;
    }

    public IconsProvider<GHReactionContent> getReactionIconsProvider() {
        return myReactionIconsProvider;
    }

    public GHPRPersistentInteractionState getInteractionState() {
        return myInteractionState;
    }

    public ComputableSequence<SequenceItem<List<GHPullRequestShort>>> getListLoader(@Nullable GHPRSearchQuery searchQuery) {
        GHRepositoryCoordinates repository = myRepositoryDataService.getRepositoryCoordinates();
        String query = buildPRSearchQuery(repository.getRepositoryPath(), searchQuery);
        return ComputableSequence.byPointer(new GraphQLRequestPagination(PR_PAGE_SIZE), pagination ->
            myRequestExecutor.execute(GHGQLRequests.PullRequest.search(repository.getServerPath(), query, pagination)).thenApply(response -> {
                GraphQLRequestPagination nextPage = response.getPageInfo().getHasNextPage()
                    ? new GraphQLRequestPagination(response.getPageInfo().getEndCursor(), PR_PAGE_SIZE)
                    : null;
                return Pair.create(response.getNodes(), nextPage);
            }));
    }

    private static String buildPRSearchQuery(GHRepositoryPath repoPath, @Nullable GHPRSearchQuery searchQuery) {
        return GithubApiSearchQueryBuilder.searchQuery(builder -> {
            builder.term(GHPRSearchQuery.QualifierName.type.createTerm(GithubIssueSearchType.pr.name()));
            builder.term(GHPRSearchQuery.QualifierName.repo.createTerm(repoPath.toString()));
            if (searchQuery != null) {
                for (GHPRSearchQuery.Term<?> term : searchQuery.getTerms()) {
                    builder.term(term);
                }
            }
        });
    }
}
