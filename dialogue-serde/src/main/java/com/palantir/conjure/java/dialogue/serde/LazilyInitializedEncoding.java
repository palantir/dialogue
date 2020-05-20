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

import com.google.common.base.Suppliers;
import com.palantir.dialogue.TypeMarker;
import com.palantir.logsafe.Preconditions;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

/**
 * Wrapper around an {@link Encoding} which allows both {@link Serializer} and {@link Deserializer} instances
 * to be created when they're first used. This avoids the up-front cost of creating jackson <pre>ObjectReader</pre>
 * and <pre>ObjectWriter</pre> instances for each request and response body type, for each encoding on service
 * startup when many endpoints are only used with one encoding based on the clients that make requests.
 * Note that this results in the first request to a given endpoint being more expensive than it would be
 * otherwise, though this is already the case to an extent before the JIT compiler can optimize the path.
 */
final class LazilyInitializedEncoding implements Encoding {

    private final Encoding delegate;

    LazilyInitializedEncoding(Encoding delegate) {
        this.delegate = Preconditions.checkNotNull(delegate, "Encoding is required");
    }

    @Override
    public <T> Serializer<T> serializer(TypeMarker<T> type) {
        return new LazilyInitializedSerializer<>(() -> delegate.serializer(type));
    }

    @Override
    public <T> Deserializer<T> deserializer(TypeMarker<T> type) {
        return new LazilyInitializedDeserializer<>(() -> delegate.deserializer(type));
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
        return "LazilyInitializedEncoding{" + delegate + '}';
    }

    private static final class LazilyInitializedSerializer<T> implements Serializer<T> {

        private final Supplier<Serializer<T>> delegate;

        LazilyInitializedSerializer(Supplier<Serializer<T>> delegate) {
            this.delegate = Suppliers.memoize(delegate::get);
        }

        @Override
        public void serialize(T value, OutputStream output) {
            delegate.get().serialize(value, output);
        }

        @Override
        public String toString() {
            return "LazilyInitializedSerializer{" + delegate + '}';
        }
    }

    private static final class LazilyInitializedDeserializer<T> implements Deserializer<T> {

        private final Supplier<Deserializer<T>> delegate;

        LazilyInitializedDeserializer(Supplier<Deserializer<T>> delegate) {
            this.delegate = Suppliers.memoize(delegate::get);
        }

        @Override
        public T deserialize(InputStream input) {
            return delegate.get().deserialize(input);
        }

        @Override
        public String toString() {
            return "LazilyInitializedDeserializer{" + delegate + '}';
        }
    }
}
