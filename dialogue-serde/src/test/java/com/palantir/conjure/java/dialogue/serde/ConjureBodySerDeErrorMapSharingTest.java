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

import com.github.benmanes.caffeine.cache.Cache;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.TestErrorException;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.TestErrorSerializableError;
import com.palantir.dialogue.ExceptionDeserializerArgs;
import com.palantir.dialogue.TypeMarker;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

final class ConjureBodySerDeErrorMapSharingTest {

    private final ConjureBodySerDe bodySerDe = new ConjureBodySerDe(
            DefaultConjureRuntime.DEFAULT_ENCODINGS,
            Encodings.emptyContainerDeserializer(),
            DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);

    @Test
    void equalErrorMapsShareAcrossEndpointsAndCacheKeys() throws ReflectiveOperationException {
        Object first =
                bodySerDe.deserializer(exceptionArgs(TypeMarker.of(String.class), "Conjure:First", "Conjure:Second"));
        Object second =
                bodySerDe.deserializer(exceptionArgs(TypeMarker.of(Integer.class), "Conjure:Second", "Conjure:First"));
        Map<?, ?> errors = errorMap(first);
        assertThat(errorMap(second)).isSameAs(errors);

        Cache<?, ?> cache = (Cache<?, ?>) field(bodySerDe, "exceptionDeserializers");
        assertThat(cache.asMap()).hasSize(2);
        for (Object key : cache.asMap().keySet()) {
            assertThat(field(key, "errors")).isSameAs(errors);
        }

        // Endpoints still holding a map must share it even after their cache entries expire or are evicted.
        cache.invalidateAll();
        Object third =
                bodySerDe.deserializer(exceptionArgs(TypeMarker.of(Long.class), "Conjure:First", "Conjure:Second"));
        assertThat(errorMap(third)).isSameAs(errors);
    }

    @Test
    void errorNamesAndTypesRemainPartOfCacheKey() {
        Object first =
                bodySerDe.deserializer(exceptionArgs(TypeMarker.of(String.class), "Conjure:First", "Conjure:Second"));
        assertThat(bodySerDe.deserializer(
                        exceptionArgs(TypeMarker.of(String.class), "Conjure:Second", "Conjure:First")))
                .isSameAs(first);
        assertThat(bodySerDe.deserializer(
                        exceptionArgs(TypeMarker.of(String.class), "Conjure:Other", "Conjure:Second")))
                .isNotSameAs(first);
        assertThat(bodySerDe.deserializer(exceptionArgs(TypeMarker.of(String.class), "Conjure:First")))
                .isNotSameAs(first);

        Object differentTypes = bodySerDe.deserializer(ExceptionDeserializerArgs.<String>builder()
                .returnType(TypeMarker.of(String.class))
                .exception(
                        "Conjure:First",
                        TypeMarker.of(ExceptionDeserializingDecoderTest.CountedTestError.class),
                        TypeMarker.of(ExceptionDeserializingDecoderTest.CountedTestErrorException.class))
                .exception(
                        "Conjure:Second",
                        TypeMarker.of(TestErrorSerializableError.class),
                        TypeMarker.of(TestErrorException.class))
                .build());
        assertThat(differentTypes).isNotSameAs(first);
    }

    @Test
    void errorMapsAreNotSharedBetweenRuntimes() throws ReflectiveOperationException {
        Object first = bodySerDe.deserializer(exceptionArgs(TypeMarker.of(String.class), "Conjure:First"));
        Object second = DefaultConjureRuntime.builder()
                .build()
                .bodySerDe()
                .deserializer(exceptionArgs(TypeMarker.of(String.class), "Conjure:First"));
        Map<?, ?> errors = errorMap(first);
        Map<?, ?> otherErrors = errorMap(second);
        assertThat(otherErrors).isNotSameAs(errors);
        assertThat(otherErrors.get("Conjure:First")).isNotSameAs(errors.get("Conjure:First"));
    }

    @Test
    void unusedErrorMapsAndDeserializersCanBeCollected() throws ReflectiveOperationException {
        List<WeakReference<?>> references = cacheUnusedErrorMap();
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            System.gc();
            ((Cache<?, ?>) field(bodySerDe, "exceptionDeserializers")).cleanUp();
            ((Cache<?, ?>) field(bodySerDe, "errorDeserializers")).cleanUp();
            references.forEach(reference -> assertThat(reference.get()).isNull());
        });
    }

    private List<WeakReference<?>> cacheUnusedErrorMap() throws ReflectiveOperationException {
        Object deserializer = bodySerDe.deserializer(exceptionArgs(TypeMarker.of(String.class), "Conjure:First"));
        Map<?, ?> errors = errorMap(deserializer);
        return List.of(
                new WeakReference<>(deserializer),
                new WeakReference<>(errors),
                new WeakReference<>(errors.get("Conjure:First")));
    }

    private static Map<?, ?> errorMap(Object deserializer) throws ReflectiveOperationException {
        return (Map<?, ?>) field(field(deserializer, "errorDecoder"), "errorDeserializers");
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static <T> ExceptionDeserializerArgs<T> exceptionArgs(TypeMarker<T> returnType, String... errorNames) {
        ExceptionDeserializerArgs.Builder<T> builder =
                ExceptionDeserializerArgs.<T>builder().returnType(returnType);
        for (String errorName : errorNames) {
            builder.exception(
                    errorName,
                    TypeMarker.of(TestErrorSerializableError.class),
                    TypeMarker.of(TestErrorException.class));
        }
        return builder.build();
    }
}
