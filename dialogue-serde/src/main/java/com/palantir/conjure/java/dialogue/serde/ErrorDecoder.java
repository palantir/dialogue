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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * Extracts and returns a {@link RemoteException} from an {@link Response}.
 * The extracted {@link RemoteException} is returned rather than thrown. Decoders may throw exceptions (other than
 * {@link RemoteException}) if a {@link RemoteException} could not be extracted, e.g., when the given {@link
 * Response} does not adhere to an expected format.
 */
public enum ErrorDecoder {
    INSTANCE;

    private static final SafeLogger log = SafeLoggerFactory.get(ErrorDecoder.class);
    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();
    private static final EndpointErrorDecoder<?> ENDPOINT_ERROR_DECODER =
            new EndpointErrorDecoder<>(Collections.emptyMap(), Optional.empty());

    public boolean isError(Response response) {
        return ENDPOINT_ERROR_DECODER.isError(response);
    }

    public RuntimeException decode(Response response) {
        if (log.isDebugEnabled()) {
            log.debug("Received an error response", EndpointErrorDecoder.diagnosticArgs(response));
        }
        RuntimeException result = decodeInternal(response);
        result.addSuppressed(EndpointErrorDecoder.diagnostic(response));
        return result;
    }

    private RuntimeException decodeInternal(Response response) {
        // TODO(rfink): What about HTTP/101 switching protocols?
        // TODO(rfink): What about HEAD requests?

        Optional<RuntimeException> maybeQosException = ENDPOINT_ERROR_DECODER.checkCode(response);
        if (maybeQosException.isPresent()) {
            return maybeQosException.get();
        }

        int code = response.code();
        String body;
        try {
            body = EndpointErrorDecoder.toString(response.body());
        } catch (NullPointerException | IOException e) {
            UnknownRemoteException exception = new UnknownRemoteException(code, "<unparseable>");
            exception.initCause(e);
            return exception;
        }

        Optional<String> contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        if (contentType.isPresent() && Encodings.matchesContentType("application/json", contentType.get())) {
            try {
                SerializableError serializableError = MAPPER.readValue(body, SerializableError.class);
                return new RemoteException(serializableError, code);
            } catch (Exception e) {
                return new UnknownRemoteException(code, body);
            }
        }

        return new UnknownRemoteException(code, body);
    }
}
