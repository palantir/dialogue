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

package com.palantir.conjure.java.dialogue.serde;

import com.palantir.dialogue.TypeMarker;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An encoding provides support for a <pre>Content-Type</pre> corresponding with the conjure wire format. Encodings
 * provide a {@link Encoding#getContentType() content type} string as well as factories for typed {@link Serializer}
 * and {@link Deserializer} objects.
 * <p>
 * This interface is considered internal API and may change without a major version rev. Custom implementations
 * are not recommended, but may be implemented to test custom encodings without a fork of the runtime.
 */
public interface Encoding {

    /**
     * Creates a new {@link Serializer} for the requested type. It is recommended to reuse instances over requesting
     * new ones for each request.
     */
    <T> Serializer<T> serializer(TypeMarker<T> type);

    /**
     * Creates a new {@link Deserializer} for the requested type. It is recommended to reuse instances over requesting
     * new ones for each request.
     */
    <T> Deserializer<T> deserializer(TypeMarker<T> type);

    /** Returns the value used in response <pre>Content-Type</pre> header. */
    String getContentType();

    /**
     * Checks if a <pre>Content-Type</pre> or <pre>Accept</pre> value is supported by this encoding. This is not an
     * exact match on {@link #getContentType()} because values may contain additional metadata, for
     * example <pre>Content-Type: application/json; charset=utf-8</pre> may be supported by an {@link Encoding}
     * which returns <pre>application/json</pre> from {@link #getContentType()}.
     */
    boolean supportsContentType(String contentType);

    interface Deserializer<T> {

        /**
         * Reads a serialized type-{@link T} object representation from the given input stream and returns the
         * corresponding object. Implementations should read the entire input stream, but must not close it.
         * Format-related deserialization errors surface as {@link IllegalArgumentException}. Inputs and outputs
         * must never be null.
         */
        T deserialize(InputStream input) throws IOException;
    }

    interface Serializer<T> {

        /**
         * Serializes the given object and writes the serialized representation to the given output stream.
         * Implementations must not close the stream. Inputs must never be null.
         */
        void serialize(T value, OutputStream output);
    }
}
