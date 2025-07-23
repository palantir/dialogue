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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;
import com.palantir.dialogue.BinaryRequestBody;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.DeserializerArgs;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.exceptions.SafeUncheckedIoException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Package private internal API. */
final class ConjureBodySerDe implements BodySerDe {

    private static final SafeLogger log = SafeLoggerFactory.get(ConjureBodySerDe.class);
    private final List<Encoding> encodingsSortedByWeight;
    private final Encoding defaultEncoding;
    private final Deserializer<InputStream> binaryInputStreamDeserializer;
    private final Deserializer<Optional<InputStream>> optionalBinaryInputStreamDeserializer;
    private final Deserializer<Void> emptyBodyDeserializer;
    private final LoadingCache<Type, Serializer<?>> serializers;
    private final LoadingCache<Type, EncodingDeserializerForEndpointRegistry<?, ?>> deserializers;
    private final EmptyContainerDeserializer emptyContainerDeserializer;
    private final boolean supportJsonErrorDeserialization;

    /**
     * Selects the first (based on input order) of the provided encodings that
     * {@link Encoding#supportsContentType supports} the serialization format {@link HttpHeaders#ACCEPT accepted}
     * by a given request, or the first serializer if no such serializer can be found.
     */
    ConjureBodySerDe(
            List<WeightedEncoding> rawEncodings,
            EmptyContainerDeserializer emptyContainerDeserializer,
            CaffeineSpec cacheSpec) {
        this(rawEncodings, emptyContainerDeserializer, /* supportJsonErrorDeserialization = */ false, cacheSpec);
    }

    /**
     * Selects the first (based on input order) of the provided encodings that
     * {@link Encoding#supportsContentType supports} the serialization format {@link HttpHeaders#ACCEPT accepted}
     * by a given request, or the first serializer if no such serializer can be found.
     */
    ConjureBodySerDe(
            List<WeightedEncoding> rawEncodings,
            EmptyContainerDeserializer emptyContainerDeserializer,
            boolean supportJsonErrorDeserialization,
            CaffeineSpec cacheSpec) {
        this.supportJsonErrorDeserialization = supportJsonErrorDeserialization;
        List<WeightedEncoding> encodings = decorateEncodings(rawEncodings);
        this.encodingsSortedByWeight = sortByWeight(encodings);
        Preconditions.checkArgument(encodings.size() > 0, "At least one Encoding is required");
        this.defaultEncoding = encodings.get(0).encoding();
        this.emptyContainerDeserializer = emptyContainerDeserializer;
        this.binaryInputStreamDeserializer = EncodingDeserializerForEndpointRegistry.create(
                ImmutableList.of(BinaryEncoding.INSTANCE),
                emptyContainerDeserializer,
                BinaryEncoding.MARKER,
                DeserializerArgs.<InputStream>builder()
                        .baseType(BinaryEncoding.MARKER)
                        .success(BinaryEncoding.MARKER)
                        .build());
        this.optionalBinaryInputStreamDeserializer = EncodingDeserializerForEndpointRegistry.create(
                ImmutableList.of(BinaryEncoding.INSTANCE),
                emptyContainerDeserializer,
                BinaryEncoding.OPTIONAL_MARKER,
                DeserializerArgs.<Optional<InputStream>>builder()
                        .baseType(BinaryEncoding.OPTIONAL_MARKER)
                        .success(BinaryEncoding.OPTIONAL_MARKER)
                        .build());
        this.emptyBodyDeserializer =
                new EmptyBodyDeserializer(new EndpointErrorDecoder<>(Collections.emptyMap(), Collections.emptyMap()));
        // Class unloading: Not supported, Jackson keeps strong references to the types
        // it sees: https://github.com/FasterXML/jackson-databind/issues/489
        this.serializers = Caffeine.from(cacheSpec)
                .build(type -> new EncodingSerializerRegistry<>(defaultEncoding, TypeMarker.of(type)));
        this.deserializers = Caffeine.from(cacheSpec).build(type -> buildCacheEntry(TypeMarker.of(type)));
    }

    @Override
    public boolean supportJsonErrorDeserialization() {
        return this.supportJsonErrorDeserialization;
    }

