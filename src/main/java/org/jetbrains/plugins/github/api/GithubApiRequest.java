// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.api;

import consulo.http.graphql.dto.GraphQLRequestDTO;
import consulo.http.graphql.dto.GraphQLResponseDTO;
import consulo.http.page.LinkHttpHeaderValue;
import consulo.util.lang.Pair;
import org.jetbrains.plugins.github.api.data.GithubResponsePage;
import org.jetbrains.plugins.github.api.data.GithubSearchResult;
import org.jetbrains.plugins.github.api.data.graphql.GHGQLError;
import org.jetbrains.plugins.github.api.data.graphql.GHGQLRateLimit;
import org.jetbrains.plugins.github.exceptions.GithubAuthenticationException;
import org.jetbrains.plugins.github.exceptions.GithubConfusingException;
import org.jetbrains.plugins.github.exceptions.GithubJsonException;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an API request with strictly defined response type
 */
public abstract class GithubApiRequest<T> {
    private final String myUrl;
    private GithubApiRequestOperation myOperation = GithubApiRequestOperation.Unknown;
    private @Nullable String myOperationName;

    protected final Map<String, String> headers = new HashMap<>();

    protected GithubApiRequest(String url) {
        myUrl = url;
    }

    public String getUrl() {
        return myUrl;
    }

    public GithubApiRequestOperation getOperation() {
        return myOperation;
    }

    public void setOperation(GithubApiRequestOperation operation) {
        myOperation = operation;
    }

    public @Nullable String getOperationName() {
        return myOperationName;
    }

    public abstract @Nullable String getAcceptMimeType();

    public Map<String, String> getAdditionalHeaders() {
        return headers;
    }

    public abstract T extractResult(GithubApiResponse response) throws IOException;

    public GithubApiRequest<T> withOperation(GithubApiRequestOperation operation) {
        myOperation = operation;
        return this;
    }

    public GithubApiRequest<T> withOperationName(String name) {
        myOperationName = name;
        return this;
    }

    public abstract static class Get<T> extends GithubApiRequest<T> {
        private final @Nullable String myAcceptMimeType;

        protected Get(String url) {
            this(url, null);
        }

        protected Get(String url, @Nullable String acceptMimeType) {
            super(url);
            myAcceptMimeType = acceptMimeType;
        }

        @Override
        public @Nullable String getAcceptMimeType() {
            return myAcceptMimeType;
        }

        public static <T> Get<T> json(String url, Class<T> clazz) {
            return json(url, clazz, null);
        }

        public static <T> Get<T> json(String url, Class<T> clazz, @Nullable String acceptMimeType) {
            return new Json<>(url, clazz, acceptMimeType);
        }

        public static <T> Get<GithubResponsePage<T>> jsonPage(String url, Class<T> clazz) {
            return jsonPage(url, clazz, null);
        }

        public static <T> Get<GithubResponsePage<T>> jsonPage(String url, Class<T> clazz, @Nullable String acceptMimeType) {
            return new JsonPage<>(url, clazz, acceptMimeType);
        }

        public static <T> Get<GithubResponsePage<T>> jsonSearchPage(String url, Class<T> clazz) {
            return jsonSearchPage(url, clazz, null);
        }

        public static <T> Get<GithubResponsePage<T>> jsonSearchPage(String url, Class<T> clazz, @Nullable String acceptMimeType) {
            return new JsonSearchPage<>(url, clazz, acceptMimeType);
        }

        public abstract static class Optional<T> extends Get<@Nullable T> {
            protected Optional(String url) {
                this(url, null);
            }

            protected Optional(String url, @Nullable String acceptMimeType) {
                super(url, acceptMimeType);
            }

            public static <T> Optional<T> json(String url, Class<T> clazz) {
                return json(url, clazz, null);
            }

            public static <T> Optional<T> json(String url, Class<T> clazz, @Nullable String acceptMimeType) {
                return new Json<>(url, clazz, acceptMimeType);
            }

