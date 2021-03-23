/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.dialogue.annotations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.conjure.java.dialogue.serde.Encoding;
import com.palantir.conjure.java.dialogue.serde.Encodings;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import java.io.InputStream;
import javax.annotation.Nullable;

/**
 * Implements conjure-style json body and response handling.
 *
 * <ul>
 *     <li>Successful responses are deserialized using the specified {@link ObjectMapper}.</li>
 *     <li>Error responses will be deserialized as {@link com.palantir.conjure.java.api.errors.SerializableError} and
 *     {@link com.palantir.conjure.java.api.errors.RemoteException} will be thrown.</li>
 *     <li>If that fails, {@link com.palantir.conjure.java.api.errors.UnknownRemoteException} will be thrown.</li>
 * </ul>
 */
public final class Json implements DeserializerFactory<Object>, SerializerFactory<Object> {

    private static final BodySerDe DEFAULT_BODY_SERDE =
            DefaultConjureRuntime.builder().encodings(Encodings.json()).build().bodySerDe();

    private final BodySerDe bodySerDe;

    public Json() {
        this(DEFAULT_BODY_SERDE);
    }

    public Json(ObjectMapper mapper) {
        this(DefaultConjureRuntime.builder().encodings(json(mapper)).build().bodySerDe());
    }

    private Json(BodySerDe bodySerDe) {
        this.bodySerDe = bodySerDe;
    }

    @Override
    public <T> com.palantir.dialogue.Deserializer<T> deserializerFor(TypeMarker<T> type) {
        return bodySerDe.deserializer(type);
    }

    @Override
    public <T> com.palantir.dialogue.Serializer<T> serializerFor(TypeMarker<T> type) {
        return bodySerDe.serializer(type);
    }

    // Copied code, tbd how to share.

    private static Encoding json(ObjectMapper mapper) {
        return new AbstractJacksonEncoding(mapper) {
            private static final String CONTENT_TYPE = "application/json";

            @Override
            public String getContentType() {
                return CONTENT_TYPE;
            }

            @Override
            public boolean supportsContentType(String contentType) {
                return matchesContentType(CONTENT_TYPE, contentType);
            }
        };
    }

    private abstract static class AbstractJacksonEncoding implements Encoding {

        private final ObjectMapper mapper;

        AbstractJacksonEncoding(ObjectMapper mapper) {
            this.mapper = Preconditions.checkNotNull(mapper, "ObjectMapper is required");
        }

        @Override
        public final <T> Serializer<T> serializer(TypeMarker<T> type) {
            ObjectWriter writer = mapper.writerFor(mapper.constructType(type.getType()));
            return (value, output) ->
                    writer.writeValue(output, Preconditions.checkNotNull(value, "cannot serialize null value"));
        }

        @Override
        public final <T> Deserializer<T> deserializer(TypeMarker<T> type) {
            ObjectReader reader = mapper.readerFor(mapper.constructType(type.getType()));
            return input -> {
                try (InputStream inputStream = input) {
                    T value = reader.readValue(inputStream);
                    return Preconditions.checkNotNull(value, "cannot deserialize a JSON null value");
                }
            };
        }

        @Override
        public final String toString() {
            return "AbstractJacksonEncoding{" + getContentType() + '}';
        }
    }

    static boolean matchesContentType(String contentType, @Nullable String typeToCheck) {
        // TODO(ckozak): support wildcards? See javax.ws.rs.core.MediaType.isCompatible
        return typeToCheck != null
                // Use startsWith to avoid failures due to charset
                && typeToCheck.startsWith(contentType);
    }
}
