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

import com.google.common.collect.ImmutableList;
import com.palantir.dialogue.BinaryRequestBody;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Headers;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeIoException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/** Package private internal API. */
final class ConjureBodySerDe implements BodySerDe {
    private static final String BINARY_CONTENT_TYPE = "application/octet-stream";

    private final List<Encoding> encodings;
    private final Encoding defaultEncoding;

    /**
     * Selects the first (based on input order) of the provided encodings that
     * {@link Encoding#supportsContentType supports} the serialization format {@link Headers#ACCEPT accepted}
     * by a given request, or the first serializer if no such serializer can be found.
     */
    ConjureBodySerDe(List<Encoding> encodings) {
        // Defensive copy
        this.encodings = ImmutableList.copyOf(encodings);
        Preconditions.checkArgument(encodings.size() > 0, "At least one Encoding is required");
        this.defaultEncoding = encodings.get(0);
    }

    @Override
    public <T> Serializer<T> serializer(TypeMarker<T> token) {
        return new EncodingSerializerRegistry<>(defaultEncoding, token);
    }

    @Override
    public <T> Deserializer<T> deserializer(TypeMarker<T> token) {
        return new EncodingDeserializerRegistry<>(encodings, token);
    }

    @Override
    public Deserializer<Void> emptyBodyDeserializer() {
        return response -> {
            if (response.body().read() != -1) {
                throw new RuntimeException("Expected empty response body");
            }
            return null;
        };
    }

    @Override
    public RequestBody serialize(BinaryRequestBody value) {
        Preconditions.checkNotNull(value, "A BinaryRequestBody value is required");
        return new RequestBody() {
            @Override
            public Optional<Long> length() {
                return Optional.empty();
            }

            @Override
            public InputStream content() {
                throw new UnsupportedOperationException("TODO(rfink): implement this");
            }

            @Override
            public String contentType() {
                return BINARY_CONTENT_TYPE;
            }
        };
    }

    @Override
    public InputStream deserializeInputStream(Response exchange) {
        Optional<String> contentType = exchange.contentType();
        if (!contentType.isPresent()) {
            throw new SafeIllegalArgumentException("Response is missing Content-Type header");
        }
        if (!contentType.get().startsWith(BINARY_CONTENT_TYPE)) {
            throw new SafeIllegalArgumentException(
                    "Unsupported Content-Type",
                    SafeArg.of("Content-Type", contentType));
        }
        return exchange.body();
    }

    private static final class EncodingSerializerRegistry<T> implements Serializer<T> {

        private final EncodingSerializerContainer<T> encoding;

        EncodingSerializerRegistry(Encoding encoding, TypeMarker<T> token) {
            this.encoding = new EncodingSerializerContainer<>(encoding, token);
        }

        @Override
        public RequestBody serialize(T value) {
            Preconditions.checkNotNull(value, "cannot serialize null value");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            encoding.serializer.serialize(value, bytes);
            try {
                bytes.flush();
                bytes.close();
            } catch (IOException e) {
                throw new RuntimeException("Failed to close or flush ByteStream. This is a bug.", e);
            }

            return new RequestBody() {
                @Override
                public Optional<Long> length() {
                    return Optional.of((long) bytes.size());
                }

                @Override
                public InputStream content() {
                    return new ByteArrayInputStream(bytes.toByteArray());
                }

                @Override
                public String contentType() {
                    return encoding.encoding.getContentType();
                }
            };
        }
    }

    private static final class EncodingSerializerContainer<T> {

        private final Encoding encoding;
        private final Encoding.Serializer<T> serializer;

        EncodingSerializerContainer(Encoding encoding, TypeMarker<T> token) {
            this.encoding = encoding;
            this.serializer = encoding.serializer(token);
        }
    }

    private static final class EncodingDeserializerRegistry<T> implements Deserializer<T> {

        private final List<EncodingDeserializerContainer<T>> encodings;

        EncodingDeserializerRegistry(List<Encoding> encodings, TypeMarker<T> token) {
            this.encodings = encodings.stream()
                    .map(encoding -> new EncodingDeserializerContainer<>(encoding, token))
                    .collect(ImmutableList.toImmutableList());
        }

        @Override
        public T deserialize(Response response) throws IOException {
            EncodingDeserializerContainer<T> container = getResponseDeserializer(response.contentType());
            return container.deserializer.deserialize(response.body());
        }

        /** Returns the {@link EncodingDeserializerContainer} to use to deserialize the request body. */
        @SuppressWarnings("ForLoopReplaceableByForEach")
        // performance sensitive code avoids iterator allocation
        EncodingDeserializerContainer<T> getResponseDeserializer(Optional<String> contentType) throws SafeIoException {
            if (!contentType.isPresent()) {
                throw new SafeIllegalArgumentException("Response is missing Content-Type header");
            }
            for (int i = 0; i < encodings.size(); i++) {
                EncodingDeserializerContainer<T> container = encodings.get(i);
                if (container.encoding.supportsContentType(contentType.get())) {
                    return container;
                }
            }
            throw new SafeIoException("Unsupported Content-Type", SafeArg.of("Content-Type", contentType));
        }
    }

    private static final class EncodingDeserializerContainer<T> {

        private final Encoding encoding;
        private final Encoding.Deserializer<T> deserializer;

        EncodingDeserializerContainer(Encoding encoding, TypeMarker<T> token) {
            this.encoding = encoding;
            this.deserializer = encoding.deserializer(token);
        }
    }
}