            public static class Json<T> extends Optional<T> {
                private final Class<T> myClazz;

                public Json(String url, Class<T> clazz) {
                    this(url, clazz, GithubApiContentHelper.V3_JSON_MIME_TYPE);
                }

                public Json(String url, Class<T> clazz, @Nullable String acceptMimeType) {
                    super(url, acceptMimeType);
                    myClazz = clazz;
                }

                @Override
                public T extractResult(GithubApiResponse response) throws IOException {
                    return parseJsonObject(response, myClazz);
                }
            }
        }

        public static class Json<T> extends Get<T> {
            private final Class<T> myClazz;

            public Json(String url, Class<T> clazz) {
                this(url, clazz, GithubApiContentHelper.V3_JSON_MIME_TYPE);
            }

            public Json(String url, Class<T> clazz, @Nullable String acceptMimeType) {
                super(url, acceptMimeType);
                myClazz = clazz;
            }

            @Override
            public T extractResult(GithubApiResponse response) throws IOException {
                return parseJsonObject(response, myClazz);
            }
        }

        public static class JsonMap<T, U> extends Get<Map<T, U>> {
            public JsonMap(String url) {
                this(url, GithubApiContentHelper.V3_JSON_MIME_TYPE);
            }

            public JsonMap(String url, @Nullable String acceptMimeType) {
                super(url, acceptMimeType);
            }

            @Override
            public Map<T, U> extractResult(GithubApiResponse response) throws IOException {
                return parseJsonMap(response);
            }
        }

        public static class JsonPage<T> extends Get<GithubResponsePage<T>> {
            private final Class<T> myClazz;

            public JsonPage(String url, Class<T> clazz) {
                this(url, clazz, GithubApiContentHelper.V3_JSON_MIME_TYPE);
            }

            public JsonPage(String url, Class<T> clazz, @Nullable String acceptMimeType) {
                super(url, acceptMimeType);
                myClazz = clazz;
            }

            @Override
            public GithubResponsePage<T> extractResult(GithubApiResponse response) throws IOException {
                List<T> list = parseJsonList(response, myClazz);
                return new GithubResponsePage<>(list, parseLinkHeader(response));
            }
        }

        public static class JsonSearchPage<T> extends Get<GithubResponsePage<T>> {
            private final Class<T> myClazz;

            public JsonSearchPage(String url, Class<T> clazz) {
                this(url, clazz, GithubApiContentHelper.V3_JSON_MIME_TYPE);
            }

            public JsonSearchPage(String url, Class<T> clazz, @Nullable String acceptMimeType) {
                super(url, acceptMimeType);
                myClazz = clazz;
            }

            @Override
            public GithubResponsePage<T> extractResult(GithubApiResponse response) throws IOException {
                GithubSearchResult<T> page = parseJsonSearchPage(response, myClazz);
                return new GithubResponsePage<>(page.getItems(), parseLinkHeader(response));
            }
        }
    }

    public abstract static class Head<T> extends GithubApiRequest<T> {
        private final @Nullable String myAcceptMimeType;

        protected Head(String url) {
            this(url, null);
        }

        protected Head(String url, @Nullable String acceptMimeType) {
            super(url);
            myAcceptMimeType = acceptMimeType;
        }

        @Override
        public @Nullable String getAcceptMimeType() {
            return myAcceptMimeType;
        }
    }

    public abstract static class WithBody<T> extends GithubApiRequest<T> {
        protected WithBody(String url) {
            super(url);
        }

        public abstract @Nullable String getBody() throws IOException;

        public abstract String getBodyMimeType();
    }

    public abstract static class Post<T> extends WithBody<T> {
        private final String myBodyMimeType;
        private @Nullable String myAcceptMimeType;

        protected Post(String bodyMimeType, String url) {
            this(bodyMimeType, url, null);
        }

        protected Post(String bodyMimeType, String url, @Nullable String acceptMimeType) {
            super(url);
            myBodyMimeType = bodyMimeType;
            myAcceptMimeType = acceptMimeType;
        }

