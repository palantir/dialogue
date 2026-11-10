/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.TestErrorException;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.TestErrorSerializableError;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.ExceptionDeserializerArgs;
import com.palantir.dialogue.ExceptionDeserializerArgs.ErrorExceptionPair;
import com.palantir.dialogue.TypeMarker;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.assertj.core.util.introspection.FieldSupport;
import org.junit.jupiter.api.Test;

final class ConjureBodySerDeKeyCacheTest {
    private static final TypeMarker<TestErrorSerializableError> ERROR = TypeMarker.of(TestErrorSerializableError.class);
    private static final TypeMarker<TestErrorException> EXCEPTION = TypeMarker.of(TestErrorException.class);

    @Test
    void repeatedBindingDoesNotRehashErrors() {
        ConjureBodySerDe serde = serde();
        CountingType errorType = new CountingType();
        @SuppressWarnings("unchecked")
        TypeMarker<TestErrorSerializableError> marker =
                (TypeMarker<TestErrorSerializableError>) TypeMarker.of(errorType);
        ExceptionDeserializerArgs<String> args = ExceptionDeserializerArgs.<String>builder()
                .returnType(TypeMarker.of(String.class))
                .exception("Test:Error", marker, EXCEPTION)
                .build();
        Deserializer<String> expected = serde.deserializer(args);
        errorType.hashCalls.set(0);
        for (int i = 0; i < 100; i++) {
            assertThat(serde.deserializer(args)).isSameAs(expected);
        }
        assertThat(errorType.hashCalls).hasValue(0);
    }

    @Test
    void deserializerModesAndRuntimesRemainDistinct() {
        ConjureBodySerDe serde = serde();
        ExceptionDeserializerArgs<Void> voidArgs = ExceptionDeserializationTestUtils.createVoidDeserializerArgs();
        Deserializer<Void> empty = serde.emptyBodyDeserializer(voidArgs);
        assertThat(serde.deserializer(voidArgs)).isNotSameAs(empty);
        assertThat(serde.emptyBodyDeserializer(voidArgs)).isSameAs(empty);
        ExceptionDeserializerArgs<InputStream> binaryArgs =
                ExceptionDeserializationTestUtils.createInputStreamDeserializerArgs();
        Deserializer<InputStream> binary = serde.inputStreamDeserializer(binaryArgs);
        assertThat(serde.deserializer(binaryArgs)).isNotSameAs(binary);
        assertThat(serde.inputStreamDeserializer(binaryArgs)).isSameAs(binary);
        assertThat(serde().inputStreamDeserializer(binaryArgs)).isNotSameAs(binary);
    }

    @Test
    void concurrentBindingsShareOneDeserializer() {
        ConjureBodySerDe serde = serde();
        ExceptionDeserializerArgs<String> args = ExceptionDeserializationTestUtils.createStringDeserializerArgs();
        List<Deserializer<String>> results = IntStream.range(0, 128)
                .parallel()
                .mapToObj(_index -> serde.deserializer(args))
                .toList();
        assertThat(results).allSatisfy(result -> assertThat(result).isSameAs(results.get(0)));
        assertThat(serde.deserializer(ExceptionDeserializationTestUtils.createStringDeserializerArgs()))
                .isSameAs(results.get(0));
    }

    @Test
    void equalErrorsRegisteredInADifferentOrderShareOneDeserializer() {
        ConjureBodySerDe serde = serde();
        Deserializer<String> forward = serde.deserializer(ExceptionDeserializerArgs.<String>builder()
                .returnType(TypeMarker.of(String.class))
                .exception("Test:First", ERROR, EXCEPTION)
                .exception("Test:Second", ERROR, EXCEPTION)
                .build());
        Deserializer<String> reversed = serde.deserializer(ExceptionDeserializerArgs.<String>builder()
                .returnType(TypeMarker.of(String.class))
                .exception("Test:Second", ERROR, EXCEPTION)
                .exception("Test:First", ERROR, EXCEPTION)
                .build());
        assertThat(reversed).isSameAs(forward);
    }

    @Test
    void differentErrorsDoNotShareADeserializer() {
        ConjureBodySerDe serde = serde();
        Deserializer<String> first = serde.deserializer(ExceptionDeserializerArgs.<String>builder()
                .returnType(TypeMarker.of(String.class))
                .exception("Test:First", ERROR, EXCEPTION)
                .build());
        Deserializer<String> second = serde.deserializer(ExceptionDeserializerArgs.<String>builder()
                .returnType(TypeMarker.of(String.class))
                .exception("Test:Second", ERROR, EXCEPTION)
                .build());
        assertThat(second).isNotSameAs(first);
    }

    @Test
    void equalErrorMapsShareOneDecoder() {
        ConjureBodySerDe serde = serde();
        ExceptionDeserializerArgs<String> args = ExceptionDeserializationTestUtils.createStringDeserializerArgs();
        ExceptionDeserializingErrorDecoder decoder = serde.errorDecoder(args.errorNameToExceptionTypeMarkers());
        assertThat(serde.errorDecoder(args.errorNameToExceptionTypeMarkers())).isSameAs(decoder);
        // A distinct but equal map, as an older dialogue-target without interning would produce.
        ImmutableMap<String, ErrorExceptionPair<?, ?>> copy =
                ImmutableMap.copyOf(new LinkedHashMap<>(args.errorNameToExceptionTypeMarkers()));
        assertThat(copy).isNotSameAs(args.errorNameToExceptionTypeMarkers());
        assertThat(serde.errorDecoder(copy)).isSameAs(decoder);
        assertThat(serde().errorDecoder(args.errorNameToExceptionTypeMarkers())).isNotSameAs(decoder);
    }

