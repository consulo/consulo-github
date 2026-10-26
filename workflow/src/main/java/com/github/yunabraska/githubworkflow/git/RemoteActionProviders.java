/*
 * Contains code from github-workflow-plugin (https://github.com/YunaBraska/github-workflow-plugin),
 * licensed under the Apache License, Version 2.0. Modified by consulo.io.
 */
package com.github.yunabraska.githubworkflow.git;

import consulo.accountManagement.Account;
import consulo.accountManagement.AccountManager;
import consulo.accountManagement.AccountType;
import consulo.application.Application;
import consulo.application.progress.EmptyProgressIndicator;
import consulo.github.workflow.localize.GitHubWorkflowLocalize;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.ui.annotation.RequiredUIAccess;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorFactory;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubBranch;
import org.jetbrains.plugins.github.api.data.GithubRepo;
import org.jetbrains.plugins.github.api.data.GithubResponsePage;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountType;
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;

public class RemoteActionProviders {
    private static final Logger LOG = Logger.getInstance(RemoteActionProviders.class);

    public record Resolution(
        String usesValue,
        String name,
        String downloadUrl,
        String githubUrl,
        String content,
        boolean action,
        List<String> refs
    ) {
    }

    public static Optional<Resolution> resolve(@Nullable String usesValue) {
        return firstPresent(usesValue, connection -> resolve(connection, usesValue));
    }

    public static boolean hasAccountFor(@Nullable String usesValue) {
        return !connections(usesValue).isEmpty();
    }

    @RequiredUIAccess
    public static CompletableFuture<@Nullable Account> requestLogin(@Nullable Project project) {
        AccountManager accountManager = Application.get().getInstance(AccountManager.class);
        AccountType<?> accountType = accountManager.findAccountType(GithubAccountType.ID);
        return accountManager.requestLogin(accountType, null, project, null);
    }

    public static List<String> latestRefs(@Nullable String usesBase, int limit) {
        if (limit < 1) {
            return List.of();
        }
        return firstUseful(
            usesBase,
            connection -> RemoteUses.parseBase(connection.server(), usesBase)
                .map(uses -> latestRefs(connection, uses, limit))
                .orElseGet(List::of),
            refs -> !refs.isEmpty(),
            List.of()
        );
    }

    public static Map<String, String> searchUses(@Nullable String usesPrefix, int limit) {
        if (limit < 1) {
            return Map.of();
        }
        return firstUseful(
            usesPrefix,
            connection -> RemoteUsesPrefix.parse(connection.server(), usesPrefix)
                .map(prefix -> searchUses(connection, prefix, limit))
                .orElseGet(Map::of),
            items -> !items.isEmpty(),
            Map.of()
        );
    }

    private static <T> Optional<T> firstPresent(@Nullable String value, Function<Connection, Optional<T>> resolver) {
        return connections(value).stream()
            .map(resolver)
            .flatMap(Optional::stream)
            .findFirst();
    }

    private static <T> T firstUseful(@Nullable String value, Function<Connection, T> resolver, Predicate<T> useful, T empty) {
        return connections(value).stream()
            .map(resolver)
            .filter(useful)
            .findFirst()
            .orElse(empty);
    }

