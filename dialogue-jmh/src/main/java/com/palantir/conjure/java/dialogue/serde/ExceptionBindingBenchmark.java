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

import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.errors.AbstractSerializableError;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.SerializableErrorProvider;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.ExceptionDeserializerArgs;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@SuppressWarnings({"VisibilityModifier", "DesignForExtension"})
public class ExceptionBindingBenchmark {
    @Param({"0", "1", "16", "90", "256"})
    public int errors;

    private BodySerDe serde;
    private ExceptionDeserializerArgs<String> args;
    private Deserializer<String> retained;

    @Setup
    @SuppressWarnings("ReferenceEquality") // Sharing the exact deserializer is the cache contract under test.
    public void before() {
        serde = new ConjureBodySerDe(
                ImmutableList.of(WeightedEncoding.of(Encodings.json())),
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);
        args = args();
        retained = serde.deserializer(args);
        if (serde.deserializer(args()) != retained) {
            throw new SafeIllegalStateException("Equal argument objects must share their deserializer");
        }
    }

    private ExceptionDeserializerArgs<String> args() {
        return args(TypeMarker.of(String.class));
    }

    private <T> ExceptionDeserializerArgs<T> args(TypeMarker<T> returnType) {
        ExceptionDeserializerArgs.Builder<T> builder =
                ExceptionDeserializerArgs.<T>builder().returnType(returnType);
        for (int index = 0; index < errors; index++) {
            builder.exception(
                    "Test:Error" + index, TypeMarker.of(BenchmarkError.class), TypeMarker.of(BenchmarkException.class));
        }
        return builder.build();
    }

    @Benchmark
    public Deserializer<String> bindReusedArguments() {
        return serde.deserializer(args);
    }

    @Benchmark
    public Deserializer<String> bindFreshArguments() {
        return serde.deserializer(args());
    }

    /** Like a generated client: one set of arguments per return type, all registering the same errors. */
    @Benchmark
    public Deserializer<?> bindRotatingReturnTypes(RotatingArguments rotating) {
        return serde.deserializer(rotating.next());
    }

    @State(Scope.Thread)
    public static class RotatingArguments {
        // Below the exception deserializer cache's maximum size, so every binding is a cache hit.
        @Param({"128", "512"})
        public int bindings;

        private final List<ExceptionDeserializerArgs<?>> arguments = new ArrayList<>();
        // The cache holds deserializers weakly, so keep them like a client would, or a GC turns hits into misses.
        private final List<Deserializer<?>> retained = new ArrayList<>();
        private int index;

        @Setup
        public void before(ExceptionBindingBenchmark shared) {
            for (Type returnType : returnTypes(bindings)) {
                ExceptionDeserializerArgs<?> next = shared.args(TypeMarker.of(returnType));
                arguments.add(next);
                retained.add(shared.serde.deserializer(next));
            }
        }

        ExceptionDeserializerArgs<?> next() {
            ExceptionDeserializerArgs<?> result = arguments.get(index);
            index = (index + 1) % arguments.size();
            return result;
        }
    }

    private static List<Type> returnTypes(int count) {
        List<Type> types = new ArrayList<>(List.of(
                String.class,
                Integer.class,
                Long.class,
                Double.class,
                Boolean.class,
                UUID.class,
                Instant.class,
                OffsetDateTime.class));
        for (int next = 0; types.size() < count; next++) {
            Type element = types.get(next);
            types.add(new WrappedType(List.class, element));
            types.add(new WrappedType(Set.class, element));
            types.add(new WrappedType(Optional.class, element));
        }
        return types.subList(0, count);
    }

    private record WrappedType(Class<?> rawType, Type element) implements ParameterizedType {
        @Override
        public Type[] getActualTypeArguments() {
            return new Type[] {element};
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }

        @Override
        public String getTypeName() {
            return rawType.getName() + "<" + element.getTypeName() + ">";
        }
    }

    public static final class BenchmarkError extends AbstractSerializableError<String> {
        BenchmarkError() {
            super("INVALID_ARGUMENT", "Test:Error", "instance", "parameters");
        }
    }

    public static final class BenchmarkException extends RemoteException implements SerializableErrorProvider<String> {
        private final BenchmarkError error;

        public BenchmarkException(BenchmarkError error, int status) {
            super(
                    SerializableError.builder()
                            .errorCode(error.errorCode())
                            .errorName(error.errorName())
                            .errorInstanceId(error.errorInstanceId())
                            .build(),
                    status);
            this.error = error;
        }

        @Override
        public BenchmarkError error() {
            return error;
        }
    }
}
