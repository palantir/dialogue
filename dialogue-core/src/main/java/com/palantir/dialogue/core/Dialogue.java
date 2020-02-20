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

import com.google.errorprone.annotations.MustBeClosed;

public final class Dialogue {

    /**
     * Facilitates creating many clients which all share the same connection pool and smart logic (including
     * concurrency limiters / blacklisting info etc). Should only create one of these per server. Close it when your
     * server shuts down to release resources.
     */
    @MustBeClosed
    public static ClientPool newClientPool() {
        // TODO(dfox): keep track of how many times people call this in a single JVM and maybe log?
        // TODO(dfox): will we ever want to pass in pool-level params? do we need a builder from the beginning?
        return new ClientPoolImpl();
    }

    private Dialogue() {}
}
