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

import consulo.accountManagement.oauth.AcceptCodeHandleResult;
import consulo.accountManagement.oauth.OAuthCallbackHandler;
import consulo.accountManagement.oauth.OAuthResult;
import consulo.accountManagement.oauth.OAuthService;
import consulo.accountManagement.oauth.RedirectAcceptCodeHandleResult;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.io.Url;
import jakarta.inject.Inject;

@ExtensionImpl
public class GHOAuthCallbackHandler extends OAuthCallbackHandler {
    private final GHOAuthService myService;

    @Inject
    public GHOAuthCallbackHandler(GHOAuthService service) {
        myService = service;
    }

    @Override
    protected OAuthService<?> oauthService() {
        return myService;
    }

    @Override
    protected AcceptCodeHandleResult handleOAuthResult(OAuthResult<?> oAuthResult) {
        Url serviceUrl = GHOAuthService.getServiceUrl(oAuthResult.request());
        Url redirectUrl = oAuthResult.isAccepted() ? serviceUrl.resolve("complete") : serviceUrl.resolve("error");
        return new RedirectAcceptCodeHandleResult(redirectUrl);
    }
}
