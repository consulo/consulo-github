// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api;

import consulo.util.lang.function.ThrowableFunction;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

public interface GithubApiResponse {
    @Nullable
    String findHeader(String headerName);

    <T> T readBody(ThrowableFunction<Reader, T, IOException> converter) throws IOException;

    <T> T handleBody(ThrowableFunction<InputStream, T, IOException> converter) throws IOException;
}