        @Override
        public String getBodyMimeType() {
            return myBodyMimeType;
        }

        @Override
        public @Nullable String getAcceptMimeType() {
            return myAcceptMimeType;
        }

        public void setAcceptMimeType(@Nullable String acceptMimeType) {
            myAcceptMimeType = acceptMimeType;
        }

        public static <T> Post<T> json(String url, Object body, Class<T> clazz) {
            return json(url, body, clazz, null);
        }

        public static <T> Post<T> json(String url, Object body, Class<T> clazz, @Nullable String acceptMimeType) {
            return new Json<>(url, body, clazz, acceptMimeType);
        }

        public static class Json<T> extends Post<T> {
            private final Object myBodyObject;
            private final Class<T> myClazz;

            public Json(String url, Object bodyObject, Class<T> clazz) {
                this(url, bodyObject, clazz, GithubApiContentHelper.V3_JSON_MIME_TYPE);
            }

            public Json(String url, Object bodyObject, Class<T> clazz, @Nullable String acceptMimeType) {
                super(GithubApiContentHelper.JSON_MIME_TYPE, url, acceptMimeType);
                myBodyObject = bodyObject;
                myClazz = clazz;
            }

            @Override
            public String getBody() throws IOException {
                return GithubApiContentHelper.toJson(myBodyObject);
            }

            @Override
            public T extractResult(GithubApiResponse response) throws IOException {
                return parseJsonObject(response, myClazz);
            }
        }

        public abstract static class GQLQuery<T> extends Post<T> {
            private final String myQueryName;
            private final Map<String, @Nullable Object> myVariablesObject;

            protected GQLQuery(String url, String queryName, Map<String, @Nullable Object> variablesObject) {
                super(GithubApiContentHelper.JSON_MIME_TYPE, url);
                myQueryName = queryName;
                myVariablesObject = variablesObject;
            }

            String getQueryName() {
                return myQueryName;
            }

            Map<String, @Nullable Object> getVariablesObject() {
                return myVariablesObject;
            }

            abstract Class<?> getClazz();

            @Override
            public String getBody() throws IOException {
                String query = GHGQLQueryLoader.INSTANCE.loadQuery(myQueryName);
                GraphQLRequestDTO request = new GraphQLRequestDTO(query, myVariablesObject);
                return GithubApiContentHelper.toJson(request, true);
            }

            protected IOException throwException(List<GHGQLError> errors) throws IOException {
                for (GHGQLError error : errors) {
                    if ("INSUFFICIENT_SCOPES".equalsIgnoreCase(error.getType())) {
                        throw new GithubAuthenticationException("Access token has not been granted the required scopes.");
                    }
                }

                if (errors.size() == 1) {
                    throw new GithubConfusingException(errors.getFirst().toString());
                }
                throw new GithubConfusingException(errors.toString());
            }

            public abstract Pair<T, @Nullable GHGQLRateLimit> extractResultWithCost(GithubApiResponse response) throws IOException;

            @Override
            public T extractResult(GithubApiResponse response) throws IOException {
                return extractResultWithCost(response).getFirst();
            }

            public static class Parsed<T> extends GQLQuery<T> {
                private final Class<? extends T> myClazz;

                public Parsed(String url, String requestFilePath, Map<String, @Nullable Object> variablesObject, Class<? extends T> clazz) {
                    super(url, requestFilePath, variablesObject);
                    myClazz = clazz;
                }

                @Override
                Class<?> getClazz() {
                    return myClazz;
                }

                @Override
                public Pair<T, @Nullable GHGQLRateLimit> extractResultWithCost(GithubApiResponse response) throws IOException {
                    Pair<T, @Nullable GHGQLRateLimit> result = parseResponse(response, myClazz);
                    if (result == null) {
                        throw new GithubJsonException("Non-nullable entity is null or entity path is invalid");
                    }
                    return result;
                }
            }

            public abstract static class Traversed<T> extends GQLQuery<T> {
                private final String[] myPathFromData;

