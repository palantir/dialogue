/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.palantir.conjure.java.api.errors.AbstractSerializableError;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.SerializableErrorProvider;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.dialogue.ExceptionDeserializerArgs;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.UnsafeArg;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;
import javax.annotation.processing.Generated;
import org.jspecify.annotations.Nullable;

final class ExceptionDeserializationTestUtils {
    private ExceptionDeserializationTestUtils() {}

    static final ErrorType TEST_ERROR_TYPE = ErrorType.create(ErrorType.Code.INVALID_ARGUMENT, "Conjure:TestError");

    static ServiceException testError(@Safe String stringArg, @Unsafe ComplexArg complexArg) {
        return new ServiceException(
                TEST_ERROR_TYPE, SafeArg.of("stringArg", stringArg), UnsafeArg.of("complexArg", complexArg));
    }

    @Generated("by conjure-java")
    record ComplexArg(@JsonProperty("foo") @Safe int foo, @JsonProperty("bar") @Unsafe String bar) {}

    @Generated("by conjure-java")
    record TestErrorParameters(
            @JsonProperty("stringArg") @Safe String stringArg,
            @JsonProperty("complexArg") @Unsafe ComplexArg complexArg) {}

    @Generated("by conjure-java")
    static final class TestErrorSerializableError extends AbstractSerializableError<TestErrorParameters> {
        @Nullable
        private final Map<String, String> legacyParameters;

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        TestErrorSerializableError(
                @JsonProperty("errorCode") @Safe String errorCode,
                @JsonProperty("errorName") @Safe String errorName,
                @JsonProperty("errorInstanceId") @Safe String errorInstanceId,
                @JsonProperty("parameters") TestErrorParameters parameters,
                @JsonProperty("legacyParameters") @Nullable Map<String, String> legacyParameters) {
            super(errorCode, errorName, errorInstanceId, parameters);
            this.legacyParameters = legacyParameters;
        }

        SerializableError toSerializableError() {
            SerializableError.Builder builder = SerializableError.builder();
            if (legacyParameters != null) {
                builder.putAllParameters(legacyParameters);
            } else {
                builder.putParameters("stringArg", Objects.toString(parameters().stringArg()))
                        .putParameters(
                                "complexArg", Objects.toString(parameters().complexArg()));
            }
            builder.errorCode(errorCode()).errorName(errorName()).errorInstanceId(errorInstanceId());
            return builder.build();
        }
    }

    @Generated("by conjure-java")
    public static final class TestErrorException extends RemoteException
            implements SerializableErrorProvider<TestErrorParameters> {
        private final TestErrorSerializableError error;

        // Constructor needs to be public so that the exception can be created via reflection
        @SuppressWarnings("RedundantModifier")
        public TestErrorException(TestErrorSerializableError error, int status) {
            super(error.toSerializableError(), status);
            this.error = error;
        }

        @Override
        public TestErrorSerializableError error() {
            return error;
        }
    }

    static ExceptionDeserializerArgs<InputStream> createInputStreamDeserializerArgs() {
        return ExceptionDeserializerArgs.<InputStream>builder()
                .returnType(new TypeMarker<>() {})
                .exception(
                        ExceptionDeserializationTestUtils.TEST_ERROR_TYPE.name(),
                        new TypeMarker<TestErrorSerializableError>() {},
                        new TypeMarker<TestErrorException>() {})
                .build();
    }

    static ExceptionDeserializerArgs<Optional<InputStream>> createOptionalInputStreamDeserializerArgs() {
        return ExceptionDeserializerArgs.<Optional<InputStream>>builder()
                .returnType(new TypeMarker<>() {})
                .exception(
                        ExceptionDeserializationTestUtils.TEST_ERROR_TYPE.name(),
                        new TypeMarker<TestErrorSerializableError>() {},
                        new TypeMarker<TestErrorException>() {})
                .build();
    }

    static ExceptionDeserializerArgs<Void> createVoidDeserializerArgs() {
        return ExceptionDeserializerArgs.<Void>builder()
                .returnType(new TypeMarker<>() {})
                .exception(
                        ExceptionDeserializationTestUtils.TEST_ERROR_TYPE.name(),
                        new TypeMarker<TestErrorSerializableError>() {},
                        new TypeMarker<TestErrorException>() {})
                .build();
    }

    static ExceptionDeserializerArgs<String> createStringDeserializerArgs() {
        return ExceptionDeserializerArgs.<String>builder()
                .returnType(new TypeMarker<>() {})
                .exception(
                        ExceptionDeserializationTestUtils.TEST_ERROR_TYPE.name(),
                        new TypeMarker<TestErrorSerializableError>() {},
                        new TypeMarker<TestErrorException>() {})
                .build();
    }

