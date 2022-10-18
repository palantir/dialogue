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
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestResponse;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import java.time.Duration;
import javax.ws.rs.core.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class ErrorDecoderTest {

    private static final ObjectMapper SERVER_MAPPER = ObjectMappers.newServerObjectMapper();

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

    private static final ErrorDecoder decoder = ConjureErrorDecoder.INSTANCE;

    @Test
    public void extractsRemoteExceptionForAllErrorCodes() {
        for (int code : ImmutableList.of(300, 400, 404, 500)) {
            Response response =
                    TestResponse.withBody(SERIALIZED_EXCEPTION).code(code).contentType("application/json");
            assertThat(decoder.isError(response)).isTrue();

            RuntimeException result = decoder.decode(response);
            assertThat(result).isInstanceOfSatisfying(RemoteException.class, exception -> {
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
            });
        }
    }

    @Test
    public void testQos503() {
        Response response = TestResponse.withBody(SERIALIZED_EXCEPTION).code(503);
        assertThat(decoder.isError(response)).isTrue();

        RuntimeException result = decoder.decode(response);
        assertThat(result).isInstanceOf(QosException.Unavailable.class);
    }

    @Test
    public void testQos429() {
        Response response = TestResponse.withBody(SERIALIZED_EXCEPTION).code(429);
        assertThat(decoder.isError(response)).isTrue();

        RuntimeException result = decoder.decode(response);
        assertThat(result)
                .isInstanceOfSatisfying(QosException.Throttle.class, exception -> assertThat(exception.getRetryAfter())
                        .isEmpty());
    }

    @Test
    public void testQos429_retryAfter() {
        Response response =
                TestResponse.withBody(SERIALIZED_EXCEPTION).code(429).withHeader(HttpHeaders.RETRY_AFTER, "3");
        assertThat(decoder.isError(response)).isTrue();

        RuntimeException result = decoder.decode(response);
        assertThat(result)
                .isInstanceOfSatisfying(QosException.Throttle.class, exception -> assertThat(exception.getRetryAfter())
                        .hasValue(Duration.ofSeconds(3)));
    }

    @Test
    public void testQos429_retryAfter_invalid() {
        Response response =
                TestResponse.withBody(SERIALIZED_EXCEPTION).code(429).withHeader(HttpHeaders.RETRY_AFTER, "bad");
        assertThat(decoder.isError(response)).isTrue();

        RuntimeException result = decoder.decode(response);
        assertThat(result)
                .isInstanceOfSatisfying(QosException.Throttle.class, exception -> assertThat(exception.getRetryAfter())
                        .isEmpty());
    }

    @Test
    public void testQos308_noLocation() {
        Response response = TestResponse.withBody(SERIALIZED_EXCEPTION).code(308);
        assertThat(decoder.isError(response)).isTrue();

        RuntimeException result = decoder.decode(response);
        assertThat(result)
                .isInstanceOfSatisfying(UnknownRemoteException.class, exception -> assertThat(exception.getStatus())
                        .isEqualTo(308));
    }

    @Test
    public void testQos308_invalidLocation() {
        Response response =
                TestResponse.withBody(SERIALIZED_EXCEPTION).code(308).withHeader(HttpHeaders.LOCATION, "invalid");
        assertThat(decoder.isError(response)).isTrue();

        RuntimeException result = decoder.decode(response);
        assertThat(result)
                .isInstanceOfSatisfying(UnknownRemoteException.class, exception -> assertThat(exception.getStatus())
                        .isEqualTo(308));
    }

    @Test
    public void testQos308() {
        String expectedLocation = "https://localhost";
        Response response = TestResponse.withBody(SERIALIZED_EXCEPTION)
                .code(308)
                .withHeader(HttpHeaders.LOCATION, expectedLocation);
        assertThat(decoder.isError(response)).isTrue();

        RuntimeException result = decoder.decode(response);
        assertThat(result)
                .isInstanceOf(UnknownRemoteException.class)
                .getRootCause()
                .isInstanceOfSatisfying(
                        QosException.RetryOther.class,
                        exception ->
                                assertThat(exception.getRedirectTo()).asString().isEqualTo(expectedLocation));
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
    }

    @Test
    public void doesNotHandleUnparseableBody() {
        assertThat(decoder.decode(TestResponse.withBody("not json").code(500).contentType("application/json/")))
                .isInstanceOfSatisfying(UnknownRemoteException.class, expected -> {
                    assertThat(expected.getStatus()).isEqualTo(500);
                    assertThat(expected.getBody()).isEqualTo("not json");
                    assertThat(expected.getMessage()).isEqualTo("Response status: 500");
                });
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally testing null body
    public void doesNotHandleNullBody() {
        assertThat(decoder.decode(TestResponse.withBody(null).code(500).contentType("application/json")))
                .isInstanceOf(UnknownRemoteException.class)
                .hasMessage("Response status: 500");
    }

    @Test
    public void handlesUnexpectedJson() {
        assertThat(decoder.decode(TestResponse.withBody("{\"error\":\"some-unknown-json\"}")
                        .code(502)
                        .contentType("application/json")))
                .isInstanceOfSatisfying(UnknownRemoteException.class, expected -> {
                    assertThat(expected.getStatus()).isEqualTo(502);
                    assertThat(expected.getBody()).isEqualTo("{\"error\":\"some-unknown-json\"}");
                    assertThat(expected.getMessage()).isEqualTo("Response status: 502");
                });
    }

    @Test
    public void handlesJsonWithEncoding() {
        int code = 500;
        RuntimeException result = decoder.decode(
                TestResponse.withBody(SERIALIZED_EXCEPTION).code(code).contentType("application/json; charset=utf-8"));
        assertThat(result).isInstanceOfSatisfying(RemoteException.class, exception -> {
            assertThat(exception.getCause()).isNull();
            assertThat(exception.getStatus()).isEqualTo(code);
            assertThat(exception.getError().errorCode())
                    .isEqualTo(ErrorType.FAILED_PRECONDITION.code().name());
            assertThat(exception.getError().errorName()).isEqualTo(ErrorType.FAILED_PRECONDITION.name());
        });
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
            throw new RuntimeException(e);
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