                protected Traversed(String url, String requestFilePath, Map<String, @Nullable Object> variablesObject, String[] pathFromData) {
                    super(url, requestFilePath, variablesObject);
                    myPathFromData = pathFromData;
                }

                String[] getPathFromData() {
                    return myPathFromData;
                }
            }

            public static class TraversedParsed<T> extends Traversed<T> {
                private final Class<? extends T> myClazz;

                public TraversedParsed(String url,
                                       String requestFilePath,
                                       Map<String, @Nullable Object> variablesObject,
                                       Class<? extends T> clazz,
                                       String... pathFromData) {
                    super(url, requestFilePath, variablesObject, pathFromData);
                    myClazz = clazz;
                }

                @Override
                Class<?> getClazz() {
                    return myClazz;
                }

                @Override
                public Pair<T, @Nullable GHGQLRateLimit> extractResultWithCost(GithubApiResponse response) throws IOException {
                    Pair<T, @Nullable GHGQLRateLimit> result = parseResponse(response, myClazz, getPathFromData());
                    if (result == null) {
                        throw new GithubJsonException("Non-nullable entity is null or entity path is invalid");
                    }
                    return result;
                }
            }

            public static class OptionalTraversedParsed<T> extends Traversed<@Nullable T> {
                private final Class<? extends T> myClazz;

                public OptionalTraversedParsed(String url,
                                               String requestFilePath,
                                               Map<String, @Nullable Object> variablesObject,
                                               Class<? extends T> clazz,
                                               String... pathFromData) {
                    super(url, requestFilePath, variablesObject, pathFromData);
                    myClazz = clazz;
                }

                @Override
                Class<?> getClazz() {
                    return myClazz;
                }

                @Override
                public Pair<@Nullable T, @Nullable GHGQLRateLimit> extractResultWithCost(GithubApiResponse response) throws IOException {
                    Pair<@Nullable T, @Nullable GHGQLRateLimit> result = parseResponse(response, myClazz, getPathFromData());
                    return result != null ? result : Pair.create(null, null);
                }
            }

            public static class OptionalTraversedParsedList<T> extends Traversed<@Nullable List<T>> {
                private final Class<? extends T> myClazz;

                public OptionalTraversedParsedList(String url,
                                                   String requestFilePath,
                                                   Map<String, @Nullable Object> variablesObject,
                                                   Class<? extends T> clazz,
                                                   String... pathFromData) {
                    super(url, requestFilePath, variablesObject, pathFromData);
                    myClazz = clazz;
                }

                @Override
                Class<?> getClazz() {
                    return myClazz;
                }

                @Override
                @SuppressWarnings("unchecked")
                public Pair<@Nullable List<T>, @Nullable GHGQLRateLimit> extractResultWithCost(GithubApiResponse response) throws IOException {
                    Pair<@Nullable List<T>, @Nullable GHGQLRateLimit> result = parseResponse(response, getPathFromData(),
                        it -> (List<T>) GithubApiContentHelper.readJsonList(new StringReader(it.toString()), myClazz));
                    return result != null ? result : Pair.create(null, null);
                }
            }

            protected <R> @Nullable Pair<R, @Nullable GHGQLRateLimit> parseResponse(GithubApiResponse response, Class<? extends R> clazz)
                throws IOException {
                return parseResponse(response, clazz, new String[0]);
            }

            protected <R> @Nullable Pair<R, @Nullable GHGQLRateLimit> parseResponse(GithubApiResponse response,
                                                                                   Class<? extends R> clazz,
                                                                                   String[] pathFromData) throws IOException {
                return parseResponse(response, pathFromData, it -> (R) GithubApiContentHelper.fromJson(it.toString(), clazz, true));
            }

