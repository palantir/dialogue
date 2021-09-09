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

import com.google.common.collect.ImmutableMap;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tracing.Tracer;
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
        return new TracedSerializer<>(
                delegate.serializer(type),
                "Dialogue: serialize",
                ImmutableMap.of("type", toString(type), "contentType", getContentType()));
    }

    @Override
    public <T> Deserializer<T> deserializer(TypeMarker<T> type) {
        return new TracedDeserializer<>(
                delegate.deserializer(type),
                "Dialogue: deserialize",
                ImmutableMap.of("type", toString(type), "contentType", getContentType()));
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

        private static final SafeLogger log = SafeLoggerFactory.get(TracedSerializer.class);

        private final Serializer<T> delegate;
        private final String operation;
        private final ImmutableMap<String, String> tags;

        TracedSerializer(Serializer<T> delegate, String operation, ImmutableMap<String, String> tags) {
            this.delegate = delegate;
            this.operation = operation;
            this.tags = tags;
        }

        @Override
        public void serialize(T value, OutputStream output) throws IOException {
            if (log.isDebugEnabled()) {
                Tracer.fastStartSpan(operation);
                try {
                    delegate.serialize(value, output);
                } finally {
                    Tracer.fastCompleteSpan(tags);
                }
            } else {
                delegate.serialize(value, output);
            }
        }

        @Override
        public String toString() {
            return "TracedSerializer{delegate=" + delegate + ", operation='" + operation + "'}";
        }
    }

    private static final class TracedDeserializer<T> implements Deserializer<T> {

        private static final SafeLogger log = SafeLoggerFactory.get(TracedDeserializer.class);

        private final Deserializer<T> delegate;
        private final String operation;
        private final ImmutableMap<String, String> tags;

        TracedDeserializer(Deserializer<T> delegate, String operation, ImmutableMap<String, String> tags) {
            this.delegate = delegate;
            this.operation = operation;
            this.tags = tags;
        }

        @Override
        public T deserialize(InputStream input) throws IOException {
            if (log.isDebugEnabled()) {
                Tracer.fastStartSpan(operation);
                try {
                    return delegate.deserialize(input);
                } finally {
                    Tracer.fastCompleteSpan(tags);
                }
            } else {
                return delegate.deserialize(input);
            }
        }

        @Override
        public String toString() {
            return "TracedDeserializer{delegate=" + delegate + ", operation='" + operation + "', tags=" + tags + "}";
        }
    }
}
