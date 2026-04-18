// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
/*
 * Copyright 2013-2025 consulo.io
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
package org.jetbrains.plugins.github.authentication.accounts;

import consulo.collaboration.auth.ServerAccount;
import org.jetbrains.plugins.github.api.GithubServerPath;

public class GithubAccount extends ServerAccount {
    private String myName;
    private GithubServerPath myServer;
    private String myId;

    /**
     * Default constructor for deserialization.
     */
    public GithubAccount() {
        myName = "";
        myServer = new GithubServerPath();
        myId = generateId();
    }

    public GithubAccount(String name, GithubServerPath server, String id) {
        myName = name;
        myServer = server;
        myId = id;
    }

    public GithubAccount(String name, GithubServerPath server) {
        this(name, server, generateId());
    }

    @Override
    public String getId() {
        return myId;
    }

    @Override
    public String getName() {
        return myName;
    }

    public void setName(String name) {
        myName = name;
    }

    @Override
    public GithubServerPath getServer() {
        return myServer;
    }

    @Override
    public String toString() {
        return myServer + "/" + myName;
    }
}
