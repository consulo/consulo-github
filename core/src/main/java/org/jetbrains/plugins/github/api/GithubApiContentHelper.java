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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import consulo.http.graphql.dto.GraphQLConnectionDTO;
import consulo.http.graphql.dto.GraphQLCursorPageInfoDTO;
import consulo.http.graphql.dto.GraphQLErrorDTO;
import consulo.http.graphql.dto.GraphQLNodesDTO;
import consulo.http.graphql.dto.GraphQLRequestDTO;
import consulo.http.graphql.dto.GraphQLResponseDTO;
import consulo.ui.image.Image;
import org.jetbrains.plugins.github.exceptions.GithubJsonException;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public final class GithubApiContentHelper {
    public static final String JSON_MIME_TYPE = "application/json";
    public static final String V3_JSON_MIME_TYPE = "application/vnd.github.v3+json";
    public static final String V3_HTML_JSON_MIME_TYPE = "application/vnd.github.v3.html+json";
    public static final String V3_DIFF_JSON_MIME_TYPE = "application/vnd.github.v3.diff+json";

    private static final ObjectMapper jackson = genericMapperBuilder()
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .build();

    private static final ObjectMapper gqlJackson = genericMapperBuilder()
        .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
        .build();

    private GithubApiContentHelper() {
    }

    private static JsonMapper.Builder genericMapperBuilder() {
        return JsonMapper.builder()
            .defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX"))
            .defaultTimeZone(TimeZone.getDefault())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            // Jackson 3 enables this feature by default. Keep the Jackson 2 behavior: an absent primitive gets its default value.
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .enable(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
            .changeDefaultPropertyInclusion(it -> JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .changeDefaultVisibility(it -> it.withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                .withCreatorVisibility(JsonAutoDetect.Visibility.NONE)
                .withFieldVisibility(JsonAutoDetect.Visibility.ANY))
            .withConfigOverride(List.class, it -> it.setNullHandling(JsonSetter.Value.forValueNulls(Nulls.AS_EMPTY)))
            .addMixIn(GraphQLResponseDTO.class, GraphQLResponseDTOMixIn.class)
            .addMixIn(GraphQLErrorDTO.class, GraphQLErrorDTOMixIn.class)
            .addMixIn(GraphQLNodesDTO.class, GraphQLNodesDTOMixIn.class)
            .addMixIn(GraphQLConnectionDTO.class, GraphQLConnectionDTOMixIn.class)
            .addMixIn(GraphQLCursorPageInfoDTO.class, GraphQLCursorPageInfoDTOMixIn.class)
            .addMixIn(GraphQLRequestDTO.class, GraphQLRequestDTOMixIn.class);
    }

    public static <T> T fromJson(String string, Class<T> clazz) throws GithubJsonException {
        return fromJson(string, clazz, false);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String string, Class<T> clazz, boolean gqlNaming) throws GithubJsonException {
        if (clazz == Void.class) {
            return (T) null;
        }
        try {
            return getObjectMapper(gqlNaming).readValue(string, clazz);
        }
        catch (StreamReadException e) {
            throw new GithubJsonException("Can't parse GitHub response", e);
        }
    }

    public static <T> T readJsonObject(Reader reader, Class<T> clazz, Class<?>... parameters) throws GithubJsonException {
        return readJsonObject(reader, clazz, false, parameters);
    }

    public static <T> T readJsonObject(Reader reader, Class<T> clazz, boolean gqlNaming, Class<?>... parameters) throws GithubJsonException {
        return readJson(reader, jackson.getTypeFactory().constructParametricType(clazz, parameters), gqlNaming);
    }

    public static <T> List<T> readJsonList(Reader reader, Class<T> parameterClass) throws GithubJsonException {
        return readJson(reader, jackson.getTypeFactory().constructCollectionType(List.class, parameterClass), false);
    }

    public static <T, U> Map<T, U> readJsonMap(Reader reader) throws GithubJsonException {
        return readJsonMap(reader, false);
    }

    public static <T, U> Map<T, U> readJsonMap(Reader reader, boolean gqlNaming) throws GithubJsonException {
        try {
            return getObjectMapper(gqlNaming).readValue(reader, new TypeReference<Map<T, U>>() {
            });
        }
        catch (JacksonException e) {
            throw new GithubJsonException("Can't parse GitHub response", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T readJson(Reader reader, JavaType type, boolean gqlNaming) throws GithubJsonException {
        try {
            if (type.isTypeOrSubTypeOf(Void.class)) {
                return (T) null;
            }
            return getObjectMapper(gqlNaming).readValue(reader, type);
        }
        catch (JacksonException e) {
            throw new GithubJsonException("Can't parse GitHub response", e);
        }
    }

    public static String toJson(Object content) throws GithubJsonException {
        return toJson(content, false);
    }

    public static String toJson(Object content, boolean gqlNaming) throws GithubJsonException {
        try {
            return getObjectMapper(gqlNaming).writeValueAsString(content);
        }
        catch (JacksonException e) {
            throw new GithubJsonException("Can't serialize GitHub request body", e);
        }
    }

    public static Image loadImage(byte[] bytes) throws IOException {
        return Image.fromBytes(detectImageType(bytes), bytes);
    }

    private static Image.ImageType detectImageType(byte[] bytes) {
        if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G')) {
            return Image.ImageType.PNG;
        }
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return Image.ImageType.JPEG;
        }
        if (startsWith(bytes, 0, 'G', 'I', 'F', '8')) {
            return Image.ImageType.GIF;
        }
        if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) {
            return Image.ImageType.WEBP;
        }
        if (startsWith(bytes, 0, 'B', 'M')) {
            return Image.ImageType.BMP;
        }
        if (startsWith(bytes, 0, 0, 0, 1, 0)) {
            return Image.ImageType.ICO;
        }
        if (startsWith(bytes, 0, 'I', 'I', '*', 0) || startsWith(bytes, 0, 'M', 'M', 0, '*')) {
            return Image.ImageType.TIFF;
        }
        if (isSvg(bytes)) {
            return Image.ImageType.SVG;
        }
        return Image.ImageType.PNG;
    }

    private static boolean startsWith(byte[] bytes, int offset, int... prefix) {
        if (bytes.length < offset + prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[offset + i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSvg(byte[] bytes) {
        String head = new String(bytes, 0, Math.min(bytes.length, 4096), StandardCharsets.UTF_8).stripLeading();
        if (head.startsWith("\uFEFF")) {
            head = head.substring(1).stripLeading();
        }
        return head.startsWith("<") && head.contains("<svg");
    }

    static ObjectMapper getObjectMapper(boolean gqlNaming) {
        return !gqlNaming ? jackson : gqlJackson;
    }

    private abstract static class GraphQLRequestDTOMixIn {
        @JsonProperty("query")
        abstract String query();

        @JsonProperty("variables")
        abstract @Nullable Object variables();
    }

    private abstract static class GraphQLResponseDTOMixIn {
        @JsonCreator
        GraphQLResponseDTOMixIn(@JsonProperty("data") @Nullable Object data, @JsonProperty("errors") @Nullable List<?> errors) {
        }
    }

    private abstract static class GraphQLErrorDTOMixIn {
        @JsonCreator
        GraphQLErrorDTOMixIn(@JsonProperty("message") String message) {
        }
    }

    private abstract static class GraphQLNodesDTOMixIn {
        @JsonCreator
        GraphQLNodesDTOMixIn(@JsonProperty("nodes") List<?> nodes) {
        }
    }

    private abstract static class GraphQLConnectionDTOMixIn {
        @JsonCreator
        GraphQLConnectionDTOMixIn(@JsonProperty("pageInfo") GraphQLCursorPageInfoDTO pageInfo, @JsonProperty("nodes") List<?> nodes) {
        }
    }

    private abstract static class GraphQLCursorPageInfoDTOMixIn {
        @JsonCreator
        GraphQLCursorPageInfoDTOMixIn(@JsonProperty("startCursor") @Nullable String startCursor,
                                      @JsonProperty("hasPreviousPage") boolean hasPreviousPage,
                                      @JsonProperty("endCursor") @Nullable String endCursor,
                                      @JsonProperty("hasNextPage") boolean hasNextPage) {
        }
    }
}
