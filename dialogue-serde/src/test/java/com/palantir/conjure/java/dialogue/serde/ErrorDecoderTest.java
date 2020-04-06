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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.palantir.conjure.java.api.errors.ErrorType;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.ServiceException;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.TestResponse;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
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

    private static final ErrorDecoder decoder = ErrorDecoder.INSTANCE;

    @Test
    public void extractsRemoteExceptionForAllErrorCodes() {
        for (int code : ImmutableList.of(300, 400, 404, 500)) {
            Response response =
                    TestResponse.withBody(SERIALIZED_EXCEPTION).code(code).contentType("application/json");
            assertThat(decoder.isError(response)).isTrue();

            RemoteException exception = decoder.decode(response);
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
                            + SERVICE_EXCEPTION.getErrorInstanceId());
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
        assertThatThrownBy(() -> decoder.decode(
                        TestResponse.withBody(SERIALIZED_EXCEPTION).code(500).contentType("text/plain")))
                .isInstanceOf(UnknownRemoteException.class)
                .hasMessage("Error 500. (Failed to parse response body as SerializableError.)");
    }

    @Test
    public void doesNotHandleUnparseableBody() {
        try {
            decoder.decode(TestResponse.withBody("not json").code(500).contentType("application/json/"));
            failBecauseExceptionWasNotThrown(UnknownRemoteException.class);
        } catch (UnknownRemoteException expected) {
            assertThat(expected.getStatus()).isEqualTo(500);
            assertThat(expected.getBody()).isEqualTo("not json");
            assertThat(expected.getMessage())
                    .isEqualTo("Error 500. (Failed to parse response body as SerializableError.)");
        }
    }

    @Test
    @SuppressWarnings("NullAway") // intentionally testing null body
    public void doesNotHandleNullBody() {
        assertThatThrownBy(() ->
                        decoder.decode(TestResponse.withBody(null).code(500).contentType("application/json")))
                .isInstanceOf(UnknownRemoteException.class)
                .hasMessage("Error 500. (Failed to parse response body as SerializableError.)");
    }

    @Test
    public void handlesUnexpectedJson() {
        try {
            decoder.decode(TestResponse.withBody("{\"error\":\"some-unknown-json\"}")
                    .code(502)
                    .contentType("application/json"));
            failBecauseExceptionWasNotThrown(UnknownRemoteException.class);
        } catch (UnknownRemoteException expected) {
            assertThat(expected.getStatus()).isEqualTo(502);
            assertThat(expected.getBody()).isEqualTo("{\"error\":\"some-unknown-json\"}");
            assertThat(expected.getMessage())
                    .isEqualTo("Error 502. (Failed to parse response body as SerializableError.)");
        }
    }

    @Test
    public void handlesJsonWithEncoding() {
        int code = 500;
        RemoteException exception = decoder.decode(
                TestResponse.withBody(SERIALIZED_EXCEPTION).code(code).contentType("application/json; charset=utf-8"));
        assertThat(exception.getCause()).isNull();
        assertThat(exception.getStatus()).isEqualTo(code);
        assertThat(exception.getError().errorCode())
                .isEqualTo(ErrorType.FAILED_PRECONDITION.code().name());
        assertThat(exception.getError().errorName()).isEqualTo(ErrorType.FAILED_PRECONDITION.name());
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
        return decoder.decode(TestResponse.withBody(json).code(status).contentType("application/json"));
    }
}
