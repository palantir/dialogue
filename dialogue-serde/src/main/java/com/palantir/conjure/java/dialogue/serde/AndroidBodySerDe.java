/*
 * (c) Copyright 2025 Palantir Technologies Inc. All rights reserved.
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
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.io.ByteStreams;
import com.palantir.dialogue.BinaryRequestBody;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.ExceptionDeserializerArgs;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AndroidBodySerDe implements BodySerDe {
    private static final Logger log = LoggerFactory.getLogger(AndroidBodySerDe.class);
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String BINARY_CONTENT_TYPE = "application/octet-stream";
    private static final ErrorDecoder ERROR_DECODER = ErrorDecoder.INSTANCE;

    private final ObjectMapper mapper;

    public AndroidBodySerDe(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public <T> Serializer<T> serializer(TypeMarker<T> type) {
        return new JsonSerializer<>(mapper, type);
    }

    @Override
    public <T> Deserializer<T> deserializer(TypeMarker<T> type) {
        return new JsonDeserializer<>(mapper, type);
    }

    @Override
    public <T> Deserializer<T> deserializer(ExceptionDeserializerArgs<T> exceptionDeserializerArgs) {
        return new JsonDeserializer<>(mapper, exceptionDeserializerArgs.returnType());
    }

    @Override
    public Deserializer<Void> emptyBodyDeserializer() {
        return new EmptyDeserializer();
    }

    @Override
    public Deserializer<Void> emptyBodyDeserializer(ExceptionDeserializerArgs<Void> _exceptionDeserializerArgs) {
        return new EmptyDeserializer();
    }

    @Override
    public Deserializer<InputStream> inputStreamDeserializer() {
        return new InputStreamDeserializer();
    }

    @Override
    public Deserializer<InputStream> inputStreamDeserializer(
            ExceptionDeserializerArgs<InputStream> _exceptionDeserializerArgs) {
        return new InputStreamDeserializer();
    }

    @Override
    public Deserializer<Optional<InputStream>> optionalInputStreamDeserializer() {
        return new OptionalInputStreamDeserializer();
    }

    @Override
    public Deserializer<Optional<InputStream>> optionalInputStreamDeserializer(
            ExceptionDeserializerArgs<Optional<InputStream>> exceptionDeserializerArgs) {
        return new OptionalInputStreamDeserializer();
    }

    @Override
    public RequestBody serialize(BinaryRequestBody value) {
        Preconditions.checkNotNull(value, "A BinaryRequestBody value is required");
        return new RequestBody() {

            @Override
            public void writeTo(OutputStream output) throws IOException {
                value.write(output);
            }

            @Override
            public String contentType() {
                return BinaryEncoding.CONTENT_TYPE;
            }

            @Override
            public boolean repeatable() {
                return value.repeatable();
            }

            @Override
            public void close() {
                try {
                    value.close();
                } catch (IOException | RuntimeException e) {
                    log.warn("Failed to close BinaryRequestBody {}", UnsafeArg.of("body", value), e);
                }
            }
        };
    }

    private static final class JsonSerializer<T> implements Serializer<T> {
        private final ObjectMapper mapper;
        private final TypeMarker<T> type;

        private JsonSerializer(ObjectMapper mapper, TypeMarker<T> type) {
            this.mapper = mapper;
            this.type = type;
        }

        @Override
        public RequestBody serialize(T value) {
            return new RequestBody() {
                @Override
                public void writeTo(OutputStream output) throws IOException {
                    ObjectWriter writer = mapper.writerFor(mapper.constructType(type.getType()));
                    writer.writeValue(output, value);
                }

                @Override
                public String contentType() {
                    return JSON_CONTENT_TYPE;
                }

                @Override
                public boolean repeatable() {
                    return true;
                }

                @Override
                public void close() {
                    // nop
                }
            };
        }
    }

    private static final class JsonDeserializer<T> implements Deserializer<T> {
        private final ObjectMapper mapper;
        private final TypeMarker<T> type;

        private JsonDeserializer(ObjectMapper mapper, TypeMarker<T> type) {
            this.mapper = mapper;
            this.type = type;
        }

        @Override
        public T deserialize(Response response) {
            // TODO(jellis): figure out closing the response
            ObjectReader reader = mapper.readerFor(mapper.constructType(type.getType()));
            if (ERROR_DECODER.isError(response)) {
                throw ERROR_DECODER.decode(response);
            } else if (response.code() == 200) {
                try (InputStream inputStream = response.body()) {
                    T value = reader.readValue(inputStream);
                    return Preconditions.checkNotNull(value, "cannot deserialize a JSON null value");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // TODO(jellis): deserialize optionals
                throw new SafeRuntimeException(inputStreamToString(response.body()));
            }
        }

        @Override
        public Optional<String> accepts() {
            return Optional.of(JSON_CONTENT_TYPE);
        }
    }

    private static final class EmptyDeserializer implements Deserializer<Void> {
        @Override
        public Void deserialize(Response response) {
            try (Response unused = response) {
                if (ERROR_DECODER.isError(response)) {
                    throw ERROR_DECODER.decode(response);
                }
                return null;
            }
        }

        @Override
        public Optional<String> accepts() {
            return Optional.empty();
        }
    }

    private static final class InputStreamDeserializer implements Deserializer<InputStream> {
        @Override
        public InputStream deserialize(Response response) {
            return response.body();
        }

        @Override
        public Optional<String> accepts() {
            return Optional.of(BINARY_CONTENT_TYPE);
        }
    }

    private static final class OptionalInputStreamDeserializer implements Deserializer<Optional<InputStream>> {
        @Override
        public Optional<InputStream> deserialize(Response response) {
            return Optional.of(response.body());
        }

        @Override
        public Optional<String> accepts() {
            return Optional.of(BINARY_CONTENT_TYPE);
        }
    }

    private static String inputStreamToString(InputStream inputStream) {
        try {
            byte[] bytes = ByteStreams.toByteArray(inputStream);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
