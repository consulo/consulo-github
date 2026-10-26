// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.exceptions;

import consulo.accountManagement.Account;

public class GithubMissingTokenException extends GithubAuthenticationException {
  public GithubMissingTokenException(String message) {
    super(message);
  }

  public GithubMissingTokenException(Account account) {
    this("Missing access token for account " + account);
  }
}
