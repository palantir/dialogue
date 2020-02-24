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

import com.google.common.collect.ListMultimap;
import java.io.Closeable;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface Response extends Closeable {
    /** The HTTP body for this response. */
    InputStream body();

    /** The HTTP response code for this response. */
    int code();

    /** The HTTP headers for this response. Headers names are compared in a case-insensitive fashion as per
     * https://tools.ietf.org/html/rfc7540#section-8.1.2. */
    ListMultimap<String, String> headers();

    /** Retrieves the first value from the header map for the given key. */
    default Optional<String> getFirstHeader(String header) {
        List<String> headerList = headers().get(header);
        return headerList.isEmpty() ? Optional.empty() : Optional.of(headerList.get(0));
    }

    /**
     * Releases all resources associated with this response. If the {@link #body()} is still open, {@link #close()}
     * should {@link InputStream#close() close the stream}.
     * Implementations must not throw, preferring to catch and log internally.
     */
    @Override
    void close();
}
