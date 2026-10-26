// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import jakarta.inject.Singleton;

@Singleton
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class GHRequestExecutorBreaker {
    private volatile boolean myRequestsShouldFail;

    public static GHRequestExecutorBreaker getInstance() {
        return Application.get().getInstance(GHRequestExecutorBreaker.class);
    }

    public boolean isRequestsShouldFail() {
        return myRequestsShouldFail;
    }

    public void setRequestsShouldFail(boolean requestsShouldFail) {
        myRequestsShouldFail = requestsShouldFail;
    }
}
