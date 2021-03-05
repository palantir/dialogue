/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import com.palantir.tracing.CloseableTracer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;

/**
 * Wrapper around an {@link Encoding} which adds tracing spans around serialization and deserialization operations
 * including I/O.
 */
final class TracedEncoding implements Encoding {

    private final Encoding delegate;

    TracedEncoding(Encoding delegate) {
        this.delegate = Preconditions.checkNotNull(delegate, "Encoding is required");
    }

    @Override
    public <T> Serializer<T> serializer(TypeMarker<T> type) {
        String operation = "Dialogue: serialize " + toString(type) + " to " + getContentType();
        return new TracedSerializer<>(delegate.serializer(type), operation);
    }

    @Override
    public <T> Deserializer<T> deserializer(TypeMarker<T> type) {
        String operation = "Dialogue: deserialize " + toString(type) + " from " + getContentType();
        return new TracedDeserializer<>(delegate.deserializer(type), operation);
    }

    @Override
    public String getContentType() {
        return delegate.getContentType();
    }

    @Override
    public boolean supportsContentType(String contentType) {
        return delegate.supportsContentType(contentType);
    }

    @Override
    public String toString() {
        return "TracedEncoding{" + delegate + '}';
    }

    /**
     * Builds a human readable type string. Class types use the classes simple name, however complex types do not have
     * this optimization because it is more complex than it's worth for now.
     */
    static String toString(TypeMarker<?> typeMarker) {
        Type type = typeMarker.getType();
        if (type instanceof Class) {
            return ((Class<?>) type).getSimpleName();
        }
        return type.toString();
    }

    private static final class TracedSerializer<T> implements Serializer<T> {

        private final Serializer<T> delegate;
        private final String operation;

        TracedSerializer(Serializer<T> delegate, String operation) {
            this.delegate = delegate;
            this.operation = operation;
        }

        @Override
        public void serialize(T value, OutputStream output) throws IOException {
            try (CloseableTracer ignored = CloseableTracer.startSpan(operation)) {
                delegate.serialize(value, output);
            }
        }

        @Override
        public String toString() {
            return "TracedSerializer{delegate=" + delegate + ", operation='" + operation + "'}";
        }
    }

    private static final class TracedDeserializer<T> implements Deserializer<T> {

        private final Deserializer<T> delegate;
        private final String operation;

        TracedDeserializer(Deserializer<T> delegate, String operation) {
            this.delegate = delegate;
            this.operation = operation;
        }

        @Override
        public T deserialize(InputStream input) throws IOException {
            try (CloseableTracer ignored = CloseableTracer.startSpan(operation)) {
                return delegate.deserialize(input);
            }
        }

        @Override
        public String toString() {
            return "TracedDeserializer{delegate=" + delegate + ", operation='" + operation + "'}";
        }
    }
}
