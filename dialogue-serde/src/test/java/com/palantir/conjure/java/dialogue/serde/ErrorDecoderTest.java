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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.api.errors.QosReason;
import com.palantir.conjure.java.api.errors.QosReason.DueTo;
import com.palantir.conjure.java.api.errors.QosReason.RetryHint;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestResponse;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class ErrorDecoderTest {

    private static final ObjectMapper SERVER_MAPPER = ObjectMappers.newServerObjectMapper();
    private static final QosReason QOS_REASON = QosReason.of("client-qos-response");

    private static final ServiceException SERVICE_EXCEPTION =
            new ServiceException(ErrorType.FAILED_PRECONDITION, SafeArg.of("key", "value"));
    private static final String SERIALIZED_EXCEPTION = createServiceException(SERVICE_EXCEPTION);

    private static String createServiceException(ServiceException exception) {
        try {
            return SERVER_MAPPER.writeValueAsString(SerializableError.forException(exception));
        } catch (JsonProcessingException e) {
            fail("failed to serialize");
            return "";
        }
    }

    public enum DecoderType {
        LEGACY,
        ENDPOINT
    }

    private static final ErrorDecoder decoder = ErrorDecoder.INSTANCE;
    private static final EndpointErrorDecoder<?> endpointErrorDecoder =
            new EndpointErrorDecoder<>(Collections.emptyMap(), Collections.emptyMap());

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    public void extractsRemoteExceptionForAllErrorCodes(DecoderType decoderType) {
        for (int code : ImmutableList.of(300, 400, 404, 500)) {
            Response response =
                    TestResponse.withBody(SERIALIZED_EXCEPTION).code(code).contentType("application/json");

            Consumer<RemoteException> validationFunction = exception -> {
                assertThat(exception.getCause()).isNull();
                assertThat(exception.getStatus()).isEqualTo(code);
                assertThat(exception.getError().errorCode())
                        .isEqualTo(ErrorType.FAILED_PRECONDITION.code().name());
                assertThat(exception.getError().errorName()).isEqualTo(ErrorType.FAILED_PRECONDITION.name());
                assertThat(exception.getMessage())
                        .isEqualTo("RemoteException: "
                                + ErrorType.FAILED_PRECONDITION.code().name()
                                + " ("
                                + ErrorType.FAILED_PRECONDITION.name()
                                + ") with instance ID "
                                + SERVICE_EXCEPTION.getErrorInstanceId() + ": {key=value}");
                assertThat(exception.getLogMessage())
                        .isEqualTo("RemoteException: "
                                + ErrorType.FAILED_PRECONDITION.code().name()
                                + " ("
                                + ErrorType.FAILED_PRECONDITION.name()
                                + ")");
            };

            if (decoderType == DecoderType.LEGACY) {
                assertThat(decoder.isError(response)).isTrue();
                RuntimeException result = decoder.decode(response);
                assertThat(result).isInstanceOfSatisfying(RemoteException.class, validationFunction);
            } else {
                assertThat(endpointErrorDecoder.isError(response)).isTrue();
                assertThatExceptionOfType(RemoteException.class)
                        .isThrownBy(() -> endpointErrorDecoder.decode(response))
                        .satisfies(validationFunction);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    public void testQos503(DecoderType decoderType) {
        Response response = TestResponse.withBody(SERIALIZED_EXCEPTION).code(503);

        Consumer<RuntimeException> validationFunction = exception -> {
            assertThat(exception).isInstanceOfSatisfying(QosException.Unavailable.class, qosException -> {
                assertThat(qosException.getReason()).isEqualTo(QOS_REASON);
            });
        };

        if (decoderType == DecoderType.LEGACY) {
            assertThat(decoder.isError(response)).isTrue();
            RuntimeException result = decoder.decode(response);
            assertThat(result).isInstanceOfSatisfying(RuntimeException.class, validationFunction);
        } else {
            assertThat(endpointErrorDecoder.isError(response)).isTrue();
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> endpointErrorDecoder.decode(response))
                    .satisfies(validationFunction);
        }
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    public void testQos503WithMetadata(DecoderType decoderType) {
        Response response = TestResponse.withBody(SERIALIZED_EXCEPTION)
                .code(503)
                .withHeader("Qos-Retry-Hint", "do-not-retry")
                .withHeader("Qos-Due-To", "custom");

        Consumer<RuntimeException> validationFunction = exception -> {
            assertThat(exception).isInstanceOfSatisfying(QosException.Unavailable.class, qosException -> {
                assertThat(qosException.getReason())
                        .isEqualTo(QosReason.builder()
                                .from(QOS_REASON)
                                .dueTo(DueTo.CUSTOM)
                                .retryHint(RetryHint.DO_NOT_RETRY)
                                .build());
            });
        };

        if (decoderType == DecoderType.LEGACY) {
            assertThat(decoder.isError(response)).isTrue();
            RuntimeException result = decoder.decode(response);
            assertThat(result).isInstanceOfSatisfying(RuntimeException.class, validationFunction);
        } else {
            assertThat(endpointErrorDecoder.isError(response)).isTrue();
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> endpointErrorDecoder.decode(response))
                    .satisfies(validationFunction);
        }
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    public void testQos429(DecoderType decoderType) {
        Response response = TestResponse.withBody(SERIALIZED_EXCEPTION).code(429);

        Consumer<RuntimeException> validationFunction = exception -> {
            assertThat(exception).isInstanceOfSatisfying(QosException.Throttle.class, qosException -> {
                assertThat(qosException.getReason()).isEqualTo(QOS_REASON);
                assertThat(qosException.getRetryAfter()).isEmpty();
            });
        };

        if (decoderType == DecoderType.LEGACY) {
            assertThat(decoder.isError(response)).isTrue();
            RuntimeException result = decoder.decode(response);
            assertThat(result).isInstanceOfSatisfying(RuntimeException.class, validationFunction);
        } else {
            assertThat(endpointErrorDecoder.isError(response)).isTrue();
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> endpointErrorDecoder.decode(response))
                    .satisfies(validationFunction);
        }
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    public void testQos429_retryAfter(DecoderType decoderType) {
        Response response =
                TestResponse.withBody(SERIALIZED_EXCEPTION).code(429).withHeader(HttpHeaders.RETRY_AFTER, "3");

        Consumer<RuntimeException> validationFunction = exception -> {
            assertThat(exception).isInstanceOfSatisfying(QosException.Throttle.class, qosException -> {
                assertThat(qosException.getReason()).isEqualTo(QOS_REASON);
                assertThat(qosException.getRetryAfter()).hasValue(Duration.ofSeconds(3));
            });
        };

        if (decoderType == DecoderType.LEGACY) {
            assertThat(decoder.isError(response)).isTrue();
            RuntimeException result = decoder.decode(response);
            assertThat(result).isInstanceOfSatisfying(RuntimeException.class, validationFunction);
        } else {
            assertThat(endpointErrorDecoder.isError(response)).isTrue();
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> endpointErrorDecoder.decode(response))
                    .satisfies(validationFunction);
        }
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    public void testQos429_retryAfter_invalid(DecoderType decoderType) {
        Response response =
                TestResponse.withBody(SERIALIZED_EXCEPTION).code(429).withHeader(HttpHeaders.RETRY_AFTER, "bad");

        Consumer<RuntimeException> validationFunction = exception -> {
            assertThat(exception).isInstanceOfSatisfying(QosException.Throttle.class, qosException -> {
                assertThat(qosException.getReason()).isEqualTo(QOS_REASON);
                assertThat(qosException.getRetryAfter()).isEmpty();
            });
        };

        if (decoderType == DecoderType.LEGACY) {
            assertThat(decoder.isError(response)).isTrue();
            RuntimeException result = decoder.decode(response);
            assertThat(result).isInstanceOfSatisfying(RuntimeException.class, validationFunction);
        } else {
            assertThat(endpointErrorDecoder.isError(response)).isTrue();
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> endpointErrorDecoder.decode(response))
                    .satisfies(validationFunction);
        }
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    public void testQos308_noLocation(DecoderType decoderType) {
        Response response = TestResponse.withBody(SERIALIZED_EXCEPTION).code(308);

        Consumer<RuntimeException> validationFunction = exception -> {
            assertThat(exception).isInstanceOfSatisfying(UnknownRemoteException.class, unknownException -> {
                assertThat(unknownException.getStatus()).isEqualTo(308);
            });
        };

        if (decoderType == DecoderType.LEGACY) {
            assertThat(decoder.isError(response)).isTrue();
            RuntimeException result = decoder.decode(response);
            assertThat(result).isInstanceOfSatisfying(RuntimeException.class, validationFunction);
        } else {
            assertThat(endpointErrorDecoder.isError(response)).isTrue();
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> endpointErrorDecoder.decode(response))
                    .satisfies(validationFunction);
        }
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    public void testQos308_invalidLocation(DecoderType decoderType) {
        Response response =
                TestResponse.withBody(SERIALIZED_EXCEPTION).code(308).withHeader(HttpHeaders.LOCATION, "invalid");

        Consumer<RuntimeException> validationFunction = exception -> {
            assertThat(exception).isInstanceOfSatisfying(UnknownRemoteException.class, unknownException -> {
                assertThat(unknownException.getStatus()).isEqualTo(308);
            });
        };

        if (decoderType == DecoderType.LEGACY) {
            assertThat(decoder.isError(response)).isTrue();
            RuntimeException result = decoder.decode(response);
            assertThat(result).isInstanceOfSatisfying(RuntimeException.class, validationFunction);
        } else {
            assertThat(endpointErrorDecoder.isError(response)).isTrue();
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> endpointErrorDecoder.decode(response))
                    .satisfies(validationFunction);
        }
    }

    @ParameterizedTest
    @EnumSource(ErrorDecoderTest.DecoderType.class)
    public void testQos308(DecoderType decoderType) {
        String expectedLocation = "https://localhost";
        Response response = TestResponse.withBody(SERIALIZED_EXCEPTION)
                .code(308)
                .withHeader(HttpHeaders.LOCATION, expectedLocation);

        Consumer<RuntimeException> validationFunction = exception -> {
            assertThat(exception)
                    .isInstanceOf(UnknownRemoteException.class)
                    .getRootCause()
                    .isInstanceOfSatisfying(QosException.RetryOther.class, qosException -> {
                        assertThat(qosException.getReason()).isEqualTo(QOS_REASON);
                        assertThat(qosException.getRedirectTo()).asString().isEqualTo(expectedLocation);
                    });
        };

        if (decoderType == DecoderType.LEGACY) {
            assertThat(decoder.isError(response)).isTrue();
            RuntimeException result = decoder.decode(response);
            assertThat(result).isInstanceOfSatisfying(RuntimeException.class, validationFunction);
        } else {
            assertThat(endpointErrorDecoder.isError(response)).isTrue();
            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> endpointErrorDecoder.decode(response))
                    .satisfies(validationFunction);
        }
    }

    @Test
    public void testSpecificException() {
        RemoteException exception = encodeAndDecode(new IllegalArgumentException("msg"));
        assertThat(exception).isInstanceOf(RemoteException.class);
        assertThat(exception.getMessage()).startsWith("RemoteException: java.lang.IllegalArgumentException (msg)");
    }

    @Test
    public void cannotDecodeNonJsonMediaTypes() {
        assertThat(decoder.decode(
                        TestResponse.withBody(SERIALIZED_EXCEPTION).code(500).contentType("text/plain")))
                .isInstanceOf(UnknownRemoteException.class)
                .hasMessage("Response status: 500");

        assertThatExceptionOfType(UnknownRemoteException.class)
                .isThrownBy(() -> endpointErrorDecoder.decode(
                        TestResponse.withBody(SERIALIZED_EXCEPTION).code(500).contentType("text/plain")))
                .satisfies(exception -> assertThat(exception.getMessage()).isEqualTo("Response status: 500"));
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    public void doesNotHandleUnparseableBody(DecoderType decoderType) {
        Response response = TestResponse.withBody("not json").code(500).contentType("application/json/");

        Consumer<UnknownRemoteException> validationFunction = exception -> {
            assertThat(exception.getStatus()).isEqualTo(500);
            assertThat(exception.getBody()).isEqualTo("not json");
        };

        if (decoderType == DecoderType.LEGACY) {
            RuntimeException result = decoder.decode(response);
            assertThat(result).isInstanceOfSatisfying(UnknownRemoteException.class, validationFunction);
        } else {
            assertThatExceptionOfType(UnknownRemoteException.class)
                    .isThrownBy(() -> endpointErrorDecoder.decode(response))
                    .satisfies(validationFunction);
        }
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally testing null body
    public void doesNotHandleNullBody() {
        assertThat(decoder.decode(TestResponse.withBody(null).code(500).contentType("application/json")))
                .isInstanceOf(UnknownRemoteException.class)
                .hasMessage("Response status: 500");

        assertThatExceptionOfType(UnknownRemoteException.class)
                .isThrownBy(() -> endpointErrorDecoder.decode(
                        TestResponse.withBody(null).code(500).contentType("application/json")))
                .satisfies(exception -> assertThat(exception.getMessage()).isEqualTo("Response status: 500"));
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    public void handlesUnexpectedJson(DecoderType decoderType) {
        Response response = TestResponse.withBody("{\"error\":\"some-unknown-json\"}")
                .code(502)
                .contentType("application/json");

        Consumer<UnknownRemoteException> validationFunction = expected -> {
            assertThat(expected.getStatus()).isEqualTo(502);
            assertThat(expected.getBody()).isEqualTo("{\"error\":\"some-unknown-json\"}");
            assertThat(expected.getMessage()).isEqualTo("Response status: 502");
        };
        if (decoderType == DecoderType.LEGACY) {
            assertThat(decoder.decode(response))
                    .isInstanceOfSatisfying(UnknownRemoteException.class, validationFunction);
        } else {
            assertThatExceptionOfType(UnknownRemoteException.class)
                    .isThrownBy(() -> endpointErrorDecoder.decode(response))
                    .satisfies(validationFunction);
        }
    }

    @ParameterizedTest
    @EnumSource(DecoderType.class)
    public void handlesJsonWithEncoding(DecoderType decoderType) {
        int code = 500;
        Response response =
                TestResponse.withBody(SERIALIZED_EXCEPTION).code(code).contentType("application/json; charset=utf-8");

        Consumer<RemoteException> validationFunction = exception -> {
            assertThat(exception.getCause()).isNull();
            assertThat(exception.getStatus()).isEqualTo(code);
            assertThat(exception.getError().errorCode())
                    .isEqualTo(ErrorType.FAILED_PRECONDITION.code().name());
            assertThat(exception.getError().errorName()).isEqualTo(ErrorType.FAILED_PRECONDITION.name());
        };

        if (decoderType == DecoderType.LEGACY) {
            assertThat(decoder.decode(response)).isInstanceOfSatisfying(RemoteException.class, validationFunction);
        } else {
            assertThatExceptionOfType(RemoteException.class)
                    .isThrownBy(() -> endpointErrorDecoder.decode(response))
                    .satisfies(validationFunction);
        }
    }

    private static RemoteException encodeAndDecode(Exception exception) {
        Preconditions.checkArgument(!(exception instanceof ServiceException), "Use SerializableError#forException");
        Object error = SerializableError.builder()
                .errorCode(exception.getClass().getName())
                .errorName(Preconditions.checkNotNull(exception.getMessage(), "exception message"))
                .build();
        String json;
        try {
            json = SERVER_MAPPER.writeValueAsString(error);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }

        // TODO(rfink): Resurrect
        // int status = (exception instanceof WebApplicationException)
        //         ? ((WebApplicationException) exception).getResponse().getStatus()
        //         : 400;
        int status = 400;
        RuntimeException result =
                decoder.decode(TestResponse.withBody(json).code(status).contentType("application/json"));
        assertThat(result).isInstanceOf(RemoteException.class);
        return (RemoteException) result;
    }
}
