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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.errors.AbstractSerializableError;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.ConjureError;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.ExceptionDeserializerArgs;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.UnsafeArg;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.annotation.processing.Generated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class ExceptionDeserializingDecoderTest {
    private static final ObjectMapper MAPPER = ObjectMappers.newServerObjectMapper();
    public static final ErrorType TEST_ERROR_TYPE =
            ErrorType.create(ErrorType.Code.INVALID_ARGUMENT, "Conjure:TestError");

    public static ServiceException testError(@Safe String stringArg, @Unsafe ComplexArg complexArg) {
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
    static final class TestErrorException extends RemoteException {
        private final TestErrorSerializableError error;

        // Constructor needs to be public so that the exception can be created via reflection
        @SuppressWarnings("RedundantModifier")
        public TestErrorException(TestErrorSerializableError error, int status) {
            super(error.toSerializableError(), status);
            this.error = error;
        }

        TestErrorSerializableError error() {
            return error;
        }
    }

    @Test
    public void testDeserializeReturnValue() {
        // Given
        String expectedString = "expectedString";
        TestResponse response = TestResponse.withBody(String.format("\"%s\"", expectedString))
                .contentType("application/json")
                .code(200);
        BodySerDe bodySerDe = conjureBodySerDe("application/json", "text/plain");
        ExceptionDeserializerArgs<String> exceptionDeserializerArgs = createStringDeserializerArgs();
        // When
        String value = bodySerDe.deserializer(exceptionDeserializerArgs).deserialize(response);
        // Then
        assertThat(value).isEqualTo(expectedString);
    }

    /**
     * This test validates that when a server sends the toString representation of parameters over the wire, the
     * deserializer falls back to throwing a RemoteException and fails to create the TestErrorException.
     */
    @Test
    public void testDeserializationFallsBackToRemoteExceptionWhenToStringParamsAreSent() throws IOException {
        // Given
        ServiceException expectedError = testError("foo", new ComplexArg(1, "bar"));
        String responseBody = MAPPER.writeValueAsString(ConjureError.fromServiceException(expectedError));

        // The server is sending the Objects.toString representation of ComplexArg over the wire. This is not valid
        // JSON.
        assertThat(responseBody).contains("ComplexArg[foo=1, bar=bar]");

        TestResponse response = TestResponse.withBody(responseBody)
                .contentType("application/json")
                .code(500);
        BodySerDe bodySerDe = conjureBodySerDe("application/json", "text/plain");
        ExceptionDeserializerArgs<String> exceptionDeserializerArgs = createStringDeserializerArgs();
        // When
        try {
            bodySerDe.deserializer(exceptionDeserializerArgs).deserialize(response);
        } catch (RemoteException e) {
            // Deserialization should have failed because `ComplexArg[foo=1, bar=bar]` is not a valid JSON
            // representation of a ComplexArg. We should not throw a TestErrorException here, but rather fallback to
            // throwing a RemoteException.
            assertThat(e).isNotInstanceOf(TestErrorException.class);

            SerializableError serializableError = e.getError();
            assertThat(serializableError.errorCode())
                    .isEqualTo(TEST_ERROR_TYPE.code().name());
            assertThat(serializableError.errorName()).isEqualTo(TEST_ERROR_TYPE.name());
            assertThat(serializableError.errorInstanceId()).isEqualTo(expectedError.getErrorInstanceId());
            assertThat(serializableError.parameters().get("stringArg")).isEqualTo("foo");
            assertThat(serializableError.parameters().get("complexArg")).isEqualTo("ComplexArg[foo=1, bar=bar]");
        }
    }

    @Test
    public void testDeserializeExpectedException() throws IOException {
        // Given
        ServiceException expectedError = testError("foo", new ComplexArg(1, "bar"));
        String responseBody = MAPPER.writeValueAsString(
                ConjureError.fromServiceExceptionWithJsonSerializedParameterValues(expectedError));

        // The server is sending the JSON representation of ComplexArg over the wire.
        String expectedJsonComplexArg = """
            "complexArg":{"foo":1,"bar":"bar"}
        """.strip();
        assertThat(responseBody).contains(expectedJsonComplexArg);

        TestResponse response = TestResponse.withBody(responseBody)
                .contentType("application/json")
                .code(500);
        BodySerDe bodySerDe = conjureBodySerDe("application/json", "text/plain");
        ExceptionDeserializerArgs<String> exceptionDeserializerArgs = createStringDeserializerArgs();

        try {
            // When
            bodySerDe.deserializer(exceptionDeserializerArgs).deserialize(response);
        } catch (RemoteException e) {
            // Then
            // The error should have been deserialized as a TestErrorException.
            assertRemoteExceptionIsTestErrorException(e, expectedError.getErrorInstanceId());
        }
    }

    @Test
    public void testDeserializeVoidReturnType() {
        // Given
        TestResponse response = TestResponse.withBody(null);
        BodySerDe bodySerDe = conjureBodySerDe("application/json", "text/plain");
        ExceptionDeserializerArgs<Void> exceptionDeserializerArgs = createVoidDeserializerArgs();
        // Assert that deserializing a void return type does not throw an exception
        bodySerDe.emptyBodyDeserializer(exceptionDeserializerArgs).deserialize(response);
    }

    @Test
    public void testDeserializeExceptionFromEndpointWithVoidReturnType() throws IOException {
        // Given
        ServiceException expectedError = testError("foo", new ComplexArg(1, "bar"));
        String responseBody = MAPPER.writeValueAsString(
                ConjureError.fromServiceExceptionWithJsonSerializedParameterValues(expectedError));

        // The server is sending the JSON representation of ComplexArg over the wire.
        String expectedJsonComplexArg = """
            "complexArg":{"foo":1,"bar":"bar"}
        """.strip();
        assertThat(responseBody).contains(expectedJsonComplexArg);

        TestResponse response = TestResponse.withBody(responseBody)
                .contentType("application/json")
                .code(500);
        BodySerDe bodySerDe = conjureBodySerDe("application/json", "text/plain");
        ExceptionDeserializerArgs<Void> exceptionDeserializerArgs = createVoidDeserializerArgs();

        try {
            // When
            bodySerDe.emptyBodyDeserializer(exceptionDeserializerArgs).deserialize(response);
        } catch (RemoteException e) {
            // Then
            // The error should have been deserialized as a TestErrorException.
            assertRemoteExceptionIsTestErrorException(e, expectedError.getErrorInstanceId());
        }
    }

    @ParameterizedTest
    @ArgumentsSource(BinaryBodyArgumentsProvider.class)
    public void testDeserializeBinaryReturnType(byte[] binaryData, boolean isOptional) {
        // Given
        TestResponse response = new TestResponse(binaryData)
                .contentType("application/octet-stream")
                .code(200);

        BodySerDe bodySerDe = new ConjureBodySerDe(
                ImmutableList.of(WeightedEncoding.of(BinaryEncoding.INSTANCE)),
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);

        if (isOptional) {
            ExceptionDeserializerArgs<Optional<InputStream>> deserializerArgs =
                    createOptionalInputStreamDeserializerArgs();
            assertThat(bodySerDe
                            .optionalInputStreamDeserializer(deserializerArgs)
                            .deserialize(response))
                    .satisfies(value -> assertThat(value).isPresent().satisfies(optionalInputStream -> assertThat(
                                    readAllBytesUnchecked(optionalInputStream::get))
                            .isEqualTo(binaryData)));
        } else {
            ExceptionDeserializerArgs<InputStream> deserializerArgs = createInputStreamDeserializerArgs();
            assertThat(bodySerDe.inputStreamDeserializer(deserializerArgs).deserialize(response))
                    .satisfies(value ->
                            assertThat(readAllBytesUnchecked(() -> value)).isEqualTo(binaryData));
        }
    }

    @Test
    public void testDeserializeExceptionThrownFromEndpointWithBinaryReturnType() throws IOException {
        // Given
        ServiceException expectedError = testError("foo", new ComplexArg(1, "bar"));
        String responseBody = MAPPER.writeValueAsString(
                ConjureError.fromServiceExceptionWithJsonSerializedParameterValues(expectedError));

        // The server is sending the JSON representation of ComplexArg over the wire.
        String expectedJsonComplexArg = """
            "complexArg":{"foo":1,"bar":"bar"}
        """.strip();
        assertThat(responseBody).contains(expectedJsonComplexArg);

        TestResponse response = TestResponse.withBody(responseBody)
                .contentType("application/json")
                .code(500);

        BodySerDe bodySerDe = new ConjureBodySerDe(
                ImmutableList.of(WeightedEncoding.of(BinaryEncoding.INSTANCE)),
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);

        ExceptionDeserializerArgs<InputStream> deserializerArgs = createInputStreamDeserializerArgs();

        try {
            // When
            bodySerDe.inputStreamDeserializer(deserializerArgs).deserialize(response);
        } catch (RemoteException e) {
            assertRemoteExceptionIsTestErrorException(e, expectedError.getErrorInstanceId());
        }
    }

    @Test
    public void testDeserializersAreCached() {
        // Given
        BodySerDe bodySerDe = conjureBodySerDe("application/json", "text/plain");

        // When
        Deserializer<InputStream> inputStreamDeserializer1 =
                bodySerDe.inputStreamDeserializer(createInputStreamDeserializerArgs());
        Deserializer<InputStream> inputStreamDeserializer2 =
                bodySerDe.inputStreamDeserializer(createInputStreamDeserializerArgs());

        // Then
        assertThat(inputStreamDeserializer1).isSameAs(inputStreamDeserializer2);

        // When
        Deserializer<Optional<InputStream>> optionalInputStreamDeserializer1 =
                bodySerDe.optionalInputStreamDeserializer(createOptionalInputStreamDeserializerArgs());
        Deserializer<Optional<InputStream>> optionalInputStreamDeserializer2 =
                bodySerDe.optionalInputStreamDeserializer(createOptionalInputStreamDeserializerArgs());

        // Then
        assertThat(optionalInputStreamDeserializer1).isSameAs(optionalInputStreamDeserializer2);

        // When
        Deserializer<Void> voidDeserializer1 = bodySerDe.emptyBodyDeserializer(createVoidDeserializerArgs());
        Deserializer<Void> voidDeserializer2 = bodySerDe.emptyBodyDeserializer(createVoidDeserializerArgs());

        // Then
        assertThat(voidDeserializer1).isSameAs(voidDeserializer2);

        // When
        Deserializer<String> stringDeserializer1 = bodySerDe.deserializer(createStringDeserializerArgs());
        Deserializer<String> stringDeserializer2 = bodySerDe.deserializer(createStringDeserializerArgs());

        // Then
        assertThat(stringDeserializer1).isSameAs(stringDeserializer2);
    }

    private static ExceptionDeserializerArgs<InputStream> createInputStreamDeserializerArgs() {
        return ExceptionDeserializerArgs.<InputStream>builder()
                .returnType(new TypeMarker<>() {})
                .exception(
                        TEST_ERROR_TYPE.name(),
                        new TypeMarker<TestErrorSerializableError>() {},
                        new TypeMarker<TestErrorException>() {})
                .build();
    }

    private static ExceptionDeserializerArgs<Optional<InputStream>> createOptionalInputStreamDeserializerArgs() {
        return ExceptionDeserializerArgs.<Optional<InputStream>>builder()
                .returnType(new TypeMarker<>() {})
                .exception(
                        TEST_ERROR_TYPE.name(),
                        new TypeMarker<TestErrorSerializableError>() {},
                        new TypeMarker<TestErrorException>() {})
                .build();
    }

    private static ExceptionDeserializerArgs<Void> createVoidDeserializerArgs() {
        return ExceptionDeserializerArgs.<Void>builder()
                .returnType(new TypeMarker<>() {})
                .exception(
                        TEST_ERROR_TYPE.name(),
                        new TypeMarker<TestErrorSerializableError>() {},
                        new TypeMarker<TestErrorException>() {})
                .build();
    }

    private static ExceptionDeserializerArgs<String> createStringDeserializerArgs() {
        return ExceptionDeserializerArgs.<String>builder()
                .returnType(new TypeMarker<>() {})
                .exception(
                        TEST_ERROR_TYPE.name(),
                        new TypeMarker<TestErrorSerializableError>() {},
                        new TypeMarker<TestErrorException>() {})
                .build();
    }

    private static void assertRemoteExceptionIsTestErrorException(RemoteException exp, String expectedErrorInstanceId) {
        assertThat(exp).isInstanceOfSatisfying(TestErrorException.class, exception -> {
            TestErrorSerializableError error = exception.error();
            assertThat(error.errorCode()).isEqualTo(TEST_ERROR_TYPE.code().name());
            assertThat(error.errorName()).isEqualTo(TEST_ERROR_TYPE.name());
            assertThat(error.errorInstanceId()).isEqualTo(expectedErrorInstanceId);
            assertThat(error.parameters().stringArg()).isEqualTo("foo");
            assertThat(error.parameters().complexArg().foo()).isEqualTo(1);
            assertThat(error.parameters().complexArg().bar()).isEqualTo("bar");
        });
    }

    private static byte[] readAllBytesUnchecked(Supplier<InputStream> stream) {
        try (InputStream is = stream.get()) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ConjureBodySerDe conjureBodySerDe(String... contentTypes) {
        return new ConjureBodySerDe(
                Arrays.stream(contentTypes)
                        .map(c ->
                                WeightedEncoding.of(new ExceptionDeserializationTestUtils.TypeReturningStubEncoding(c)))
                        .collect(ImmutableList.toImmutableList()),
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);
    }

    /**
     * Constructs arguments where the first element is a byte array representing binary data, and the second element is
     * a boolean representing if they should be serialized as an optional binary field or not.
     */
    private static final class BinaryBodyArgumentsProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext _context) {
            return Stream.of(
                    Arguments.of((Object) new byte[] {1, 2, 3}, false),
                    Arguments.of(new byte[] {1, 2, 3}, true),
                    Arguments.of(new byte[] {}, false),
                    Arguments.of(new byte[] {}, true));
        }
    }
}