            protected <R> @Nullable Pair<R, @Nullable GHGQLRateLimit> parseResponse(GithubApiResponse response,
                                                                                   String[] pathFromData,
                                                                                   JsonNodeDeserializer<R> deserialize)
                throws IOException {
                GraphQLResponseDTO<JsonNode, GHGQLError> result = parseGQLResponse(response, JsonNode.class);
                JsonNode data = result.getData();
                if (data != null && !data.isNull()) {
                    JsonNode node = data;
                    for (String path : pathFromData) {
                        JsonNode next = node.get(path);
                        if (next == null) {
                            break;
                        }
                        node = next;
                    }
                    if (!node.isNull()) {
                        R value = deserialize.deserialize(node);
                        JsonNode ratesNode = data.get("rateLimit");
                        GHGQLRateLimit rates = ratesNode == null || ratesNode.isNull()
                            ? null
                            : GithubApiContentHelper.fromJson(ratesNode.toString(), GHGQLRateLimit.class, true);
                        return Pair.create(value, rates);
                    }
                }
                List<GHGQLError> errors = result.getErrors();
                if (errors == null) {
                    return null;
                }
                else {
                    throw throwException(errors);
                }
            }

            @FunctionalInterface
            protected interface JsonNodeDeserializer<R> {
                R deserialize(JsonNode node) throws IOException;
            }
        }
    }

    public abstract static class Put<T> extends WithBody<T> {
        private final String myBodyMimeType;
        private final @Nullable String myAcceptMimeType;

        protected Put(String bodyMimeType, String url) {
            this(bodyMimeType, url, null);
        }

        protected Put(String bodyMimeType, String url, @Nullable String acceptMimeType) {
            super(url);
            myBodyMimeType = bodyMimeType;
            myAcceptMimeType = acceptMimeType;
        }

        @Override
        public String getBodyMimeType() {
            return myBodyMimeType;
        }

        @Override
        public @Nullable String getAcceptMimeType() {
            return myAcceptMimeType;
        }

        public static <T> Put<T> json(String url, Class<T> clazz) {
            return json(url, null, clazz);
        }

        public static <T> Put<T> json(String url, @Nullable Object body, Class<T> clazz) {
            return new Json<>(url, body, clazz);
        }

        public static <T> Put<List<T>> jsonList(String url, Object body, Class<T> clazz) {
            return new JsonList<>(url, body, clazz);
        }

        public static class Json<T> extends Put<T> {
            private final @Nullable Object myBodyObject;
            private final Class<T> myClazz;

            public Json(String url, @Nullable Object bodyObject, Class<T> clazz) {
                super(GithubApiContentHelper.JSON_MIME_TYPE, url, GithubApiContentHelper.V3_JSON_MIME_TYPE);
                myBodyObject = bodyObject;
                myClazz = clazz;
                if (bodyObject == null) {
                    headers.put("Content-Length", "0");
                }
            }

            @Override
            public @Nullable String getBody() throws IOException {
                return myBodyObject == null ? null : GithubApiContentHelper.toJson(myBodyObject);
            }

            @Override
            public T extractResult(GithubApiResponse response) throws IOException {
                return parseJsonObject(response, myClazz);
            }
        }

        public static class JsonList<T> extends Put<List<T>> {
            private final @Nullable Object myBodyObject;
            private final Class<T> myClazz;

            public JsonList(String url, @Nullable Object bodyObject, Class<T> clazz) {
                super(GithubApiContentHelper.JSON_MIME_TYPE, url, GithubApiContentHelper.V3_JSON_MIME_TYPE);
                myBodyObject = bodyObject;
                myClazz = clazz;
                if (bodyObject == null) {
                    headers.put("Content-Length", "0");
                }
            }

            @Override
            public @Nullable String getBody() throws IOException {
                return myBodyObject == null ? null : GithubApiContentHelper.toJson(myBodyObject);
            }

            @Override
            public List<T> extractResult(GithubApiResponse response) throws IOException {
                return parseJsonList(response, myClazz);
            }
        }
    }

    public abstract static class Patch<T> extends Post<T> {
        protected Patch(String bodyMimeType, String url) {
            this(bodyMimeType, url, null);
        }

