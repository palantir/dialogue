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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Suppliers;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import java.io.InputStream;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class Encodings {

    private Encodings() {}

    private static final Supplier<ObjectMapper> JSON_MAPPER =
            Suppliers.memoize(() -> configure(ObjectMappers.newClientObjectMapper()));

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
                    // Bad input should result in a 4XX response status, throw IAE rather than NPE.
                    Preconditions.checkArgument(value != null, "cannot deserialize a JSON null value");
                    return value;
                }
            };
        }

        @Override
        public final String toString() {
            return "AbstractJacksonEncoding{" + getContentType() + '}';
        }
    }

    /** Returns a serializer for the Conjure JSON wire format. */
    public static Encoding json() {
        return new AbstractJacksonEncoding(JSON_MAPPER.get()) {
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

    /** Returns a serializer for the Conjure CBOR wire format. */
    public static Encoding cbor() {
        return new AbstractJacksonEncoding(configure(ObjectMappers.newCborClientObjectMapper())) {
            private static final String CONTENT_TYPE = "application/cbor";

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

    /** Returns a serializer for the Conjure Smile wire format. */
    public static Encoding smile() {
        return new AbstractJacksonEncoding(configure(ObjectMappers.newSmileClientObjectMapper())) {
            private static final String CONTENT_TYPE = "application/x-jackson-smile";

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

    static EmptyContainerDeserializer emptyContainerDeserializer() {
        return new JacksonEmptyContainerLoader(JSON_MAPPER.get());
    }

    static boolean matchesContentType(String contentType, @Nullable String typeToCheck) {
        // TODO(ckozak): support wildcards? See javax.ws.rs.core.MediaType.isCompatible
        return typeToCheck != null
                // Use startsWith to avoid failures due to charset
                && typeToCheck.startsWith(contentType);
    }

    private static ObjectMapper configure(ObjectMapper mapper) {
        // See documentation on Encoding.Serializer#serialize: Implementations must not close the stream.
        return mapper.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
                // Avoid flushing, allowing us to set content-length if the length is below the buffer size.
                .disable(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM);
    }
}