    @Test
    void emptyErrorsShareOneDecoder() {
        ConjureBodySerDe serde = serde();
        ExceptionDeserializingErrorDecoder empty = serde.errorDecoder(ImmutableMap.of());
        assertThat(serde.errorDecoder(ExceptionDeserializerArgs.<String>builder()
                        .returnType(TypeMarker.of(String.class))
                        .build()
                        .errorNameToExceptionTypeMarkers()))
                .isSameAs(empty);
    }

    @Test
    void cacheKeysDoNotRetainDecoders() throws InterruptedException {
        // Hold the cache key itself, so this checks the key and not whether Caffeine happened to evict the entry
        // once the deserializer was collected.
        ConjureBodySerDe serde = serde();
        List<Object> keys = new ArrayList<>();
        List<WeakReference<?>> references = bindAndDrop(serde, keys);
        try {
            awaitCollected(references);
        } finally {
            Reference.reachabilityFence(keys);
            Reference.reachabilityFence(serde);
        }
    }

    private static List<WeakReference<?>> bindAndDrop(ConjureBodySerDe serde, List<Object> keys) {
        ExceptionDeserializerArgs<String> args = ExceptionDeserializerArgs.<String>builder()
                .returnType(TypeMarker.of(String.class))
                .exception("Test:Dropped", ERROR, EXCEPTION)
                .build();
        Deserializer<String> deserializer = serde.deserializer(args);
        ExceptionDeserializingErrorDecoder decoder = serde.errorDecoder(args.errorNameToExceptionTypeMarkers());
        assertThat(FieldSupport.EXTRACTION.fieldValue("errorDecoder", Object.class, deserializer))
                .isSameAs(decoder);
        keys.addAll(FieldSupport.EXTRACTION
                .fieldValue("exceptionDeserializers", LoadingCache.class, serde)
                .asMap()
                .keySet());
        assertThat(keys).hasSize(1);
        return List.of(new WeakReference<>(deserializer), new WeakReference<>(decoder));
    }

    @Test
    void bindingDoesNotRetainClientsArgsOrDeserializers() throws InterruptedException {
        ConjureBodySerDe serde = serde();
        List<WeakReference<?>> references = bindCapturingMarkers(serde);
        try {
            awaitCollected(references);
        } finally {
            Reference.reachabilityFence(serde);
        }
    }

    @Test
    void decodersDoNotRetainCallerMarkers() throws InterruptedException {
        // Older dialogue-target versions keep the caller's `new TypeMarker<T>() {}` subclasses in their error maps.
        ConjureBodySerDe serde = serde();
        List<Object> decoder = new ArrayList<>();
        List<WeakReference<?>> references = decodeCapturingMarkers(serde, decoder);
        try {
            awaitCollected(references);
        } finally {
            Reference.reachabilityFence(decoder);
            Reference.reachabilityFence(serde);
        }
    }

    private static List<WeakReference<?>> decodeCapturingMarkers(ConjureBodySerDe serde, List<Object> decoder) {
        Object owner = new Object();
        TypeMarker<TestErrorSerializableError> errorType = new TypeMarker<TestErrorSerializableError>() {
            @SuppressWarnings("unused")
            private Object owner() {
                return owner;
            }
        };
        TypeMarker<TestErrorException> exceptionType = new TypeMarker<TestErrorException>() {
            @SuppressWarnings("unused")
            private Object owner() {
                return owner;
            }
        };
        ImmutableMap<String, ErrorExceptionPair<?, ?>> errors =
                ImmutableMap.of("Test:Error", new ErrorExceptionPair<>(errorType, exceptionType));
        decoder.add(serde.errorDecoder(errors));
        return List.of(
                new WeakReference<>(owner),
                new WeakReference<>(errorType),
                new WeakReference<>(exceptionType),
                new WeakReference<>(errors));
    }

    private static List<WeakReference<?>> bindCapturingMarkers(ConjureBodySerDe serde) {
        Object owner = new Object();
        TypeMarker<String> returnType = new TypeMarker<String>() {
            @SuppressWarnings("unused")
            private Object owner() {
                return owner;
            }
        };
        TypeMarker<TestErrorSerializableError> errorType = new TypeMarker<TestErrorSerializableError>() {
            @SuppressWarnings("unused")
            private Object owner() {
                return owner;
            }
        };
        TypeMarker<TestErrorException> exceptionType = new TypeMarker<TestErrorException>() {
            @SuppressWarnings("unused")
            private Object owner() {
                return owner;
            }
        };
        ExceptionDeserializerArgs<String> args = ExceptionDeserializerArgs.<String>builder()
                .returnType(returnType)
                .exception("Test:Error", errorType, exceptionType)
                .build();
        Deserializer<String> deserializer = serde.deserializer(args);
        return List.of(
                new WeakReference<>(owner),
                new WeakReference<>(returnType),
                new WeakReference<>(errorType),
                new WeakReference<>(exceptionType),
                new WeakReference<>(args),
                new WeakReference<>(deserializer));
    }

    private static void awaitCollected(List<WeakReference<?>> references) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (references.stream().anyMatch(ref -> ref.get() != null) && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(25);
        }
        assertThat(references).allSatisfy(ref -> assertThat(ref.get()).isNull());
    }

    private static ConjureBodySerDe serde() {
        return new ConjureBodySerDe(
                ImmutableList.of(WeightedEncoding.of(Encodings.json())),
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);
    }

    private static final class CountingType implements Type {
        final AtomicInteger hashCalls = new AtomicInteger();

        @Override
        public String getTypeName() {
            return TestErrorSerializableError.class.getName();
        }

        @Override
        public int hashCode() {
            hashCalls.incrementAndGet();
            return 17;
        }

        @Override
        public boolean equals(Object other) {
            return this == other;
        }
    }
}
