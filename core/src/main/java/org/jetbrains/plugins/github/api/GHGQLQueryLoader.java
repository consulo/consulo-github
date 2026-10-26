// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api;

import consulo.http.graphql.CachingGraphQLQueryLoader;
import consulo.http.graphql.GraphQLQueryLoader;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class GHGQLQueryLoader implements GraphQLQueryLoader {
    public static final GHGQLQueryLoader INSTANCE = new GHGQLQueryLoader();

    private final CachingGraphQLQueryLoader myDelegate =
        new CachingGraphQLQueryLoader(path -> GHGQLQueryLoader.class.getClassLoader().getResourceAsStream(path));

    private GHGQLQueryLoader() {
    }

    @Override
    public String loadQuery(String queryPath) throws IOException {
        return myDelegate.loadQuery(queryPath);
    }

    public static List<String> findAllQueries() throws IOException, URISyntaxException {
        URL url = GHGQLQueryLoader.class.getClassLoader().getResource("graphql/query");
        if (url == null) {
            return List.of();
        }

        URI uri = url.toURI();
        if (url.getProtocol().contains("jar")) {
            try (FileSystem jarFs = FileSystems.newFileSystem(uri, Map.of())) {
                return getQueries(jarFs.getPath("graphql/query"));
            }
        }
        else {
            return getQueries(Paths.get(uri));
        }
    }

    private static List<String> getQueries(Path directory) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(it -> !Files.isDirectory(it))
                .map(it -> "graphql/query/" + directory.relativize(it).toString().replace('\\', '/'))
                .collect(Collectors.toList());
        }
    }
}
