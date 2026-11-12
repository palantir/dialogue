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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableErrorProvider;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.ComplexArg;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.ConjureError;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.TestErrorException;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.TestErrorParameters;
import com.palantir.conjure.java.dialogue.serde.ExceptionDeserializationTestUtils.TestErrorSerializableError;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.ExceptionDeserializerArgs;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.TypeMarker;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * How deserializers bound with {@link ExceptionDeserializerArgs} handle error and successful responses. Error responses
 * must always be closed, so every error test also checks that.
 */
final class ConjureBodySerDeErrorsTest {
    private static final ObjectMapper MAPPER = ObjectMappers.newServerObjectMapper();

    private final BodySerDe serde = new ConjureBodySerDe(
            ImmutableList.of(WeightedEncoding.of(Encodings.json())),
            Encodings.emptyContainerDeserializer(),
            DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);

    /** Each way to bind a deserializer with the test error registered. */
    enum Binding {
        STANDARD,
        EMPTY_BODY,
        INPUT_STREAM,
        OPTIONAL_INPUT_STREAM;

        Deserializer<?> bind(BodySerDe bodySerDe) {
            return switch (this) {
                case STANDARD ->
                    bodySerDe.deserializer(ExceptionDeserializationTestUtils.createStringDeserializerArgs());
                case EMPTY_BODY ->
                    bodySerDe.emptyBodyDeserializer(ExceptionDeserializationTestUtils.createVoidDeserializerArgs());
                case INPUT_STREAM ->
                    bodySerDe.inputStreamDeserializer(
                            ExceptionDeserializationTestUtils.createInputStreamDeserializerArgs());
                case OPTIONAL_INPUT_STREAM ->
                    bodySerDe.optionalInputStreamDeserializer(
                            ExceptionDeserializationTestUtils.createOptionalInputStreamDeserializerArgs());
            };
        }
    }

