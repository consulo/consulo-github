// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package org.jetbrains.plugins.github.authentication;

import consulo.accountManagement.oauth.AcquireCredentialsResult;
import consulo.accountManagement.oauth.OAuthCredentials;
import consulo.accountManagement.oauth.OAuthCredentialsAcquirer;
import consulo.accountManagement.oauth.OAuthCredentialsAcquirerHttp;
import consulo.accountManagement.oauth.SimpleOAuthCredentials;
import consulo.util.io.Url;

import java.util.Map;

public class GHOAuthCredentialsAcquirer implements OAuthCredentialsAcquirer<OAuthCredentials> {
    private final Url myServiceUrl;
    private final String myCodeVerifier;

    public GHOAuthCredentialsAcquirer(Url serviceUrl, String codeVerifier) {
        myServiceUrl = serviceUrl;
        myCodeVerifier = codeVerifier;
    }

    @Override
    public AcquireCredentialsResult<OAuthCredentials> acquireCredentials(String code) {
        Url tokenUrl = myServiceUrl.resolve("access_token").addParameters(Map.of(
            "code", code,
            "code_verifier", myCodeVerifier
        ));

        return OAuthCredentialsAcquirerHttp.requestToken(tokenUrl, (body, headers) ->
            new SimpleOAuthCredentials(headers.firstValue("X-OAuth-Token").orElseThrow()));
    }
}
