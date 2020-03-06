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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.palantir.dialogue.BinaryRequestBody;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.core.HttpHeaders;

/** Package private internal API. */
final class ConjureBodySerDe implements BodySerDe {

    private final List<Encoding> encodingsSortedByWeight;
    private final ErrorDecoder errorDecoder;
    private final Encoding defaultEncoding;
    private final Deserializer<InputStream> binaryInputStreamDeserializer;

    /**
     * Selects the first (based on input order) of the provided encodings that
     * {@link Encoding#supportsContentType supports} the serialization format {@link HttpHeaders#ACCEPT accepted}
     * by a given request, or the first serializer if no such serializer can be found.
     */
    ConjureBodySerDe(List<WeightedEncoding> encodings) {
        this(encodings, ErrorDecoder.INSTANCE);
    }

    @VisibleForTesting
    ConjureBodySerDe(List<WeightedEncoding> encodings, ErrorDecoder errorDecoder) {
        this.encodingsSortedByWeight = sortByWeight(encodings);
        this.errorDecoder = errorDecoder;
        Preconditions.checkArgument(encodings.size() > 0, "At least one Encoding is required");
        this.defaultEncoding = encodings.get(0).encoding();
        this.binaryInputStreamDeserializer = new EncodingDeserializerRegistry<>(
                ImmutableList.of(BinaryEncoding.INSTANCE), errorDecoder, BinaryEncoding.MARKER);
    }

    private ImmutableList<Encoding> sortByWeight(List<WeightedEncoding> encodings) {
        // Use list.sort which guarantees a stable sort, so the original order is preserved
        // when weights are equal.
        List<WeightedEncoding> mutableEncodings = new ArrayList<>(encodings);
        mutableEncodings.sort(Comparator.comparing(WeightedEncoding::weight).reversed());
        return ImmutableList.copyOf(Lists.transform(mutableEncodings, WeightedEncoding::encoding));
    }

    @Override
    public <T> Serializer<T> serializer(TypeMarker<T> token) {
        return new EncodingSerializerRegistry<>(defaultEncoding, token);
    }

    @Override
    public <T> Deserializer<T> deserializer(TypeMarker<T> token) {
        return new EncodingDeserializerRegistry<>(encodingsSortedByWeight, errorDecoder, token);
    }

    @Override
    public Deserializer<Void> emptyBodyDeserializer() {
        return EmptyBodyDeserializer.INSTANCE;
    }

    @Override
    public Deserializer<InputStream> inputStreamDeserializer() {
        return binaryInputStreamDeserializer;
    }

    @Override
    public RequestBody serialize(BinaryRequestBody value) {
        Preconditions.checkNotNull(value, "A BinaryRequestBody value is required");
        return new RequestBody() {

            @Override
            public void writeTo(OutputStream output) throws IOException {
                value.write(output);
            }

            @Override
            public String contentType() {
                return BinaryEncoding.CONTENT_TYPE;
            }
        };
    }

    private static final class EncodingSerializerRegistry<T> implements Serializer<T> {

        private final EncodingSerializerContainer<T> encoding;

        EncodingSerializerRegistry(Encoding encoding, TypeMarker<T> token) {
            this.encoding = new EncodingSerializerContainer<>(encoding, token);
        }

        @Override
        public RequestBody serialize(T value) {
            Preconditions.checkNotNull(value, "cannot serialize null value");

            return new RequestBody() {

                @Override
                public void writeTo(OutputStream output) {
                    encoding.serializer.serialize(value, output);
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

        private final ImmutableList<EncodingDeserializerContainer<T>> encodings;
        private final ErrorDecoder errorDecoder;
        private final TypeMarker<T> token;
        private final boolean isOptionalType;
        private final Optional<String> acceptValue;

        EncodingDeserializerRegistry(List<Encoding> encodings, ErrorDecoder errorDecoder, TypeMarker<T> token) {
            this.encodings = encodings.stream()
                    .map(encoding -> new EncodingDeserializerContainer<>(encoding, token))
                    .collect(ImmutableList.toImmutableList());
            this.errorDecoder = errorDecoder;
            this.token = token;
            this.isOptionalType = TypeMarkers.isOptional(token);
            // Encodings are applied to the accept header in the order of preference based on the provided list.
            this.acceptValue =
                    Optional.of(encodings.stream().map(Encoding::getContentType).collect(Collectors.joining(", ")));
        }

        @Override
        public T deserialize(Response response) {
            try {
                if (errorDecoder.isError(response)) {
                    throw errorDecoder.decode(response);
                } else if (response.code() == 204) {
                    if (!isOptionalType) {
                        throw new SafeRuntimeException(
                                "Unable to deserialize non-optional response type from 204", SafeArg.of("type", token));
                    }
                    return TypeMarkers.getEmptyOptional(token);
                }

                Optional<String> contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
                EncodingDeserializerContainer<T> container = getResponseDeserializer(contentType);
                return container.deserializer.deserialize(response.body());
            } finally {
                response.close();
            }
        }

        @Override
        public Optional<String> accepts() {
            return acceptValue;
        }

        /** Returns the {@link EncodingDeserializerContainer} to use to deserialize the request body. */
        @SuppressWarnings("ForLoopReplaceableByForEach")
        // performance sensitive code avoids iterator allocation
        EncodingDeserializerContainer<T> getResponseDeserializer(Optional<String> contentType) {
            if (!contentType.isPresent()) {
                throw new SafeIllegalArgumentException("Response is missing Content-Type header");
            }
            for (int i = 0; i < encodings.size(); i++) {
                EncodingDeserializerContainer<T> container = encodings.get(i);
                if (container.encoding.supportsContentType(contentType.get())) {
                    return container;
                }
            }
            throw new SafeRuntimeException("Unsupported Content-Type", SafeArg.of("Content-Type", contentType));
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

    private enum EmptyBodyDeserializer implements Deserializer<Void> {
        INSTANCE;

        @Override
        @SuppressWarnings("NullAway") // empty body is a special case
        public Void deserialize(Response response) {
            // We should not fail if a server that previously returned nothing starts returning a response
            response.close();
            return null;
        }

        @Override
        public Optional<String> accepts() {
            return Optional.empty();
        }
    }
}