    @ParameterizedTest
    @EnumSource(Binding.class)
    void registeredErrorsThrowTheirExceptions(Binding binding) throws IOException {
        ServiceException error = ExceptionDeserializationTestUtils.testError("foo", new ComplexArg(1, "bar"));
        TestResponse response = errorResponse(error);

        assertThatThrownBy(() -> binding.bind(serde).deserialize(response))
                .isInstanceOfSatisfying(TestErrorException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(500);
                    assertThat(exception.error().errorInstanceId()).isEqualTo(error.getErrorInstanceId());
                    assertThat(exception.error().parameters())
                            .isEqualTo(new TestErrorParameters("foo", new ComplexArg(1, "bar")));
                    assertThat(exception.getSuppressed())
                            .singleElement()
                            .isInstanceOf(ExceptionDeserializingErrorDecoder.ResponseDiagnostic.class);
                });
        assertThat(response.isClosed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Binding.class)
    void unregisteredErrorsThrowRemoteExceptions(Binding binding) {
        TestResponse response = TestResponse.withBody(
                        // language=JSON
                        """
                        {"errorCode":"NOT_FOUND","errorName":"Test:Unregistered","errorInstanceId":"id","parameters":{}}
                        """)
                .contentType("application/json")
                .code(404);

        assertThatThrownBy(() -> binding.bind(serde).deserialize(response))
                .isExactlyInstanceOf(RemoteException.class)
                .satisfies(exception -> assertThat(
                                ((RemoteException) exception).getError().errorName())
                        .isEqualTo("Test:Unregistered"));
        assertThat(response.isClosed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Binding.class)
    void malformedErrorsThrowUnknownRemoteExceptions(Binding binding) {
        TestResponse response = TestResponse.withBody("{not json")
                .contentType("application/json")
                .code(500);

        assertThatThrownBy(() -> binding.bind(serde).deserialize(response))
                .isInstanceOfSatisfying(
                        UnknownRemoteException.class,
                        exception -> assertThat(exception.getBody()).isEqualTo("{not json"));
        assertThat(response.isClosed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Binding.class)
    void nonJsonErrorsThrowUnknownRemoteExceptions(Binding binding) {
        TestResponse response =
                TestResponse.withBody("Bad gateway").contentType("text/plain").code(502);

        assertThatThrownBy(() -> binding.bind(serde).deserialize(response))
                .isInstanceOfSatisfying(UnknownRemoteException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(502);
                    assertThat(exception.getBody()).isEqualTo("Bad gateway");
                });
        assertThat(response.isClosed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Binding.class)
    void throttledResponsesThrowThrottleExceptions(Binding binding) {
        TestResponse response = TestResponse.withBody("ignored").code(429).withHeader("Retry-After", "3");

        assertThatThrownBy(() -> binding.bind(serde).deserialize(response))
                .isInstanceOfSatisfying(
                        QosException.Throttle.class,
                        exception -> assertThat(exception.getRetryAfter()).contains(Duration.ofSeconds(3)));
        assertThat(response.isClosed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Binding.class)
    void unavailableResponsesThrowUnavailableExceptions(Binding binding) {
        TestResponse response = TestResponse.withBody("ignored").code(503);

        assertThatThrownBy(() -> binding.bind(serde).deserialize(response))
                .isInstanceOf(QosException.Unavailable.class);
        assertThat(response.isClosed()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(Binding.class)
    void redirectsThrowUnknownRemoteExceptionsCausedByRetryOther(Binding binding) {
        TestResponse response =
                TestResponse.withBody("ignored").code(308).withHeader("Location", "https://other.example/path");

        assertThatThrownBy(() -> binding.bind(serde).deserialize(response))
                .isInstanceOf(UnknownRemoteException.class)
                .cause()
                .isInstanceOf(QosException.RetryOther.class);
        assertThat(response.isClosed()).isTrue();
    }

    @Test
    void errorsThrowTheExceptionsTheirEndpointRegistered() throws IOException {
        ServiceException error = ExceptionDeserializationTestUtils.testError("foo", new ComplexArg(1, "bar"));
        Deserializer<String> test =
                serde.deserializer(ExceptionDeserializationTestUtils.createStringDeserializerArgs());
        Deserializer<String> other = serde.deserializer(ExceptionDeserializerArgs.<String>builder()
                .returnType(TypeMarker.of(String.class))
                .exception(
                        ExceptionDeserializationTestUtils.TEST_ERROR_TYPE.name(),
                        TypeMarker.of(TestErrorSerializableError.class),
                        TypeMarker.of(OtherTestErrorException.class))
                .build());

        assertThatThrownBy(() -> test.deserialize(errorResponse(error))).isExactlyInstanceOf(TestErrorException.class);
        assertThatThrownBy(() -> other.deserialize(errorResponse(error)))
                .isExactlyInstanceOf(OtherTestErrorException.class);
    }

    @ParameterizedTest
    @EnumSource(Binding.class)
    void acceptedContentTypesMatchDeserializersWithoutErrors(Binding binding) {
        Optional<String> expected = switch (binding) {
            case STANDARD -> serde.deserializer(TypeMarker.of(String.class)).accepts();
            case EMPTY_BODY -> serde.emptyBodyDeserializer().accepts();
            case INPUT_STREAM -> serde.inputStreamDeserializer().accepts();
            case OPTIONAL_INPUT_STREAM ->
                serde.optionalInputStreamDeserializer().accepts();
        };
        assertThat(binding.bind(serde).accepts()).isEqualTo(expected);
    }

    @Test
    void successfulResponsesAreDeserialized() {
        TestResponse response =
                TestResponse.withBody("\"ok\"").contentType("application/json").code(200);

        assertThat(serde.deserializer(ExceptionDeserializationTestUtils.createStringDeserializerArgs())
                        .deserialize(response))
                .isEqualTo("ok");
    }

    @Test
    void successfulBinaryResponsesAreLeftOpen() throws IOException {
        TestResponse response = new TestResponse(new byte[] {1, 2, 3})
                .contentType("application/octet-stream")
                .code(200);

        try (InputStream body = serde.inputStreamDeserializer(
                        ExceptionDeserializationTestUtils.createInputStreamDeserializerArgs())
                .deserialize(response)) {
            assertThat(response.isClosed()).isFalse();
            assertThat(body.readAllBytes()).containsExactly(1, 2, 3);
        }
    }

    @Test
    void noContentResponsesAreEmptyForOptionalTypes() {
        ExceptionDeserializerArgs<Optional<String>> args = ExceptionDeserializerArgs.<Optional<String>>builder()
                .returnType(new TypeMarker<>() {})
                .exception(
                        ExceptionDeserializationTestUtils.TEST_ERROR_TYPE.name(),
                        TypeMarker.of(TestErrorSerializableError.class),
                        TypeMarker.of(TestErrorException.class))
                .build();
        TestResponse response = new TestResponse().code(204);

        assertThat(serde.deserializer(args).deserialize(response)).isEmpty();
        assertThat(response.isClosed()).isTrue();
    }

    @Test
    void noContentResponsesFailForNonOptionalTypes() {
        TestResponse response = new TestResponse().code(204);

        assertThatThrownBy(() -> serde.deserializer(ExceptionDeserializationTestUtils.createStringDeserializerArgs())
                        .deserialize(response))
                .hasMessageContaining("Unable to deserialize non-optional response type from 204");
        assertThat(response.isClosed()).isTrue();
    }

    @Test
    void emptyBodyResponsesIgnoreTheirContent() {
        TestResponse response =
                TestResponse.withBody("unexpected").contentType("text/plain").code(200);

        assertThat(serde.emptyBodyDeserializer(ExceptionDeserializationTestUtils.createVoidDeserializerArgs())
                        .deserialize(response))
                .isNull();
        assertThat(response.isClosed()).isTrue();
    }

    @Test
    void bindingDoesNotRetainCallerTypeMarkers() throws IOException, InterruptedException {
        List<Object> deserializer = new ArrayList<>();
        List<WeakReference<?>> references = bindWithCapturingMarkers(deserializer);
        awaitCollected(references);

        // The deserializer keeps working after the caller's markers, and what they captured, are gone.
        ServiceException error = ExceptionDeserializationTestUtils.testError("foo", new ComplexArg(1, "bar"));
        @SuppressWarnings("unchecked")
        Deserializer<String> bound = (Deserializer<String>) deserializer.get(0);
        assertThatThrownBy(() -> bound.deserialize(errorResponse(error))).isInstanceOf(TestErrorException.class);
    }

    private List<WeakReference<?>> bindWithCapturingMarkers(List<Object> deserializer) {
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
                .exception(ExceptionDeserializationTestUtils.TEST_ERROR_TYPE.name(), errorType, exceptionType)
                .build();
        deserializer.add(serde.deserializer(args));
        return List.of(
                new WeakReference<>(owner),
                new WeakReference<>(returnType),
                new WeakReference<>(errorType),
                new WeakReference<>(exceptionType),
                new WeakReference<>(args));
    }

    private static void awaitCollected(List<WeakReference<?>> references) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (references.stream().anyMatch(ref -> ref.get() != null) && System.nanoTime() < deadline) {
            System.gc();
            Thread.sleep(25);
        }
        assertThat(references).allSatisfy(ref -> assertThat(ref.get()).isNull());
    }

    private static TestResponse errorResponse(ServiceException error) throws IOException {
        return new TestResponse(MAPPER.writeValueAsString(
                                ConjureError.fromServiceExceptionWithJsonSerializedParameterValues(error))
                        .getBytes(StandardCharsets.UTF_8))
                .contentType("application/json")
                .code(500);
    }

    /** Another exception for the test error, as a different endpoint's generated code might register. */
    public static final class OtherTestErrorException extends RemoteException
            implements SerializableErrorProvider<TestErrorParameters> {
        private final TestErrorSerializableError error;

        // Constructor needs to be public so that the exception can be created via reflection
        @SuppressWarnings("RedundantModifier")
        public OtherTestErrorException(TestErrorSerializableError error, int status) {
            super(error.toSerializableError(), status);
            this.error = error;
        }

        @Override
        public TestErrorSerializableError error() {
            return error;
        }
    }
}
