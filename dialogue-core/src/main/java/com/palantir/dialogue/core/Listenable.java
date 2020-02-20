/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.dialogue.core;

import java.io.Closeable;

public interface Listenable<T> {

    // long method name just to make it obvious when something is not live reloading
    T getListenableCurrentValue();

    default Subscription subscribe(Runnable _updateListener) {
        throw new UnsupportedOperationException("TODO dfox build live reloading support");
    }

    interface Subscription extends Closeable {
        @Override
        void close();
    }
}
