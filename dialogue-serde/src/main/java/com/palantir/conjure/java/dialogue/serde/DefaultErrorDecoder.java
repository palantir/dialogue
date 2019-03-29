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
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.ErrorDecoder;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public enum DefaultErrorDecoder implements ErrorDecoder {
    INSTANCE;

    private static final ObjectMapper MAPPER = ObjectMappers.newClientObjectMapper();

    @Override
    public RemoteException decode(Response response) {
        // TODO(rfink): What about HTTP/101 switching protocols?
        // TODO(rfink): What about HEAD requests?

        if (response.contentType().isPresent() && response.contentType().get().equals("application/json")) {
            final String body;
            try {
                body = toString(response.body());
            } catch (NullPointerException | IOException e) {
                throw new SafeRuntimeException(
                        "Failed to read response body, could not deserialize SerializableError", e);
            }
            try {
                SerializableError serializableError = MAPPER.readValue(body, SerializableError.class);
                return new RemoteException(serializableError, response.code());
            } catch (Exception e) {
                throw new SafeRuntimeException(
                        "Failed to deserialize response body as JSON, could not deserialize SerializableError",
                        e,
                        SafeArg.of("code", response.code()),
                        UnsafeArg.of("body", body));
            }
        }

        throw new SafeRuntimeException(
                "Failed to interpret response body as SerializableError", SafeArg.of("code", response.code()));
    }

    private static String toString(InputStream body) throws IOException {
        try (Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
            return CharStreams.toString(reader);
        }
    }
}