    static void assertRemoteExceptionIsTestErrorException(RemoteException exp, String expectedErrorInstanceId) {
        assertThat(exp).isInstanceOfSatisfying(TestErrorException.class, exception -> {
            TestErrorSerializableError error = exception.error();
            assertThat(error.errorCode())
                    .isEqualTo(ExceptionDeserializationTestUtils.TEST_ERROR_TYPE
                            .code()
                            .name());
            assertThat(error.errorName()).isEqualTo(ExceptionDeserializationTestUtils.TEST_ERROR_TYPE.name());
            assertThat(error.errorInstanceId()).isEqualTo(expectedErrorInstanceId);
            assertThat(error.parameters().stringArg()).isEqualTo("foo");
            assertThat(error.parameters().complexArg().foo()).isEqualTo(1);
            assertThat(error.parameters().complexArg().bar()).isEqualTo("bar");
        });
    }

    record ConjureError(
            @JsonProperty("errorCode") String errorCode,
            @JsonProperty("errorName") String errorName,
            @JsonProperty("errorInstanceId") String errorInstanceId,
            @JsonProperty("parameters") Map<String, Object> parameters) {

        static ConjureError fromServiceException(ServiceException exception) {
            Map<String, Object> parameters = new HashMap<>();
            for (Arg<?> arg : exception.getArgs()) {
                parameters.put(arg.getName(), Objects.toString(arg.getValue()));
            }
            ErrorType errorType = exception.getErrorType();
            return new ConjureError(
                    errorType.code().name(), errorType.name(), exception.getErrorInstanceId(), parameters);
        }

        /**
         * Creates a {@link ConjureError} from a {@link ServiceException} where the parameter values are serialized as
         * JSON. Currently, this should only be used when clients send a request with a custom header specifying the
         * parameter serialization format: <code>Accept-Conjure-Error-Parameter-Format</code> with value
         * <code>JSON</code>.
         */
        static ConjureError fromServiceExceptionWithJsonSerializedParameterValues(ServiceException exception) {
            Map<String, Object> parameters = getParametersFromArgs(exception.getArgs());
            ErrorType errorType = exception.getErrorType();
            return new ConjureError(
                    errorType.code().name(), errorType.name(), exception.getErrorInstanceId(), parameters);
        }

        /**
         * Construct the parameters map from the provided arguments. Parameters are included if they are not null and
         * not {@link Optional#empty}.
         */
        private static Map<String, Object> getParametersFromArgs(Iterable<Arg<?>> args) {
            Map<String, Object> parameters = new HashMap<>();
            for (Arg<?> arg : args) {
                if (shouldIncludeArgInParameters(arg)) {
                    parameters.put(arg.getName(), arg.getValue());
                }
            }
            return parameters;
        }

        private static boolean shouldIncludeArgInParameters(Arg<?> arg) {
            Object obj = arg.getValue();
            return obj != null
                    && (!(obj instanceof Optional) || ((Optional<?>) obj).isPresent())
                    && (!(obj instanceof OptionalInt optionalInt) || optionalInt.isPresent())
                    && (!(obj instanceof OptionalLong optionalLong) || optionalLong.isPresent())
                    && (!(obj instanceof OptionalDouble optionalDouble) || optionalDouble.isPresent());
        }
    }

    /** Deserializes requests as the type. */
    static final class TypeReturningStubEncoding implements Encoding {

        private final String contentType;
        private final Function<TypeMarker<?>, Deserializer<?>> deserializerFactory;
        private final Map<TypeMarker<?>, Encoding.Deserializer<?>> deserializers = new HashMap<>();

        TypeReturningStubEncoding(String contentType) {
            this(contentType, typeMarker -> Encodings.json().deserializer(typeMarker));
        }

        TypeReturningStubEncoding(
                String contentType, Function<TypeMarker<?>, Encoding.Deserializer<?>> deserializerFactory) {
            this.contentType = contentType;
            this.deserializerFactory = deserializerFactory;
        }

        @Override
        public <T> Encoding.Serializer<T> serializer(TypeMarker<T> _type) {
            return (_value, _output) -> {
                // nop
            };
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Encoding.Deserializer<T> deserializer(TypeMarker<T> type) {
            return input -> {
                Deserializer<T> deserializer =
                        (Deserializer<T>) deserializers.computeIfAbsent(type, deserializerFactory);
                return deserializer.deserialize(input);
            };
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean supportsContentType(String input) {
            return contentType.equals(input);
        }

        @Override
        public String toString() {
            return "TypeReturningStubEncoding{" + contentType + '}';
        }

        @SuppressWarnings("unchecked")
        public <T> Encoding.Deserializer<T> getDeserializer(TypeMarker<T> type) {
            return (Deserializer<T>) deserializers.get(type);
        }
    }
}
