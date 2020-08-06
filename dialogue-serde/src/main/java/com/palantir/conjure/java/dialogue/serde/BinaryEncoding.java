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

import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Package-private internal api.
 * This partial Encoding implementation exists to allow binary responses to share the same safety
 * and validation provided by structured encodings. This is only consumed internally to create
 * a binary-specific <pre>EncodingDeserializerRegistry</pre>.
 */
enum BinaryEncoding implements Encoding {
    INSTANCE;

    static final String CONTENT_TYPE = "application/octet-stream";
    static final TypeMarker<InputStream> MARKER = new TypeMarker<InputStream>() {};
    static final TypeMarker<Optional<InputStream>> OPTIONAL_MARKER = new TypeMarker<Optional<InputStream>>() {};

    @Override
    public <T> Serializer<T> serializer(TypeMarker<T> _type) {
        throw new UnsupportedOperationException("BinaryEncoding does not support serializers");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Deserializer<T> deserializer(TypeMarker<T> type) {
        if (MARKER.equals(type)) {
            return (Deserializer<T>) InputStreamDeserializer.INSTANCE;
        } else if (OPTIONAL_MARKER.equals(type)) {
            return (Deserializer<T>) OptionalInputStreamDeserializer.INSTANCE;
        }
        throw new SafeIllegalStateException(
                "BinaryEncoding only supports InputStream and Optional<InputStream>", SafeArg.of("requested", type));
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public boolean supportsContentType(String contentType) {
        return Encodings.matchesContentType(CONTENT_TYPE, contentType);
    }

    @Override
    public String toString() {
        return "BinaryEncoding{" + CONTENT_TYPE + '}';
    }

    enum OptionalInputStreamDeserializer implements Deserializer<Optional<InputStream>> {
        INSTANCE;

        @Override
        public Optional<InputStream> deserialize(InputStream input) {
            // intentionally not closing this, otherwise users wouldn't be able to get any data out of it!
            return Optional.of(input);
        }

        @Override
        public String toString() {
            return "OptionalInputStreamDeserializer{}";
        }
    }

    enum InputStreamDeserializer implements Deserializer<InputStream> {
        INSTANCE;

        @Override
        public InputStream deserialize(InputStream input) {
            // intentionally not closing this, otherwise users wouldn't be able to get any data out of it!
            return input;
        }

        @Override
        public String toString() {
            return "InputStreamDeserializer{}";
        }
    }
}
