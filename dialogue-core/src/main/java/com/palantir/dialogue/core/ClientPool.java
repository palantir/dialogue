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
import java.net.URI;
import jdk.internal.net.http.websocket.RawChannel;

public interface ClientPool extends Closeable {

    /** Returns a working implementation of the given dialogueInterface, hooked up to a smart channel underneath. */
    <T> T get(Class<T> dialogueInterface, String serviceName, Listenable<DialogConfig> config);

    /** Load balances nicely across hosts in the given {@link DialogConfig}. */
    Channel smartChannel(String serviceName, ConfigSource configSupplier);

    /**
     * Gets us a direct line to a single host within the specified Config. Live-reloads under the hood. The channel
     * will always fail if the specified uri is not listed in the latest version of the config.
     */
    RawChannel rawChannel(URI uri, String serviceName, ConfigSource configSupplier);

    /**
     * Releases all underlying resources (e.g. connection pools). All previously returned clients will become
     * non-functional after calling this. Call this at server shutdown time.
     */
    void close();
}
