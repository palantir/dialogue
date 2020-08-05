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

/** Multi-release jar shim to allow new versions to take advantage of the Cleaner on supported runtimes. */
final class CleanerSupport {

    /** Returns a cleanable runnable which can prematurely clean the result when values aren't leaked. */
    static void register(Object _object, Runnable _action) {}

    static boolean enabled() {
        return false;
    }

    private CleanerSupport() {}
}