    private static List<Connection> connections(@Nullable String value) {
        if (value == null || value.isBlank() || value.startsWith(".")) {
            return List.of();
        }
        String trimmed = value.trim();
        boolean explicitServer = trimmed.startsWith("http://") || trimmed.startsWith("https://");
        AccountManager accountManager = Application.get().getInstance(AccountManager.class);
        AccountType<?> accountType = accountManager.findAccountType(GithubAccountType.ID);
        if (accountType == null) {
            return List.of();
        }
        Map<String, Account> accounts = new LinkedHashMap<>();
        List<Project> projects = new ArrayList<>();
        projects.add(null);
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (!project.isDisposed()) {
                projects.add(project);
            }
        }
        for (Project project : projects) {
            Account active = accountManager.getActiveAccount(accountType, project);
            if (active != null) {
                accounts.putIfAbsent(active.getId(), active);
            }
            for (Account account : accountManager.getAccounts(accountType, project)) {
                accounts.putIfAbsent(account.getId(), account);
            }
        }
        List<Connection> result = new ArrayList<>();
        for (Account account : accounts.values()) {
            GithubServerPath server = GithubAccountType.toServerPath(accountManager.getServerUrl(account));
            if (explicitServer && !trimmed.startsWith(server.toUrl() + "/")) {
                continue;
            }
            result.add(new Connection(account, server));
        }
        result.sort(Comparator
            .comparingInt((Connection connection) -> connection.account().isAuthFailed() ? 1 : 0)
            .thenComparingInt(connection -> connection.server().isGithubDotCom() ? 0 : 1));
        return result;
    }

    private static Optional<Resolution> resolve(Connection connection, @Nullable String usesValue) {
        return RemoteUses.parse(connection.server(), usesValue).flatMap(remoteUses -> resolve(connection, remoteUses));
    }

    private static Optional<Resolution> resolve(Connection connection, RemoteUses uses) {
        for (String metadataPath : metadataPaths(uses)) {
            Optional<ContentResponse> content = getContent(connection, uses.owner(), uses.repo(), metadataPath, uses.ref());
            if (content.isPresent()) {
                List<String> refs = listRefs(connection, uses.owner(), uses.repo());
                return Optional.of(new Resolution(
                    uses.usesValue(),
                    uses.owner() + "/" + uses.repo(),
                    content.get().downloadUrl(),
                    htmlUrl(connection.server(), uses, metadataPath),
                    content.get().content(),
                    !isWorkflowPath(metadataPath),
                    refs
                ));
            }
        }
        return Optional.empty();
    }

    private static List<String> metadataPaths(RemoteUses uses) {
        if (isWorkflowPath(uses.path())) {
            return List.of(uses.path());
        }
        String base = uses.path().isBlank() ? "" : uses.path() + "/";
        return List.of(base + "action.yml", base + "action.yaml");
    }

    private static boolean isWorkflowPath(String path) {
        String normalized = path.replace('\\', '/');
        return normalized.contains(".github/workflows/")
            && (normalized.endsWith(".yml") || normalized.endsWith(".yaml"));
    }

    private static Optional<ContentResponse> getContent(Connection connection, String owner, String repo, String path, String ref) {
        return execute(connection, GitHubWorkflowApiRequests.content(connection.server(), owner, repo, path, ref, isWorkflowPath(path)))
            .flatMap(RemoteActionProviders::contentFrom);
    }

    private static List<String> listRefs(Connection connection, String owner, String repo) {
        Set<String> result = new LinkedHashSet<>();
        execute(connection, GitHubWorkflowApiRequests.branches(connection.server(), owner, repo, 0)).ifPresent(page -> names(page).forEach(result::add));
        execute(connection, GitHubWorkflowApiRequests.tags(connection.server(), owner, repo, 0)).ifPresent(page -> names(page).forEach(result::add));
        return List.copyOf(result);
    }

    private static List<String> latestRefs(Connection connection, RemoteUses uses, int limit) {
        Set<String> result = new LinkedHashSet<>();
        execute(connection, GitHubWorkflowApiRequests.tags(connection.server(), uses.owner(), uses.repo(), limit)).ifPresent(page -> names(page).forEach(result::add));
        if (result.size() < limit) {
            execute(connection, GitHubWorkflowApiRequests.branches(connection.server(), uses.owner(), uses.repo(), limit)).ifPresent(page -> names(page).forEach(result::add));
        }
        return result.stream().limit(limit).toList();
    }

    private static Map<String, String> searchUses(Connection connection, RemoteUsesPrefix prefix, int limit) {
        Map<String, String> result = new LinkedHashMap<>();
        execute(connection, GitHubWorkflowApiRequests.userRepos(connection.server(), prefix.owner(), limit))
            .ifPresent(page -> repoCompletions(page, prefix, limit).forEach(result::putIfAbsent));
        if (result.size() < limit) {
            execute(connection, GitHubWorkflowApiRequests.orgRepos(connection.server(), prefix.owner(), limit))
                .ifPresent(page -> repoCompletions(page, prefix, limit).forEach(result::putIfAbsent));
        }
        Map<String, String> limited = new LinkedHashMap<>();
        result.entrySet().stream().limit(limit).forEach(entry -> limited.put(entry.getKey(), entry.getValue()));
        return limited;
    }

    private static <T> Optional<T> execute(Connection connection, GithubApiRequest<T> request) {
        try {
            GithubApiRequestExecutor executor = GithubApiRequestExecutorFactory.getInstance().create(connection.account(), connection.server());
            return Optional.ofNullable(executor.execute(new EmptyProgressIndicator(), request));
        }
        catch (GithubStatusCodeException e) {
            return Optional.empty();
        }
        catch (IOException e) {
            LOG.warn("Remote request failed [" + request.getUrl() + "]", e);
            return Optional.empty();
        }
        catch (RuntimeException e) {
            LOG.warn("Remote response failed [" + request.getUrl() + "]", e);
            return Optional.empty();
        }
    }

    private static Optional<ContentResponse> contentFrom(GitHubWorkflowContent response) {
        String rawContent = response.getContent();
        if (rawContent == null || rawContent.isBlank()) {
            return Optional.empty();
        }
        String content = new String(Base64.getMimeDecoder().decode(rawContent), StandardCharsets.UTF_8);
        String downloadUrl = Optional.ofNullable(response.getDownloadUrl()).filter(value -> !value.isBlank()).orElse(response.getPath());
        return Optional.of(new ContentResponse(content, downloadUrl));
    }

    private static List<String> names(GithubResponsePage<GithubBranch> page) {
        return page.getItems().stream()
            .map(GithubBranch::getName)
            .filter(RemoteActionProviders::hasText)
            .toList();
    }

    private static Map<String, String> repoCompletions(GithubResponsePage<GithubRepo> page, RemoteUsesPrefix prefix, int limit) {
        Map<String, String> result = new LinkedHashMap<>();
        for (GithubRepo repo : page.getItems()) {
            String name = repo.getName();
            if (hasText(name) && name.startsWith(prefix.repoPrefix())) {
                String fullName = hasText(repo.getFullName()) ? repo.getFullName() : prefix.owner() + "/" + name;
                String description = hasText(repo.getDescription()) ? repo.getDescription() : GitHubWorkflowLocalize.completionRemoteRepository().get();
                result.putIfAbsent(fullName, description);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private static String htmlUrl(GithubServerPath server, RemoteUses uses, String metadataPath) {
        String base = server.toUrl() + "/" + uses.owner() + "/" + uses.repo();
        if (isWorkflowPath(metadataPath)) {
            return base + "/blob/" + uses.ref() + "/" + metadataPath;
        }
        String actionPath = metadataPath.endsWith("/action.yml")
            ? metadataPath.substring(0, metadataPath.length() - "/action.yml".length())
            : metadataPath.endsWith("/action.yaml")
            ? metadataPath.substring(0, metadataPath.length() - "/action.yaml".length())
            : "";
        String suffix = actionPath.isBlank() ? "" : "/" + actionPath;
        return base + "/tree/" + uses.ref() + suffix + "#readme";
    }

    private record Connection(Account account, GithubServerPath server) {
    }

    private record ContentResponse(String content, String downloadUrl) {
    }

    private record RemoteUses(String usesValue, String owner, String repo, String path, String ref) {
        static Optional<RemoteUses> parse(GithubServerPath server, @Nullable String value) {
            if (value == null || value.isBlank() || value.startsWith(".")) {
                return Optional.empty();
            }
            String stripped = stripServerPrefix(server, value.trim()).orElse(null);
            if (stripped == null) {
                return Optional.empty();
            }
            int atIndex = stripped.lastIndexOf('@');
            if (atIndex < 0 || atIndex == stripped.length() - 1) {
                return Optional.empty();
            }
            String path = stripped.substring(0, atIndex);
            String ref = stripped.substring(atIndex + 1);
            String[] parts = path.split("/", 3);
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new RemoteUses(value.trim(), parts[0], parts[1], parts.length == 3 ? parts[2] : "", ref));
        }

        static Optional<RemoteUses> parseBase(GithubServerPath server, @Nullable String value) {
            if (value == null || value.isBlank() || value.startsWith(".")) {
                return Optional.empty();
            }
            String stripped = stripServerPrefix(server, value.trim()).orElse(null);
            if (stripped == null) {
                return Optional.empty();
            }
            int atIndex = stripped.lastIndexOf('@');
            String path = atIndex < 0 ? stripped : stripped.substring(0, atIndex);
            String[] parts = path.split("/", 3);
            if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new RemoteUses(value.trim(), parts[0], parts[1], parts.length == 3 ? parts[2] : "", ""));
        }

        private static Optional<String> stripServerPrefix(GithubServerPath server, String value) {
            if (value.startsWith("http://") || value.startsWith("https://")) {
                String prefix = server.toUrl() + "/";
                return value.startsWith(prefix) ? Optional.of(value.substring(prefix.length())) : Optional.empty();
            }
            return Optional.of(value);
        }
    }

    private record RemoteUsesPrefix(String owner, String repoPrefix) {
        static Optional<RemoteUsesPrefix> parse(GithubServerPath server, @Nullable String value) {
            if (value == null || value.isBlank() || value.startsWith(".") || value.contains("@")) {
                return Optional.empty();
            }
            String stripped = RemoteUses.stripServerPrefix(server, value.trim()).orElse(null);
            if (stripped == null) {
                return Optional.empty();
            }
            String[] parts = stripped.split("/", 3);
            if (parts.length < 2 || parts[0].isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new RemoteUsesPrefix(parts[0], parts[1]));
        }
    }

    private RemoteActionProviders() {
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
