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
import com.google.common.io.CharStreams;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.api.errors.SerializableError;
import com.palantir.conjure.java.api.errors.UnknownRemoteException;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.ws.rs.core.HttpHeaders;

/**
 * Extracts and returns a {@link RemoteException} from an {@link Response}.
 * The extracted {@link RemoteException} is returned rather than thrown. Decoders may throw exceptions (other than
 * {@link RemoteException}) if a {@link RemoteException} could not be extracted, e.g., when the given {@link
 * Response} does not adhere to an expected format.
 */
enum ErrorDecoder {
    INSTANCE;

    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();

    boolean isError(Response response) {
        return 300 <= response.code() && response.code() <= 599;
    }

    RemoteException decode(Response response) {
        // TODO(rfink): What about HTTP/101 switching protocols?
        // TODO(rfink): What about HEAD requests?

        String body;
        try {
            body = toString(response.body());
        } catch (NullPointerException | IOException e) {
            UnknownRemoteException exception = new UnknownRemoteException(response.code(), "<unparseable>");
            exception.initCause(e);
            throw exception;
        }

        Optional<String> contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        if (contentType.isPresent() && Encodings.matchesContentType("application/json", contentType.get())) {
            try {
                SerializableError serializableError = MAPPER.readValue(body, SerializableError.class);
                return new RemoteException(serializableError, response.code());
            } catch (Exception e) {
                throw new UnknownRemoteException(response.code(), body);
            }
        }

        throw new UnknownRemoteException(response.code(), body);
    }

    private static String toString(InputStream body) throws IOException {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return CharStreams.toString(reader);
        }
    }
}
