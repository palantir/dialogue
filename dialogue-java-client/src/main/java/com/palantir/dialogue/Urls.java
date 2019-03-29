/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue;

import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.net.MalformedURLException;
import java.net.URL;

/** Convenience methods for creating {@link URL}s from hosts, ports, paths, etc. */
final class Urls {
    private Urls() {}

    static URL https(String host, int port, String path) {
        return create("https", host, port, path);
    }

    static URL https(String host, int port) {
        return create("https", host, port, "");
    }

    static URL http(String host, int port) {
        return create("http", host, port, "");
    }

    static URL http(String host, int port, String path) {
        return create("http", host, port, path);
    }

    static URL create(String protocol, String host, int port, String path) {
        Preconditions.checkArgument(path.isEmpty() || path.startsWith("/"),
                "path must be empty or start with /");
        try {
            return new URL(protocol, host, port, path);
        } catch (MalformedURLException e) {
            throw new SafeRuntimeException("Failed to create URL", e);
        }
    }
}
