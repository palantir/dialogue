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

package com.palantir.conjure.java.dialogue.serde;

import java.lang.ref.Cleaner;

final class CleanerSupport {
    private static final Cleaner cleaner = Cleaner.create();

    /** Returns a cleanable runnable which can prematurely clean the result when values aren't leaked. */
    static void register(Object object, Runnable action) {
        cleaner.register(object, action);
    }

    static boolean enabled() {
        return true;
    }

    private CleanerSupport() {}
}
