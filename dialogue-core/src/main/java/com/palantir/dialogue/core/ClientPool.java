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

import com.palantir.dialogue.Channel;
import java.io.Closeable;

/**
 * Facilitates creating many clients which all share the same connection pool and smart logic (including
 * concurrency limiters / blacklisting info etc. Should only create one of these per server. Close it when your
 * server shuts down to release resources.
 */
public interface ClientPool extends Closeable {

    /** Returns a working implementation of the given dialogueInterface, hooked up to a smart channel underneath. */
    <T> T get(Class<T> dialogueInterface, Listenable<ClientConfig> config);

    /**
     * Returns a channel for interacting with the given abstract upstream service, which routes traffic
     * appropriately to the various available nodes.
     */
    Channel smartChannel(Listenable<ClientConfig> config);

    /**
     * Gets us a direct line to a single host within the specified Config. Live-reloads under the hood. The channel
     * will always fail if the specified uri is not listed in the latest version of the config.
     */
    Channel rawChannel(String uri, Listenable<ClientConfig> config);

    /**
     * Releases all underlying resources (e.g. connection pools). All previously returned clients will become
     * non-functional after calling this. Call this at server shutdown time.
     */
    void close();
}
