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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.api.errors.QosReason;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.ComplexArg;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.ConjureError;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.TestErrorException;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.DialogueRetries;
import com.palantir.dialogue.ExceptionDeserializerArgs;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.TypeMarker;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class ExceptionDeserializingDecoderTest {
    private static final ObjectMapper MAPPER = ObjectMappers.newServerObjectMapper();

    @Test
    public void testDeserializeReturnValue() {
        // Given
        String expectedString = "expectedString";
        TestResponse response = TestResponse.withBody(String.format("\"%s\"", expectedString))
                .contentType("application/json")
                .code(200);
        BodySerDe bodySerDe = conjureBodySerDe("application/json", "text/plain");
        ExceptionDeserializerArgs<String> exceptionDeserializerArgs =
                ExceptionDeserializationTestUtils.createStringDeserializerArgs();
        // When
        String value = bodySerDe.deserializer(exceptionDeserializerArgs).deserialize(response);
        // Then
        assertThat(value).isEqualTo(expectedString);
    }

    @ParameterizedTest
    @CsvSource({"429, true", "429, false", "503, true", "503, false"})
    void exhaustion_marker_preserves_qos_error_type_and_metadata(int statusCode, boolean exhausted) {
        TestResponse response = new TestResponse()
                .code(statusCode)
                .withHeader("Qos-Due-To", "custom")
                .withHeader("Qos-Retry-Hint", "do-not-retry")
                .withHeader("Retry-After", "3");
        if (exhausted) {
            DialogueRetries.setRetriesExhausted(response);
        }
        Deserializer<String> deserializer = conjureBodySerDe("application/json")
                .deserializer(ExceptionDeserializationTestUtils.createStringDeserializerArgs());

        Throwable result = catchThrowable(() -> deserializer.deserialize(response));

        assertThat(result).isInstanceOfSatisfying(QosException.class, exception -> {
            assertThat(DialogueRetries.isRetriesExhausted(exception)).isEqualTo(exhausted);
            assertThat(exception.getReason())
                    .isEqualTo(QosReason.builder()
                            .reason("client-qos-response")
                            .dueTo(QosReason.DueTo.CUSTOM)
                            .retryHint(QosReason.RetryHint.DO_NOT_RETRY)
                            .build());
            if (statusCode == 429) {
                assertThat(exception)
                        .isInstanceOfSatisfying(
                                QosException.Throttle.class,
                                throttle -> assertThat(throttle.getRetryAfter()).hasValue(Duration.ofSeconds(3)));
            } else {
                assertThat(exception).isInstanceOf(QosException.Unavailable.class);
            }
        });
        assertThat(response.isClosed()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void exhaustion_marker_preserves_remote_error_type_status_and_body(boolean exhausted) throws IOException {
        ServiceException serviceException =
                ExceptionDeserializationTestUtils.testError("foo", new ComplexArg(1, "bar"));
        SerializableError expectedError = SerializableError.forException(serviceException);
        TestResponse response = TestResponse.withBody(MAPPER.writeValueAsString(expectedError))
                .contentType("application/json")
                .code(400);
        if (exhausted) {
            DialogueRetries.setRetriesExhausted(response);
        }
        ExceptionDeserializerArgs<String> deserializerArgs = ExceptionDeserializerArgs.<String>builder()
                .returnType(new TypeMarker<>() {})
                .build();
        Deserializer<String> deserializer = conjureBodySerDe("application/json").deserializer(deserializerArgs);

        Throwable result = catchThrowable(() -> deserializer.deserialize(response));

        assertThat(result).isInstanceOfSatisfying(RemoteException.class, exception -> {
            assertThat(DialogueRetries.isRetriesExhausted(exception)).isEqualTo(exhausted);
            assertThat(exception.getStatus()).isEqualTo(400);
            assertThat(exception.getError()).isEqualTo(expectedError);
        });
        assertThat(response.isClosed()).isTrue();
    }

    @Test
    void exhaustion_marker_preserves_typed_endpoint_exception() throws IOException {
        ServiceException expectedError = ExceptionDeserializationTestUtils.testError("foo", new ComplexArg(1, "bar"));
        TestResponse response = TestResponse.withBody(MAPPER.writeValueAsString(
                        ConjureError.fromServiceExceptionWithJsonSerializedParameterValues(expectedError)))
                .contentType("application/json")
                .code(500);
        DialogueRetries.setRetriesExhausted(response);
        Deserializer<String> deserializer = conjureBodySerDe("application/json")
                .deserializer(ExceptionDeserializationTestUtils.createStringDeserializerArgs());

        assertThatThrownBy(() -> deserializer.deserialize(response))
                .isInstanceOfSatisfying(TestErrorException.class, exception -> {
                    assertThat(DialogueRetries.isRetriesExhausted(exception)).isTrue();
                    assertThat(exception.getStatus()).isEqualTo(500);
                    ExceptionDeserializationTestUtils.assertRemoteExceptionIsTestErrorException(
                            exception, expectedError.getErrorInstanceId());
                });
        assertThat(response.isClosed()).isTrue();
    }

    /**
     * This test validates that when a server sends the toString representation of parameters over the wire, the
     * deserializer falls back to throwing a RemoteException and fails to create the TestErrorException.
     */
    @Test
    public void testDeserializationFallsBackToRemoteExceptionWhenToStringParamsAreSent() throws IOException {
        // Given
        ServiceException expectedError = ExceptionDeserializationTestUtils.testError("foo", new ComplexArg(1, "bar"));
        String responseBody = MAPPER.writeValueAsString(ConjureError.fromServiceException(expectedError));

        // The server is sending the Objects.toString representation of ComplexArg over the wire. This is not valid
        // JSON.
        assertThat(responseBody).contains("ComplexArg[foo=1, bar=bar]");

        TestResponse response = TestResponse.withBody(responseBody)
                .contentType("application/json")
                .code(500);
        BodySerDe bodySerDe = conjureBodySerDe("application/json", "text/plain");
        ExceptionDeserializerArgs<String> exceptionDeserializerArgs =
                ExceptionDeserializationTestUtils.createStringDeserializerArgs();
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
                    .isEqualTo(ExceptionDeserializationTestUtils.TEST_ERROR_TYPE
                            .code()
                            .name());
            assertThat(serializableError.errorName())
                    .isEqualTo(ExceptionDeserializationTestUtils.TEST_ERROR_TYPE.name());
            assertThat(serializableError.errorInstanceId()).isEqualTo(expectedError.getErrorInstanceId());
            assertThat(serializableError.parameters().get("stringArg")).isEqualTo("foo");
            assertThat(serializableError.parameters().get("complexArg")).isEqualTo("ComplexArg[foo=1, bar=bar]");
            assertThat(e.getSuppressed())
                    .allSatisfy(throwable -> assertThat(throwable.getMessage())
                            .startsWith(ExceptionDeserializingErrorDecoder.ResponseDiagnostic.SAFE_MESSAGE));
        }
    }

    @Test
    public void testThatErrorWithMessageInsteadOfErrorNameIsDeserializedToRemoteException() {
        // For legacy servers that do not follow the Conjure spec and throw errors with a "message" field instead of an
        // "errorName" field, this test verifies that we are still able to deserialize the error to a RemoteException.
        String responseBody =
                // language=JSON
                """
                {
                    "message": "Conjure:TestError",
                    "errorCode":"INVALID_ARGUMENT",
                    "errorInstanceId":"f8795ac5-59cf-4760-92e5-f566ce7978b0",
                    "parameters":{"stringArg":"foo","complexArg":"ComplexArg[foo=1, bar=bar]"}
                }
                """;

        TestResponse response = TestResponse.withBody(responseBody)
                .contentType("application/json")
                .code(500);
        BodySerDe bodySerDe = conjureBodySerDe("application/json", "text/plain");
        ExceptionDeserializerArgs<String> exceptionDeserializerArgs =
                ExceptionDeserializationTestUtils.createStringDeserializerArgs();
        // When
        try {
            bodySerDe.deserializer(exceptionDeserializerArgs).deserialize(response);
        } catch (RemoteException e) {
            assertThat(e).isNotInstanceOf(TestErrorException.class);

            SerializableError serializableError = e.getError();
            assertThat(serializableError.errorCode())
                    .isEqualTo(ExceptionDeserializationTestUtils.TEST_ERROR_TYPE
                            .code()
                            .name());
            assertThat(serializableError.errorName())
                    .isEqualTo(ExceptionDeserializationTestUtils.TEST_ERROR_TYPE.name());
            // assertThat(serializableError.errorInstanceId()).isEqualTo(expectedError.getErrorInstanceId());
            assertThat(serializableError.parameters().get("stringArg")).isEqualTo("foo");
            assertThat(serializableError.parameters().get("complexArg")).isEqualTo("ComplexArg[foo=1, bar=bar]");
            assertThat(e.getSuppressed())
                    .allSatisfy(throwable -> assertThat(throwable.getMessage())
                            .startsWith(ExceptionDeserializingErrorDecoder.ResponseDiagnostic.SAFE_MESSAGE));
        }
    }

    /**
     * This test validates that when a server sends an error that the client has not specified in the mapping from error
     * name -> exception, the deserializer falls back to throwing a RemoteException.
     */
    @Test
    public void testDeserializationFallsBackToRemoteExceptionWhenErrorIsNotKnown() throws IOException {
        // Given
        ServiceException expectedError = ExceptionDeserializationTestUtils.testError("foo", new ComplexArg(1, "bar"));
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
        // The client does not know about TestErrorException, so we do not register it when constructing the
        // deserializer.
        ExceptionDeserializerArgs<String> exceptionDeserializerArgs = ExceptionDeserializerArgs.<String>builder()
                .returnType(new TypeMarker<>() {})
                .build();
        // When
        try {
            bodySerDe.deserializer(exceptionDeserializerArgs).deserialize(response);
        } catch (RemoteException e) {
            // Deserialization should have failed because the deserializer does not know about TestErrorException. We
            // should not throw a TestErrorException here, but rather fallback to throwing a RemoteException.
            assertThat(e).isNotInstanceOf(TestErrorException.class);

            SerializableError serializableError = e.getError();
            assertThat(serializableError.errorCode())
                    .isEqualTo(ExceptionDeserializationTestUtils.TEST_ERROR_TYPE
                            .code()
                            .name());
            assertThat(serializableError.errorName())
                    .isEqualTo(ExceptionDeserializationTestUtils.TEST_ERROR_TYPE.name());
            assertThat(serializableError.errorInstanceId()).isEqualTo(expectedError.getErrorInstanceId());
            assertThat(serializableError.parameters().get("stringArg")).isEqualTo("foo");
            assertThat(serializableError.parameters().get("complexArg")).isEqualTo("{\"foo\":1,\"bar\":\"bar\"}");
            assertThat(e.getSuppressed())
                    .allSatisfy(throwable -> assertThat(throwable.getMessage())
                            .startsWith(ExceptionDeserializingErrorDecoder.ResponseDiagnostic.SAFE_MESSAGE));
        }
    }

    @Test
    public void testDeserializeExpectedException() throws IOException {
        // Given
        ServiceException expectedError = ExceptionDeserializationTestUtils.testError("foo", new ComplexArg(1, "bar"));
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
        ExceptionDeserializerArgs<String> exceptionDeserializerArgs =
                ExceptionDeserializationTestUtils.createStringDeserializerArgs();

        try {
            // When
            bodySerDe.deserializer(exceptionDeserializerArgs).deserialize(response);
        } catch (RemoteException e) {
            // Then
            // The error should have been deserialized as a TestErrorException.
            ExceptionDeserializationTestUtils.assertRemoteExceptionIsTestErrorException(
                    e, expectedError.getErrorInstanceId());
            assertThat(e.getSuppressed())
                    .allSatisfy(throwable -> assertThat(throwable.getMessage())
                            .startsWith(ExceptionDeserializingErrorDecoder.ResponseDiagnostic.SAFE_MESSAGE));
        }
    }

    @Test
    public void testDeserializeVoidReturnType() {
        // Given
        TestResponse response = TestResponse.withBody(null);
        BodySerDe bodySerDe = conjureBodySerDe("application/json", "text/plain");
        ExceptionDeserializerArgs<Void> exceptionDeserializerArgs =
                ExceptionDeserializationTestUtils.createVoidDeserializerArgs();
        // Assert that deserializing a void return type does not throw an exception
        bodySerDe.emptyBodyDeserializer(exceptionDeserializerArgs).deserialize(response);
    }

    @Test
    public void testDeserializeExceptionFromEndpointWithVoidReturnType() throws IOException {
        // Given
        ServiceException expectedError = ExceptionDeserializationTestUtils.testError("foo", new ComplexArg(1, "bar"));
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
        ExceptionDeserializerArgs<Void> exceptionDeserializerArgs =
                ExceptionDeserializationTestUtils.createVoidDeserializerArgs();

        try {
            // When
            bodySerDe.emptyBodyDeserializer(exceptionDeserializerArgs).deserialize(response);
        } catch (RemoteException e) {
            // Then
            // The error should have been deserialized as a TestErrorException.
            ExceptionDeserializationTestUtils.assertRemoteExceptionIsTestErrorException(
                    e, expectedError.getErrorInstanceId());
            assertThat(e.getSuppressed())
                    .allSatisfy(throwable -> assertThat(throwable.getMessage())
                            .startsWith(ExceptionDeserializingErrorDecoder.ResponseDiagnostic.SAFE_MESSAGE));
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
                    ExceptionDeserializationTestUtils.createOptionalInputStreamDeserializerArgs();
            assertThat(bodySerDe
                            .optionalInputStreamDeserializer(deserializerArgs)
                            .deserialize(response))
                    .satisfies(value -> assertThat(value)
                            .isPresent()
                            .satisfies(
                                    optionalInputStream -> assertThat(readAllBytesUnchecked(optionalInputStream::get))
                                            .isEqualTo(binaryData)));
        } else {
            ExceptionDeserializerArgs<InputStream> deserializerArgs =
                    ExceptionDeserializationTestUtils.createInputStreamDeserializerArgs();
            assertThat(bodySerDe.inputStreamDeserializer(deserializerArgs).deserialize(response))
                    .satisfies(value ->
                            assertThat(readAllBytesUnchecked(() -> value)).isEqualTo(binaryData));
        }
    }

    @Test
    public void testDeserializeExceptionThrownFromEndpointWithBinaryReturnType() throws IOException {
        // Given
        ServiceException expectedError = ExceptionDeserializationTestUtils.testError("foo", new ComplexArg(1, "bar"));
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

        ExceptionDeserializerArgs<InputStream> deserializerArgs =
                ExceptionDeserializationTestUtils.createInputStreamDeserializerArgs();

        try {
            // When
            bodySerDe.inputStreamDeserializer(deserializerArgs).deserialize(response);
        } catch (RemoteException e) {
            ExceptionDeserializationTestUtils.assertRemoteExceptionIsTestErrorException(
                    e, expectedError.getErrorInstanceId());
            assertThat(e.getSuppressed())
                    .allSatisfy(throwable -> assertThat(throwable.getMessage())
                            .startsWith(ExceptionDeserializingErrorDecoder.ResponseDiagnostic.SAFE_MESSAGE));
        }
    }

    @Test
    public void testDeserializersAreCached() {
        // Given
        BodySerDe bodySerDe = conjureBodySerDe("application/json", "text/plain");

        // When
        Deserializer<InputStream> inputStreamDeserializer1 = bodySerDe.inputStreamDeserializer(
                ExceptionDeserializationTestUtils.createInputStreamDeserializerArgs());
        Deserializer<InputStream> inputStreamDeserializer2 = bodySerDe.inputStreamDeserializer(
                ExceptionDeserializationTestUtils.createInputStreamDeserializerArgs());

        // Then
        assertThat(inputStreamDeserializer1).isSameAs(inputStreamDeserializer2);

        // When
        Deserializer<Optional<InputStream>> optionalInputStreamDeserializer1 =
                bodySerDe.optionalInputStreamDeserializer(
                        ExceptionDeserializationTestUtils.createOptionalInputStreamDeserializerArgs());
        Deserializer<Optional<InputStream>> optionalInputStreamDeserializer2 =
                bodySerDe.optionalInputStreamDeserializer(
                        ExceptionDeserializationTestUtils.createOptionalInputStreamDeserializerArgs());

        // Then
        assertThat(optionalInputStreamDeserializer1).isSameAs(optionalInputStreamDeserializer2);

        // When
        Deserializer<Void> voidDeserializer1 =
                bodySerDe.emptyBodyDeserializer(ExceptionDeserializationTestUtils.createVoidDeserializerArgs());
        Deserializer<Void> voidDeserializer2 =
                bodySerDe.emptyBodyDeserializer(ExceptionDeserializationTestUtils.createVoidDeserializerArgs());

        // Then
        assertThat(voidDeserializer1).isSameAs(voidDeserializer2);

        // When
        Deserializer<String> stringDeserializer1 =
                bodySerDe.deserializer(ExceptionDeserializationTestUtils.createStringDeserializerArgs());
        Deserializer<String> stringDeserializer2 =
                bodySerDe.deserializer(ExceptionDeserializationTestUtils.createStringDeserializerArgs());

        // Then
        assertThat(stringDeserializer1).isSameAs(stringDeserializer2);
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
