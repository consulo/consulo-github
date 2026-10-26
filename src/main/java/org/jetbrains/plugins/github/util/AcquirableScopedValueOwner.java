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
package org.jetbrains.plugins.github.util;

import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

public class AcquirableScopedValueOwner<T> {
    private final Disposable myCs;
    private final Function<Disposable, T> myValueFactory;

    private int mySubscriptionCount = 0;
    private @Nullable Pair<T, Disposable> myValueAndCs;
    private boolean myCancelled;

    public AcquirableScopedValueOwner(Disposable parentCs, Function<Disposable, T> valueFactory) {
        myValueFactory = valueFactory;
        myCs = Disposable.newDisposable(getClass().getName());
        if (!Disposer.tryRegister(parentCs, myCs)) {
            throw new IllegalStateException("Already cancelled");
        }
        Disposer.register(myCs, () -> {
            synchronized (this) {
                myCancelled = true;
                myValueAndCs = null;
            }
        });
    }

    public synchronized @Nullable T getValue() {
        return myValueAndCs == null ? null : myValueAndCs.getFirst();
    }

    public T acquireValue(Disposable borrowCs) {
        synchronized (this) {
            if (myCancelled) {
                throw new IllegalStateException("Already cancelled");
            }

            mySubscriptionCount += 1;
            Disposer.register(borrowCs, this::releaseValue);

            if (myValueAndCs == null) {
                Disposable newCs = Disposable.newDisposable("value");
                Disposer.register(myCs, newCs);
                myValueAndCs = Pair.create(myValueFactory.apply(newCs), newCs);
            }
            return myValueAndCs.getFirst();
        }
    }

    private void releaseValue() {
        Disposable toDispose = null;
        synchronized (this) {
            mySubscriptionCount -= 1;
            if (mySubscriptionCount <= 0 && myValueAndCs != null) {
                toDispose = myValueAndCs.getSecond();
                myValueAndCs = null;
            }
        }
        if (toDispose != null) {
            Disposer.dispose(toDispose);
        }
    }
}
