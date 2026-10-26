// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow.create;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.review.util.IncrementallyComputedValue;
import consulo.util.concurrent.coroutine.MutableObservableValue;
import consulo.util.concurrent.coroutine.ObservableValue;
import org.jetbrains.plugins.github.ui.component.LabeledListPanelViewModel;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

class MetadataListViewModel<T> implements LabeledListPanelViewModel<T> {
    private final MutableObservableValue<List<T>> myItems = MutableObservableValue.of(List.of());

    private final MutableObservableValue<IncrementallyComputedValue<List<T>>> mySelectableItems =
        MutableObservableValue.of(IncrementallyComputedValue.loading());

    private final Function<Consumer<List<T>>, CompletableFuture<?>> myIncrementallyLoadedItems;

    private final Object myLock = new Object();
    private @Nullable Object myCurrentLoading;
    private @Nullable CompletableFuture<?> myCurrentFuture;
    private boolean myDisposed;

    MetadataListViewModel(Disposable cs,
                          BiConsumer<Disposable, Runnable> reloadSignal,
                          Function<Consumer<List<T>>, CompletableFuture<?>> incrementallyLoadedItems) {
        myIncrementallyLoadedItems = incrementallyLoadedItems;

        Disposer.register(cs, () -> {
            CompletableFuture<?> current;
            synchronized (myLock) {
                myDisposed = true;
                myCurrentLoading = null;
                current = myCurrentFuture;
                myCurrentFuture = null;
            }
            if (current != null) {
                current.cancel(false);
            }
        });

        // Eagerly load the data on creation, so that the reviewer panel is populated immediately.
        reloadSignal.accept(cs, this::load);
        load();
    }

    private void load() {
        Object loading = new Object();
        CompletableFuture<?> previous;
        synchronized (myLock) {
            if (myDisposed) {
                return;
            }
            myCurrentLoading = loading;
            previous = myCurrentFuture;
            myCurrentFuture = null;
        }
        if (previous != null) {
            previous.cancel(false);
        }

        CompletableFuture<?> future = IncrementallyComputedValue.collectIncrementallyTo(myIncrementallyLoadedItems, value -> {
            synchronized (myLock) {
                if (myCurrentLoading != loading) {
                    return;
                }
            }
            mySelectableItems.set(value);
        });
        synchronized (myLock) {
            if (myCurrentLoading == loading) {
                myCurrentFuture = future;
                return;
            }
        }
        future.cancel(false);
    }

    @Override
    public ObservableValue<List<T>> getItems() {
        return myItems;
    }

    @Override
    public ObservableValue<IncrementallyComputedValue<List<T>>> getSelectableItems() {
        return mySelectableItems;
    }

    @Override
    public void adjustList(List<T> newList) {
        myItems.set(newList);
    }
}
