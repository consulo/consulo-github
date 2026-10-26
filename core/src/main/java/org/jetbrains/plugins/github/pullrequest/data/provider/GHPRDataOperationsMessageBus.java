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
package org.jetbrains.plugins.github.pullrequest.data.provider;

import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.proxy.EventDispatcher;
import consulo.ui.UIAccess;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class GHPRDataOperationsMessageBus {
    private static final Logger LOG = Logger.getInstance(GHPRDataOperationsMessageBus.class);

    private final Project myProject;
    private final EventDispatcher<GHPRDataOperationsListener> myDispatcher = EventDispatcher.create(GHPRDataOperationsListener.class);

    public GHPRDataOperationsMessageBus(Project project) {
        myProject = project;
    }

    public void addListener(GHPRDataOperationsListener listener, Disposable parentDisposable) {
        myDispatcher.addListener(listener, parentDisposable);
    }

    public CompletableFuture<?> syncPublisher(Consumer<GHPRDataOperationsListener> event) {
        return runOnUIThread(() -> event.accept(myDispatcher.getMulticaster()));
    }

    public CompletableFuture<?> runOnUIThread(Runnable runnable) {
        CompletableFuture<@Nullable Object> result = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                if (!myProject.isDisposed()) {
                    runnable.run();
                }
            }
            catch (Throwable e) {
                LOG.error(e);
            }
            finally {
                result.complete(null);
            }
        };
        if (myProject.isDisposed()) {
            result.complete(null);
            return result;
        }
        UIAccess uiAccess = myProject.getUIAccess();
        if (!uiAccess.isValid()) {
            task.run();
            return result;
        }
        uiAccess.execute(task);
        return result;
    }
}
