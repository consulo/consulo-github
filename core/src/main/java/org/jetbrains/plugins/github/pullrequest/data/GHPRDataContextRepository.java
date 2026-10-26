// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.http.client.HttpStatusErrorException;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.review.icon.AsyncImageIconsProvider;
import consulo.review.icon.AsyncImageLoader;
import consulo.review.icon.CachingIconsProvider;
import consulo.review.icon.IconsProvider;
import consulo.review.util.FutureUtil;
import consulo.ui.image.Image;
import consulo.ui.image.ImageEffects;
import consulo.ui.messageList.AsyncHtmlImageLoader;
import git4idea.remote.GitRemoteUrlCoordinates;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.plugins.github.api.GHGQLRequests;
import org.jetbrains.plugins.github.api.GHRepositoryCoordinates;
import org.jetbrains.plugins.github.api.GHRepositoryPath;
import org.jetbrains.plugins.github.api.GithubApiContentHelper;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestOperation;
import org.jetbrains.plugins.github.api.GithubApiRequests;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GHReactionContent;
import org.jetbrains.plugins.github.api.data.GHRepository;
import org.jetbrains.plugins.github.api.data.GHUser;
import org.jetbrains.plugins.github.api.data.GithubAuthenticatedUser;
import org.jetbrains.plugins.github.authentication.accounts.GHCachingAccountInformationProvider;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRMentionableUsersProviderImpl;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRChangesServiceImpl;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCommentServiceImpl;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRCreationServiceImpl;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRDetailsServiceImpl;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRFilesServiceImpl;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRPersistentInteractionState;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRRepositoryDataServiceImpl;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRReviewServiceImpl;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityServiceImpl;
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRTimelineServiceImpl;
import org.jetbrains.plugins.github.util.CachingGHUserAvatarLoader;
import org.jetbrains.plugins.github.util.GithubSharedProjectSettings;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public class GHPRDataContextRepository implements Disposable {
    private static final Logger LOG = Logger.getInstance(GHPRDataContextRepository.class);

    private static final ExecutorService IMAGES_DISPATCHER = AppExecutorUtil.createBoundedApplicationPoolExecutor("GitHub Images Loader", 4);

    private final Project myProject;
    private final AccountManager myAccountManager;

    private final Map<GHRepositoryCoordinates, GHPRDataContext> myCache = new ConcurrentHashMap<>();
    private CompletableFuture<?> myCacheGuard = CompletableFuture.completedFuture(null);

    @Inject
    public GHPRDataContextRepository(Project project, AccountManager accountManager) {
        myProject = project;
        myAccountManager = accountManager;
    }

    public static GHPRDataContextRepository getInstance(Project project) {
        return project.getInstance(GHPRDataContextRepository.class);
    }

    public CompletableFuture<GHPRDataContext> getContext(GHRepositoryCoordinates repository,
                                                         GitRemoteUrlCoordinates remote,
                                                         Account account,
                                                         GithubApiRequestExecutor requestExecutor) {
        return withLock(() -> {
            GHPRDataContext existing = myCache.get(repository);
            if (existing != null) {
                return CompletableFuture.completedFuture(existing);
            }
            Disposable contextScope = Disposable.newDisposable(GHPRDataContext.class.getName());
            Disposer.register(this, contextScope);
            return getContextAsync(contextScope, account, requestExecutor, repository, remote).whenComplete((context, error) -> {
                if (error != null) {
                    Disposer.dispose(contextScope);
                    if (!FutureUtil.isCancellation(error)) {
                        LOG.info("Error occurred while creating data context", FutureUtil.unwrap(error));
                    }
                }
                else {
                    myCache.put(repository, context);
                }
            });
        });
    }

    public CompletableFuture<?> clearContext(GHRepositoryCoordinates repository) {
        return withLock(() -> {
            GHPRDataContext context = myCache.remove(repository);
            if (context != null) {
                Disposer.dispose(context.getScope());
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    private synchronized <T> CompletableFuture<T> withLock(Supplier<CompletableFuture<T>> action) {
        CompletableFuture<T> result = myCacheGuard.handle((ignored, error) -> null).thenCompose(ignored -> action.get());
        myCacheGuard = result;
        return result;
    }

    private CompletableFuture<GHPRDataContext> getContextAsync(Disposable cs,
                                                               Account account,
                                                               GithubApiRequestExecutor requestExecutor,
                                                               GHRepositoryCoordinates parsedRepositoryCoordinates,
                                                               GitRemoteUrlCoordinates remoteCoordinates) {
        GithubServerPath server = GithubAccountType.toServerPath(myAccountManager.getServerUrl(account));

        CompletableFuture<GithubAuthenticatedUser> accountDetailsRequest =
            GHCachingAccountInformationProvider.getInstance().loadInformation(requestExecutor, account);
        CompletableFuture<GHUser> ghostUserRequest = requestExecutor.execute(GHGQLRequests.User.find(server, "ghost"))
            .thenApply(Objects::requireNonNull)
            .exceptionally(e -> {
                Throwable cause = FutureUtil.unwrap(e);
                if (cause instanceof CancellationException cancellation) {
                    throw cancellation;
                }
                if (cause instanceof HttpStatusErrorException) {
                    // github.com is always expected to have a ghost user, but any enterprise server may not
                    if (server.isGithubDotCom()) {
                        throw new IllegalStateException("Couldn't load ghost user details");
                    }
                }
                return GHUser.FAKE_GHOST;
            });
        CompletableFuture<GHRepository> repositoryInfoRequest = requestExecutor.execute(
                GHGQLRequests.Repo.find(new GHRepositoryCoordinates(server, parsedRepositoryCoordinates.getRepositoryPath())))
            .thenApply(repositoryInfo -> {
                if (repositoryInfo == null) {
                    throw new IllegalArgumentException("Repository " + parsedRepositoryCoordinates.getRepositoryPath() + " does not exist at "
                        + server + " or you don't have access.");
                }
                return repositoryInfo;
            });

        return CompletableFuture.allOf(accountDetailsRequest, ghostUserRequest, repositoryInfoRequest).thenApply(ignored -> {
            GithubAuthenticatedUser accountDetails = accountDetailsRequest.join();
            GHUser ghostUserDetails = ghostUserRequest.join();
            GHRepository repositoryInfo = repositoryInfoRequest.join();

            GHUser currentUser = new GHUser(accountDetails.getNodeId(), accountDetails.getLogin(), accountDetails.getHtmlUrl(),
                Objects.requireNonNull(accountDetails.getAvatarUrl()), accountDetails.getName());

            // Image loaders
            AsyncHtmlImageLoader imageLoader = (baseUrl, src) -> CompletableFuture.supplyAsync(() -> null, IMAGES_DISPATCHER)
                .thenCompose(it -> requestExecutor.execute(GithubApiRequests.getBytes(src)
                    .withOperation(GithubApiRequestOperation.RestGetImage)
                    .withOperationName("get image")))
                .thenApply(bytes -> {
                    try {
                        return GithubApiContentHelper.loadImage(bytes);
                    }
                    catch (Exception e) {
                        throw new CompletionException(e);
                    }
                });
            IconsProvider<String> avatarIconsProvider = new CachingIconsProvider<>(new AsyncImageIconsProvider<>(new AvatarLoader(requestExecutor)));
            GHReactionImageLoader reactionImagesLoader = new GHReactionImageLoader(server, requestExecutor);
            IconsProvider<GHReactionContent> reactionIconsProvider = new CachingIconsProvider<>(new AsyncImageIconsProvider<>(reactionImagesLoader));

            // repository might have been renamed/moved
            GHRepositoryPath apiRepositoryPath = repositoryInfo.getPath();
            GHRepositoryCoordinates apiRepositoryCoordinates = new GHRepositoryCoordinates(server, apiRepositoryPath);

            GHPRRepositoryDataServiceImpl repoDataService = new GHPRRepositoryDataServiceImpl(cs,
                requestExecutor,
                remoteCoordinates, apiRepositoryCoordinates,
                repositoryInfo.getOwner(),
                repositoryInfo.getId(), repositoryInfo.getDefaultBranch(), repositoryInfo.isFork());
            GHPRSecurityServiceImpl securityService = new GHPRSecurityServiceImpl(GithubSharedProjectSettings.getInstance(myProject),
                ghostUserDetails,
                account, currentUser,
                repositoryInfo);
            GHPRDetailsServiceImpl detailsService = new GHPRDetailsServiceImpl(myProject, securityService, requestExecutor, apiRepositoryCoordinates);
            GHPRTimelineServiceImpl timelineService = new GHPRTimelineServiceImpl(requestExecutor, apiRepositoryCoordinates);
            GHPRCommentServiceImpl commentService = new GHPRCommentServiceImpl(requestExecutor, apiRepositoryCoordinates);
            GHPRChangesServiceImpl changesService = new GHPRChangesServiceImpl(myProject, requestExecutor, remoteCoordinates, apiRepositoryCoordinates);
            GHPRReviewServiceImpl reviewService = new GHPRReviewServiceImpl(securityService, requestExecutor, apiRepositoryCoordinates);
            GHPRFilesServiceImpl filesService = new GHPRFilesServiceImpl(requestExecutor, apiRepositoryCoordinates);
            GHReactionsServiceImpl reactionsService = new GHReactionsServiceImpl(requestExecutor, apiRepositoryCoordinates);

            GHPRDataProviderRepositoryImpl dataProviderRepository = new GHPRDataProviderRepositoryImpl(myProject, cs,
                repoDataService,
                detailsService,
                timelineService,
                reviewService,
                filesService,
                commentService,
                changesService);

            GHPRMentionableUsersProviderImpl mentionableUsersProvider = new GHPRMentionableUsersProviderImpl(cs, repoDataService);

            GHPRPersistentInteractionState interactionState = GHPRPersistentInteractionState.getInstance(myProject);

            GHPRCreationServiceImpl creationService = new GHPRCreationServiceImpl(requestExecutor, repoDataService);
            if (Disposer.isDisposed(cs)) {
                throw new CancellationException();
            }
            return new GHPRDataContext(cs, requestExecutor, dataProviderRepository,
                securityService, repoDataService, creationService, detailsService, reactionsService,
                imageLoader, avatarIconsProvider, mentionableUsersProvider, reactionIconsProvider,
                interactionState);
        });
    }

    private static class AvatarLoader implements AsyncImageLoader<String> {
        private final GithubApiRequestExecutor myRequestExecutor;
        private final CachingGHUserAvatarLoader myAvatarsLoader = CachingGHUserAvatarLoader.getInstance();

        AvatarLoader(GithubApiRequestExecutor requestExecutor) {
            myRequestExecutor = requestExecutor;
        }

        @Override
        public CompletableFuture<@Nullable Image> load(String key) {
            return myAvatarsLoader.loadAvatar(myRequestExecutor, key);
        }

        @Override
        public Image createBaseIcon(@Nullable String key, int iconSize) {
            return ImageEffects.resize(PlatformIconGroup.actionsLoginavatar(), iconSize, iconSize);
        }
    }

    @Override
    public void dispose() {
        myCache.clear();
    }
}