        protected Patch(String bodyMimeType, String url, @Nullable String acceptMimeType) {
            super(bodyMimeType, url, acceptMimeType);
        }

        public static <T> Post<T> json(String url, Object body, Class<T> clazz) {
            return new Json<>(url, body, clazz);
        }

        // TODO: Mark as PATCH, currently this likely sends POSTs?
        public static class Json<T> extends Post.Json<T> {
            public Json(String url, Object bodyObject, Class<T> clazz) {
                super(url, bodyObject, clazz);
            }
        }
    }

    public abstract static class Delete<T> extends WithBody<T> {
        private final String myBodyMimeType;
        private final @Nullable String myAcceptMimeType;

        protected Delete(String bodyMimeType, String url) {
            this(bodyMimeType, url, null);
        }

        protected Delete(String bodyMimeType, String url, @Nullable String acceptMimeType) {
            super(url);
            myBodyMimeType = bodyMimeType;
            myAcceptMimeType = acceptMimeType;
        }

        @Override
        public String getBodyMimeType() {
            return myBodyMimeType;
        }

        @Override
        public @Nullable String getAcceptMimeType() {
            return myAcceptMimeType;
        }

        public static <T> Delete<T> json(String url, Class<T> clazz) {
            return json(url, null, clazz);
        }

        public static <T> Delete<T> json(String url, @Nullable Object body, Class<T> clazz) {
            return new Json<>(url, body, clazz);
        }

        public static class Json<T> extends Delete<T> {
            private final @Nullable Object myBodyObject;
            private final Class<T> myClazz;

            public Json(String url, Class<T> clazz) {
                this(url, null, clazz);
            }

            public Json(String url, @Nullable Object bodyObject, Class<T> clazz) {
                super(GithubApiContentHelper.JSON_MIME_TYPE, url, GithubApiContentHelper.V3_JSON_MIME_TYPE);
                myBodyObject = bodyObject;
                myClazz = clazz;
                if (bodyObject == null) {
                    headers.put("Content-Length", "0");
                }
            }

            @Override
            public @Nullable String getBody() throws IOException {
                return myBodyObject == null ? null : GithubApiContentHelper.toJson(myBodyObject);
            }

            @Override
            public T extractResult(GithubApiResponse response) throws IOException {
                return parseJsonObject(response, myClazz);
            }
        }
    }

    private static @Nullable LinkHttpHeaderValue parseLinkHeader(GithubApiResponse response) {
        String header = response.findHeader(LinkHttpHeaderValue.HEADER_NAME);
        return header == null ? null : LinkHttpHeaderValue.parse(header);
    }

    private static <T> T parseJsonObject(GithubApiResponse response, Class<T> clazz) throws IOException {
        return response.readBody(it -> GithubApiContentHelper.readJsonObject(it, clazz));
    }

    private static <T> List<T> parseJsonList(GithubApiResponse response, Class<T> clazz) throws IOException {
        return response.readBody(it -> GithubApiContentHelper.readJsonList(it, clazz));
    }

    private static <T, U> Map<T, U> parseJsonMap(GithubApiResponse response) throws IOException {
        return response.readBody(GithubApiContentHelper::readJsonMap);
    }

    @SuppressWarnings("unchecked")
    private static <T> GithubSearchResult<T> parseJsonSearchPage(GithubApiResponse response, Class<T> clazz) throws IOException {
        return response.readBody(it -> (GithubSearchResult<T>) GithubApiContentHelper.readJsonObject(it, GithubSearchResult.class, clazz));
    }

    @SuppressWarnings("unchecked")
    private static <T> GraphQLResponseDTO<T, GHGQLError> parseGQLResponse(GithubApiResponse response, Class<? extends T> dataClass)
        throws IOException {
        return response.readBody(it -> (GraphQLResponseDTO<T, GHGQLError>) GithubApiContentHelper.readJsonObject(it,
            GraphQLResponseDTO.class, true, dataClass, GHGQLError.class));
    }
}