    private <T> EncodingDeserializerForEndpointRegistry<?, ?> buildCacheEntry(TypeMarker<T> typeMarker) {
        return EncodingDeserializerForEndpointRegistry.create(
                encodingsSortedByWeight,
                emptyContainerDeserializer,
                typeMarker,
                DeserializerArgs.<T>builder()
                        .baseType(typeMarker)
                        .success(typeMarker)
                        .build());
    }

    private static List<WeightedEncoding> decorateEncodings(List<WeightedEncoding> input) {
        return input.stream()
                .map(weightedEncoding -> WeightedEncoding.of(
                        new LazilyInitializedEncoding(new TracedEncoding(weightedEncoding.encoding())),
                        weightedEncoding.weight()))
                .collect(ImmutableList.toImmutableList());
    }

    private ImmutableList<Encoding> sortByWeight(List<WeightedEncoding> encodings) {
        // Use list.sort which guarantees a stable sort, so the original order is preserved
        // when weights are equal.
        List<WeightedEncoding> mutableEncodings = new ArrayList<>(encodings);
        mutableEncodings.sort(Comparator.comparing(WeightedEncoding::weight).reversed());
        return ImmutableList.copyOf(Lists.transform(mutableEncodings, WeightedEncoding::encoding));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Serializer<T> serializer(TypeMarker<T> token) {
        return (Serializer<T>) serializers.get(token.getType());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Deserializer<T> deserializer(TypeMarker<T> token) {
        return (Deserializer<T>) deserializers.get(token.getType());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Deserializer<T> deserializer(DeserializerArgs<T> deserializerArgs) {
        return EncodingDeserializerForEndpointRegistry.create(
                encodingsSortedByWeight, emptyContainerDeserializer, deserializerArgs.baseType(), deserializerArgs);
    }

    @Override
    public Deserializer<Void> emptyBodyDeserializer() {
        return emptyBodyDeserializer;
    }

    @Override
    public Deserializer<InputStream> inputStreamDeserializer() {
        return binaryInputStreamDeserializer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Deserializer<T> inputStreamDeserializer(DeserializerArgs<T> deserializerArgs) {
        return new EncodingDeserializerForEndpointRegistry<>(
                ImmutableList.of(BinaryEncoding.INSTANCE),
                emptyContainerDeserializer,
                deserializerArgs.baseType(),
                deserializerArgs,
                BinaryEncoding.MARKER,
                (Function<InputStream, T>) createSuccessTypeFunctionForInputStream(deserializerArgs.successType()));
    }

    @Override
    public Deserializer<Optional<InputStream>> optionalInputStreamDeserializer() {
        return optionalBinaryInputStreamDeserializer;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Deserializer<T> optionalInputStreamDeserializer(DeserializerArgs<T> deserializerArgs) {
        return new EncodingDeserializerForEndpointRegistry<>(
                ImmutableList.of(BinaryEncoding.INSTANCE),
                emptyContainerDeserializer,
                deserializerArgs.baseType(),
                deserializerArgs,
                BinaryEncoding.OPTIONAL_MARKER,
                (Function<Optional<InputStream>, T>)
                        createSuccessTypeFunctionForOptionalInputStream(deserializerArgs.successType()));
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

            @Override
            public boolean repeatable() {
                return value.repeatable();
            }

            @Override
            public void close() {
                try {
                    value.close();
                } catch (IOException | RuntimeException e) {
                    log.warn("Failed to close BinaryRequestBody {}", UnsafeArg.of("body", value), e);
                }
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
                public void writeTo(OutputStream output) throws IOException {
                    encoding.serializer.serialize(value, output);
                }

                @Override
                public String contentType() {
                    return encoding.encoding.getContentType();
                }

                @Override
                public boolean repeatable() {
                    return true;
                }

                @Override
                public void close() {
                    // nop
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

    private static final class EncodingDeserializerForEndpointRegistry<S, T> implements Deserializer<T> {
        private static final SafeLogger log = SafeLoggerFactory.get(EncodingDeserializerForEndpointRegistry.class);
        private final ImmutableList<EncodingDeserializerContainer<? extends S>> encodings;
        private final EndpointErrorDecoder<T> endpointErrorDecoder;
        private final Optional<String> acceptValue;
        private final Supplier<Optional<? extends S>> emptyInstance;
        private final TypeMarker<T> token;
        private final @Nullable Function<S, T> transform;

        @SuppressWarnings("unchecked")
        static <T> EncodingDeserializerForEndpointRegistry<T, T> create(
                List<Encoding> encodingsSortedByWeight,
                EmptyContainerDeserializer empty,
                TypeMarker<T> token,
                DeserializerArgs<T> deserializersForEndpoint) {
            return new EncodingDeserializerForEndpointRegistry<>(
                    encodingsSortedByWeight,
                    empty,
                    token,
                    deserializersForEndpoint,
                    (TypeMarker<T>) deserializersForEndpoint.successType(),
                    null);
        }

        EncodingDeserializerForEndpointRegistry(
                List<Encoding> encodingsSortedByWeight,
                EmptyContainerDeserializer empty,
                TypeMarker<T> token,
                DeserializerArgs<T> deserializersForEndpoint,
                TypeMarker<S> intermediateResult,
                @Nullable Function<S, T> transform) {
            this.encodings = encodingsSortedByWeight.stream()
                    .map(encoding -> new EncodingDeserializerContainer<>(encoding, intermediateResult))
                    .collect(ImmutableList.toImmutableList());
            this.endpointErrorDecoder = new EndpointErrorDecoder<>(
                    deserializersForEndpoint.errorNameToTypeMarker(),
                    deserializersForEndpoint.errorNameToExceptionTypeMarkers(),
                    encodingsSortedByWeight.stream()
                            .filter(encoding -> encoding.supportsContentType("application/json"))
                            .findFirst());
            this.token = token;
            this.emptyInstance = Suppliers.memoize(() -> empty.tryGetEmptyInstance(intermediateResult));
            this.acceptValue = Optional.of(encodingsSortedByWeight.stream()
                    .map(Encoding::getContentType)
                    .collect(Collectors.joining(", ")));
            this.transform = transform;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T deserialize(Response response) {
            boolean closeResponse = true;
            try {
                if (endpointErrorDecoder.isError(response)) {
                    return endpointErrorDecoder.decode(response);
                } else if (response.code() == 204) {
                    // TODO(dfox): what if we get a 204 for a non-optional type???
                    // TODO(dfox): support http200 & body=null
                    // TODO(dfox): what if we were expecting an empty list but got {}?
                    Optional<? extends S> maybeEmptyInstance = emptyInstance.get();
                    if (maybeEmptyInstance.isPresent()) {
                        if (transform == null) {
                            return (T) maybeEmptyInstance.get();
                        }
                        return transform.apply(maybeEmptyInstance.get());
                    }
                    throw new SafeRuntimeException(
                            "Unable to deserialize non-optional response type from 204", SafeArg.of("type", token));
                }

                Optional<String> contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
                if (!contentType.isPresent()) {
                    throw new SafeIllegalArgumentException(
                            "Response is missing Content-Type header",
                            SafeArg.of("received", response.headers().keySet()));
                }
                Encoding.Deserializer<? extends S> deserializer = getResponseDeserializer(contentType.get());
                S deserialized = deserializer.deserialize(response.body());
                // deserializer has taken on responsibility for closing the response body
                closeResponse = false;
                if (transform == null) {
                    return (T) deserialized;
                }
                return transform.apply(deserialized);
            } catch (IOException e) {
                throw new SafeUncheckedIoException(
                        "Failed to deserialize response stream",
                        e,
                        SafeArg.of("contentType", response.getFirstHeader(HttpHeaders.CONTENT_TYPE)),
                        SafeArg.of("type", token));
            } finally {
                if (closeResponse) {
                    response.close();
                }
            }
        }

        @Override
        public Optional<String> accepts() {
            return acceptValue;
        }

        /** Returns the {@link EncodingDeserializerContainer} to use to deserialize the request body. */
        @SuppressWarnings("ForLoopReplaceableByForEach")
        // performance sensitive code avoids iterator allocation
        Encoding.Deserializer<? extends S> getResponseDeserializer(String contentType) {
            for (int i = 0; i < encodings.size(); i++) {
                EncodingDeserializerContainer<? extends S> container = encodings.get(i);
                if (container.encoding.supportsContentType(contentType)) {
                    return container.deserializer;
                }
            }
            return throwingDeserializer(contentType);
        }

        private Encoding.Deserializer<S> throwingDeserializer(String contentType) {
            return input -> {
                try {
                    input.close();
                } catch (RuntimeException | IOException e) {
                    log.warn("Failed to close InputStream", e);
                }
                throw new SafeRuntimeException(
                        "Unsupported Content-Type",
                        SafeArg.of("received", contentType),
                        SafeArg.of("supportedEncodings", encodings));
            };
        }
    }

    /** Effectively just a pair. */
    private static final class EncodingDeserializerContainer<T> {

        private final Encoding encoding;
        private final Encoding.Deserializer<T> deserializer;

        EncodingDeserializerContainer(Encoding encoding, TypeMarker<T> token) {
            this.encoding = encoding;
            this.deserializer = encoding.deserializer(token);
        }

        @Override
        public String toString() {
            return "EncodingDeserializerContainer{encoding=" + encoding + ", deserializer=" + deserializer + '}';
        }
    }

    private static final class EmptyBodyDeserializer implements Deserializer<Void> {
        private final EndpointErrorDecoder<?> endpointErrorDecoder;

        EmptyBodyDeserializer(EndpointErrorDecoder<?> endpointErrorDecoder) {
            this.endpointErrorDecoder = endpointErrorDecoder;
        }

        @Override
        @SuppressWarnings("NullAway") // empty body is a special case
        public Void deserialize(Response response) {
            // We should not fail if a server that previously returned nothing starts returning a response
            try (Response unused = response) {
                if (endpointErrorDecoder.isError(response)) {
                    endpointErrorDecoder.decode(response);
                }
                return null;
            }
        }

        @Override
        public Optional<String> accepts() {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return "EmptyBodyDeserializer{}";
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Function<InputStream, T> createSuccessTypeFunctionForInputStream(TypeMarker<T> successT) {
        return successTypeCreatorFactory(successT, successType -> {
            try {
                return ((Class<T>) successType.getType()).getConstructor(InputStream.class);
            } catch (ReflectiveOperationException ex) {
                throw new SafeRuntimeException("Failed to create success type", ex);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> Function<Optional<InputStream>, T> createSuccessTypeFunctionForOptionalInputStream(
            TypeMarker<T> successT) {
        return successTypeCreatorFactory(successT, successType -> {
            try {
                Class<T> clazz = (Class<T>) successType.getType();
                for (Constructor<?> ctor : clazz.getConstructors()) {
                    if (ctor.getParameterCount() != 1) {
                        continue;
                    }
                    Type paramType = ctor.getGenericParameterTypes()[0];
                    if (paramType instanceof ParameterizedType parameterizedType) {
                        if (parameterizedType.getRawType().equals(Optional.class)
                                && parameterizedType.getActualTypeArguments()[0].equals(InputStream.class)) {
                            return (Constructor<T>) ctor;
                        }
                    }
                }
            } catch (SecurityException ex) {
                throw new SafeRuntimeException("Failed to create success type", ex);
            }
            throw new SafeRuntimeException(
                    "Failed to create success type. Could not find constructor with Optional<InputStream> parameter");
        });
    }

    private static <S, T> Function<S, T> successTypeCreatorFactory(
            TypeMarker<T> successT, Function<TypeMarker<T>, Constructor<T>> ctorExtractor) {
        if (!(successT.getType() instanceof Class<?>)) {
            throw new SafeRuntimeException("Failed to create success type", SafeArg.of("type", successT));
        }
        Constructor<T> ctor = ctorExtractor.apply(successT);
        if (ctor == null) {
            throw new SafeRuntimeException("Failed to create success type", SafeArg.of("type", successT));
        }
        return ctorParam -> {
            try {
                return ctor.newInstance(ctorParam);
            } catch (ReflectiveOperationException e) {
                throw new SafeRuntimeException("Failed to create success type", e);
            }
        };
    }
}
