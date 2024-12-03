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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.errors.CheckedServiceException;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.dialogue.serde.EndpointErrorTestUtils.ConjureError;
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
import java.util.Optional;
import javax.annotation.Nullable;
import javax.annotation.processing.Generated;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EndpointErrorsConjureBodySerDeTest {
    private static final ObjectMapper MAPPER = ObjectMappers.newServerObjectMapper();
    private ErrorDecoder errorDecoder = ErrorDecoder.INSTANCE;

    @Generated("by conjure-java")
    private sealed interface EndpointReturnBaseType permits StringReturn, ErrorForEndpoint {}

    @Generated("by conjure-java")
    record StringReturn(String value) implements EndpointReturnBaseType {
        @JsonCreator
        public static StringReturn create(String value) {
            return new StringReturn(Preconditions.checkArgumentNotNull(value, "value cannot be null"));
        }
    }

    abstract static class EndpointError<T> {
        @Safe
        String errorCode;

        @Safe
        String errorName;

        @Safe
        String errorInstanceId;

        T args;

        EndpointError(String errorCode, String errorName, String errorInstanceId, T args) {
            this.errorCode = errorCode;
            this.errorName = errorName;
            this.errorInstanceId = errorInstanceId;
            this.args = args;
        }
    }

    record ErrorForEndpointArgs(
            @JsonProperty("arg") @Safe String arg,
            @JsonProperty("unsafeArg") @Unsafe String unsafeArg,
            @JsonProperty("complexArg") @Safe ComplexArg complexArg,
            @JsonProperty("optionalArg") @Safe Optional<Integer> optionalArg) {}

    static final class ErrorForEndpoint extends EndpointError<ErrorForEndpointArgs> implements EndpointReturnBaseType {
        @JsonCreator
        ErrorForEndpoint(
                @JsonProperty("errorCode") String errorCode,
                @JsonProperty("errorName") String errorName,
                @JsonProperty("errorInstanceId") String errorInstanceId,
                @JsonProperty("parameters") ErrorForEndpointArgs args) {
            super(errorCode, errorName, errorInstanceId, args);
        }
    }

    @Generated("by conjure-java")
    record ComplexArg(int foo, String bar) {}

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

    @Test
    public void testDeserializeCustomErrors() throws IOException {
        TestEndpointError errorThrownByEndpoint =
                new TestEndpointError("value", "unsafeValue", new ComplexArg(1, "bar"), Optional.of(2), null);

        ErrorForEndpoint expectedErrorForEndpoint = new ErrorForEndpoint(
                "FAILED_PRECONDITION",
                "Default:FailedPrecondition",
                errorThrownByEndpoint.getErrorInstanceId(),
                new ErrorForEndpointArgs("value", "unsafeValue", new ComplexArg(1, "bar"), Optional.of(2)));

        String responseBody =
                MAPPER.writeValueAsString(ConjureError.fromCheckedServiceException(errorThrownByEndpoint));
        TestResponse response = TestResponse.withBody(responseBody)
                .contentType("application/json")
                .code(500);
        BodySerDe serializers = conjureBodySerDe("application/json", "text/plain");
        DeserializerArgs<EndpointReturnBaseType> deserializerArgs = DeserializerArgs.<EndpointReturnBaseType>builder()
                .withBaseType(new TypeMarker<>() {})
                .withExpectedResult(new TypeMarker<StringReturn>() {})
                .withErrorType("Default:FailedPrecondition", new TypeMarker<ErrorForEndpoint>() {})
                .build();
        EndpointErrorsConjureBodySerDeTest.EndpointReturnBaseType value =
                serializers.deserializer(deserializerArgs).deserialize(response);

        assertThat(value)
                .extracting("errorCode", "errorName", "errorInstanceId", "args")
                .containsExactly(
                        expectedErrorForEndpoint.errorCode,
                        expectedErrorForEndpoint.errorName,
                        expectedErrorForEndpoint.errorInstanceId,
                        expectedErrorForEndpoint.args);
    }

    @Test
    public void testDeserializeExpectedValue() {
        String expectedString = "expectedString";
        TestResponse response = TestResponse.withBody(String.format("\"%s\"", expectedString))
                .contentType("application/json")
                .code(200);
        BodySerDe serializers = conjureBodySerDe("application/json", "text/plain");
        DeserializerArgs<EndpointReturnBaseType> deserializerArgs = DeserializerArgs.<EndpointReturnBaseType>builder()
                .withBaseType(new TypeMarker<>() {})
                .withExpectedResult(new TypeMarker<StringReturn>() {})
                .withErrorType("Default:FailedPrecondition", new TypeMarker<ErrorForEndpoint>() {})
                .build();
        EndpointReturnBaseType value =
                serializers.deserializer(deserializerArgs).deserialize(response);
        assertThat(value).isEqualTo(new StringReturn(expectedString));
    }

    private ConjureBodySerDe conjureBodySerDe(String... contentTypes) {
        return new ConjureBodySerDe(
                Arrays.stream(contentTypes)
                        .map(c -> WeightedEncoding.of(new TypeReturningStubEncoding(c)))
                        .collect(ImmutableList.toImmutableList()),
                errorDecoder,
                Encodings.emptyContainerDeserializer(),
                DefaultConjureRuntime.DEFAULT_SERDE_CACHE_SPEC);
    }
}
