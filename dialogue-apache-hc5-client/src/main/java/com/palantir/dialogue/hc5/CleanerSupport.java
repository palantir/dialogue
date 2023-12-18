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

package com.palantir.dialogue.hc5;

import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;

/** Reflective shim to allow consumers on new runtime versions to take advantage of java.lang.ref.Cleaner. */
final class CleanerSupport {

    // Ideally we would use a name pattern like 'dialogue-cleaner-%d', however it's more important
    // that this cleaner does not retain context classloader references.
    private static final Cleaner cleaner = Cleaner.create();

    /** Arguments are passed to {@code java.lang.ref.Cleaner.register(Object, Runnable)}. */
    static Cleanable register(Object object, Runnable action) {
        return cleaner.register(object, action);
    }

    private CleanerSupport() {}
}
