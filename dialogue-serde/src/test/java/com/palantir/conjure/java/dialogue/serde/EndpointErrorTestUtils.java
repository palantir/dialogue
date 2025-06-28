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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.palantir.conjure.java.api.errors.CheckedServiceException;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Arg;
import com.palantir.logsafe.Safe;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;

final class EndpointErrorTestUtils {
    private EndpointErrorTestUtils() {}

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

    record ConjureError(
            @JsonProperty("errorCode") String errorCode,
            @JsonProperty("errorName") String errorName,
            @JsonProperty("errorInstanceId") String errorInstanceId,
            @JsonProperty("parameters") Map<String, Object> parameters) {
        static ConjureError fromCheckedServiceException(CheckedServiceException exception) {
            Map<String, Object> parameters = new HashMap<>();
            for (Arg<?> arg : exception.getArgs()) {
                if (shouldIncludeArgInParameters(arg)) {
                    parameters.put(arg.getName(), arg.getValue());
                }
            }
            return new ConjureError(
                    exception.getErrorType().code().name(),
                    exception.getErrorType().name(),
                    exception.getErrorInstanceId(),
                    parameters);
        }

        private static boolean shouldIncludeArgInParameters(Arg<?> arg) {
            Object obj = arg.getValue();
            return obj != null
                    && (!(obj instanceof Optional) || ((Optional<?>) obj).isPresent())
                    && (!(obj instanceof OptionalInt optionalInt) || optionalInt.isPresent())
                    && (!(obj instanceof OptionalLong optionalLong) || optionalLong.isPresent())
                    && (!(obj instanceof OptionalDouble optionalDouble) || optionalDouble.isPresent());
        }
    }

    /** Deserializes requests as the type. */
    public static final class TypeReturningStubEncoding implements Encoding {

        private final String contentType;
        private final Function<TypeMarker<?>, Encoding.Deserializer<?>> deserializerFactory;
        private final Map<TypeMarker<?>, Encoding.Deserializer<?>> deserializers = new HashMap<>();

        TypeReturningStubEncoding(String contentType) {
            this(contentType, typeMarker -> Encodings.json().deserializer(typeMarker));
        }

        TypeReturningStubEncoding(
                String contentType, Function<TypeMarker<?>, Encoding.Deserializer<?>> deserializerFactory) {
            this.contentType = contentType;
            this.deserializerFactory = deserializerFactory;
        }

        @Override
        public <T> Encoding.Serializer<T> serializer(TypeMarker<T> _type) {
            return (_value, _output) -> {
                // nop
            };
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Encoding.Deserializer<T> deserializer(TypeMarker<T> type) {
            return input -> {
                Deserializer<T> deserializer =
                        (Deserializer<T>) deserializers.computeIfAbsent(type, deserializerFactory);
                return deserializer.deserialize(input);
            };
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean supportsContentType(String input) {
            return contentType.equals(input);
        }

        @Override
        public String toString() {
            return "TypeReturningStubEncoding{" + contentType + '}';
        }

        @SuppressWarnings("unchecked")
        public <T> Encoding.Deserializer<T> getDeserializer(TypeMarker<T> type) {
            return (Deserializer<T>) deserializers.get(type);
        }
    }

    public static final class ContentRecordingJsonDeserializer<T> implements Encoding.Deserializer<T> {
        private final List<String> deserializedContent = new ArrayList<>();
        private final Encoding.Deserializer<T> delegate;

        ContentRecordingJsonDeserializer(TypeMarker<T> type) {
            this.delegate = Encodings.json().deserializer(type);
        }

        public List<String> getDeserializedContent() {
            return deserializedContent;
        }

        @Override
        public T deserialize(InputStream input) throws IOException {
            String inputString = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            deserializedContent.add(inputString);
            return delegate.deserialize(new ByteArrayInputStream(inputString.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
