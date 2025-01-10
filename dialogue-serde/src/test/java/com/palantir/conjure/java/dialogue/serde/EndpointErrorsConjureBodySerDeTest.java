/*
 * (c) Copyright 2024 Palantir Technologies Inc. All rights reserved.
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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.errors.CheckedServiceException;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.dialogue.serde.EndpointErrorTestUtils.ConjureError;
import com.palantir.conjure.java.dialogue.serde.EndpointErrorTestUtils.ContentRecordingJsonDeserializer;
import com.palantir.conjure.java.dialogue.serde.EndpointErrorTestUtils.EndpointError;
import com.palantir.conjure.java.dialogue.serde.EndpointErrorTestUtils.TypeReturningStubEncoding;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.DeserializerArgs;
import com.palantir.dialogue.TestResponse;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.Safe;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.Unsafe;
import com.palantir.logsafe.UnsafeArg;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.processing.Generated;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EndpointErrorsConjureBodySerDeTest {
    private static final ObjectMapper MAPPER = ObjectMappers.newServerObjectMapper();

    @Generated("by conjure-java")
    private sealed interface EndpointReturnBaseType permits ExpectedReturnValue, ErrorReturnValue {}

    @Generated("by conjure-java")
    record ExpectedReturnValue(String value) implements EndpointReturnBaseType {
        @JsonCreator
        public static ExpectedReturnValue create(String value) {
            return new ExpectedReturnValue(Preconditions.checkArgumentNotNull(value, "value cannot be null"));
        }
    }

    @Generated("by conjure-java")
    record ComplexArg(int foo, String bar) {}

    @Generated("by conjure-java")
    record ErrorForEndpointArgs(
            @JsonProperty("arg") @Safe String arg,
            @JsonProperty("unsafeArg") @Unsafe String unsafeArg,
            @JsonProperty("complexArg") @Safe ComplexArg complexArg,
            @JsonProperty("optionalArg") @Safe Optional<Integer> optionalArg) {}

    static final class ErrorReturnValue extends EndpointError<ErrorForEndpointArgs> implements EndpointReturnBaseType {
        @JsonCreator
        ErrorReturnValue(
                @JsonProperty("errorCode") String errorCode,
                @JsonProperty("errorName") String errorName,
                @JsonProperty("errorInstanceId") String errorInstanceId,
                @JsonProperty("parameters") ErrorForEndpointArgs args) {
            super(errorCode, errorName, errorInstanceId, args);
        }
    }

    @Generated("by conjure-java")
    public static final class TestEndpointError extends CheckedServiceException {
        private TestEndpointError(
                @Safe String arg,
                @Unsafe String unsafeArg,
                @Safe ComplexArg complexArg,
                @Safe Optional<Integer> optionalArg,
                @Nullable Throwable cause) {
            super(
                    ErrorType.FAILED_PRECONDITION,
                    cause,
                    SafeArg.of("arg", arg),
                    UnsafeArg.of("unsafeArg", unsafeArg),
                    SafeArg.of("complexArg", complexArg),
                    SafeArg.of("optionalArg", optionalArg));
        }
    }

    // The error should be deserialized using Encodings.json(), when a JSON encoding is not provided.
    @ParameterizedTest
    @ValueSource(strings = {"application/json", "text/plain"})
    public void testDeserializeCustomError(String supportedContentType) throws IOException {
        // Given
        TestEndpointError errorThrownByEndpoint =
                new TestEndpointError("value", "unsafeValue", new ComplexArg(1, "bar"), Optional.of(2), null);
        String responseBody =
                MAPPER.writeValueAsString(ConjureError.fromCheckedServiceException(errorThrownByEndpoint));

        TestResponse response = TestResponse.withBody(responseBody)
                .contentType("application/json")
                .code(500);
        BodySerDe serializers = conjureBodySerDe(supportedContentType);
        DeserializerArgs<EndpointReturnBaseType> deserializerArgs = DeserializerArgs.<EndpointReturnBaseType>builder()
                .baseType(new TypeMarker<>() {})
                .success(new TypeMarker<ExpectedReturnValue>() {})
                .error("Default:FailedPrecondition", new TypeMarker<ErrorReturnValue>() {})
                .build();

        // When
        EndpointErrorsConjureBodySerDeTest.EndpointReturnBaseType value =
                serializers.deserializer(deserializerArgs).deserialize(response);

        // Then
        ErrorReturnValue expectedErrorForEndpoint = new ErrorReturnValue(
                ErrorType.FAILED_PRECONDITION.code().name(),
                ErrorType.FAILED_PRECONDITION.name(),
                errorThrownByEndpoint.getErrorInstanceId(),
                new ErrorForEndpointArgs("value", "unsafeValue", new ComplexArg(1, "bar"), Optional.of(2)));
        assertThat(value).isInstanceOf(ErrorReturnValue.class);
        assertThat(value)
                .extracting("errorCode", "errorName", "errorInstanceId", "args")
                .containsExactly(
                        expectedErrorForEndpoint.errorCode,
                        expectedErrorForEndpoint.errorName,
                        expectedErrorForEndpoint.errorInstanceId,
                        expectedErrorForEndpoint.args);
    }

    // When an error is deserialized, but the error type is not registered, the error should be deserialized as a
    // SerializableError and a RemoteException should be thrown.
    @Test
    public void testDeserializingUndefinedErrorFallsbackToSerializableError() throws IOException {
        TestEndpointError errorThrownByEndpoint =
                new TestEndpointError("value", "unsafeValue", new ComplexArg(1, "bar"), Optional.of(2), null);
        String responseBody =
                MAPPER.writeValueAsString(ConjureError.fromCheckedServiceException(errorThrownByEndpoint));

        TestResponse response = TestResponse.withBody(responseBody)
                .contentType("application/json")
                .code(500);
        BodySerDe serializers = conjureBodySerDe("application/json", "text/plain");
        DeserializerArgs<EndpointReturnBaseType> deserializerArgs = DeserializerArgs.<EndpointReturnBaseType>builder()
                .baseType(new TypeMarker<>() {})
                .success(new TypeMarker<ExpectedReturnValue>() {})
                // Note: no error types are registered.
                .build();

        // Then
        assertThatExceptionOfType(RemoteException.class)
                .isThrownBy(() -> {
                    serializers.deserializer(deserializerArgs).deserialize(response);
                })
                .satisfies(exception -> {
                    SerializableError error = exception.getError();
                    assertThat(error.errorCode())
                            .isEqualTo(ErrorType.FAILED_PRECONDITION.code().name());
                    assertThat(error.errorInstanceId()).isEqualTo(errorThrownByEndpoint.getErrorInstanceId());
                    assertThat(error.errorName()).isEqualTo(ErrorType.FAILED_PRECONDITION.name());
                    assertThat(error.parameters())
                            .extracting("arg", "unsafeArg", "complexArg", "optionalArg")
                            .containsExactly(
                                    "value",
                                    "unsafeValue",
                                    MAPPER.writeValueAsString(new ComplexArg(1, "bar")),
                                    MAPPER.writeValueAsString(Optional.of(2)));
                });
    }

    @Test
    public void testDeserializeExpectedValue() {
        // Given
        String expectedString = "expectedString";
        TestResponse response = TestResponse.withBody(String.format("\"%s\"", expectedString))
                .contentType("application/json")
                .code(200);
        BodySerDe serializers = conjureBodySerDe("application/json", "text/plain");
        DeserializerArgs<EndpointReturnBaseType> deserializerArgs = DeserializerArgs.<EndpointReturnBaseType>builder()
                .baseType(new TypeMarker<>() {})
                .success(new TypeMarker<ExpectedReturnValue>() {})
                .error("Default:FailedPrecondition", new TypeMarker<ErrorReturnValue>() {})
                .build();
        // When
        EndpointReturnBaseType value =
                serializers.deserializer(deserializerArgs).deserialize(response);
        // Then
        assertThat(value).isEqualTo(new ExpectedReturnValue(expectedString));
    }

    // Ensure that the supplied JSON encoding is used when available.
    @Test
    public void testDeserializeWithCustomEncoding() throws JsonProcessingException {
        // Given
        TestEndpointError errorThrownByEndpoint =
                new TestEndpointError("value", "unsafeValue", new ComplexArg(1, "bar"), Optional.of(2), null);
        String responseBody =
                MAPPER.writeValueAsString(ConjureError.fromCheckedServiceException(errorThrownByEndpoint));

        TypeReturningStubEncoding stubbingEncoding =
                new TypeReturningStubEncoding("application/json", ContentRecordingJsonDeserializer::new);
        BodySerDe serializers = new ConjureBodySerDe(
                List.of(WeightedEncoding.of(stubbingEncoding)),
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);
        TestResponse response = TestResponse.withBody(responseBody)
                .contentType("application/json")
                .code(500);

        TypeMarker<ErrorReturnValue> errorTypeMarker = new TypeMarker<>() {};
        DeserializerArgs<EndpointReturnBaseType> deserializerArgs = DeserializerArgs.<EndpointReturnBaseType>builder()
                .baseType(new TypeMarker<>() {})
                .success(new TypeMarker<ExpectedReturnValue>() {})
                .error("Default:FailedPrecondition", errorTypeMarker)
                .build();

        // When
        serializers.deserializer(deserializerArgs).deserialize(response);

        // Then
        assertThat(stubbingEncoding.getDeserializer(errorTypeMarker))
                .isInstanceOfSatisfying(ContentRecordingJsonDeserializer.class, deserializer -> {
                    assertThat(deserializer.getDeserializedContent())
                            .asInstanceOf(InstanceOfAssertFactories.LIST)
                            .containsExactly(responseBody);
                });
    }

    private ConjureBodySerDe conjureBodySerDe(String... contentTypes) {
        return new ConjureBodySerDe(
                Arrays.stream(contentTypes)
                        .map(c -> WeightedEncoding.of(new TypeReturningStubEncoding(c)))
                        .collect(ImmutableList.toImmutableList()),
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);
    }
}
