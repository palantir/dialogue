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

package com.palantir.dialogue.clients;

import com.palantir.conjure.java.api.errors.ConjureErrorParameterFormat;
import com.palantir.dialogue.BinaryRequestBody;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Clients;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.ExceptionDeserializerArgs;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import java.io.InputStream;
import java.util.Optional;

/**
 * Wraps a user-supplied {@link ConjureRuntime} to override {@link BodySerDe#errorParameterFormat()}. Since
 * {@link ConjureRuntime} is an interface, we can't mutate the caller's instance (passed via {@code withRuntime}).
 */
final class ErrorSerializationFormatSettingConjureRuntime implements ConjureRuntime {
    private final ConjureRuntime delegate;
    private final BodySerDe bodySerDe;

    ErrorSerializationFormatSettingConjureRuntime(
            ConjureRuntime delegate, ConjureErrorParameterFormat errorParameterSerializationFormat) {
        this.delegate = delegate;
        this.bodySerDe = new ErrorParameterSerializationFormatSettingBodySerDe(
                delegate.bodySerDe(), errorParameterSerializationFormat);
    }

    @Override
    public BodySerDe bodySerDe() {
        return bodySerDe;
    }

    @Override
    public PlainSerDe plainSerDe() {
        return delegate.plainSerDe();
    }

    @Override
    public Clients clients() {
        return delegate.clients();
    }

    private static final class ErrorParameterSerializationFormatSettingBodySerDe implements BodySerDe {
        private final BodySerDe delegate;
        private final Optional<ConjureErrorParameterFormat> errorParameterFormat;

        ErrorParameterSerializationFormatSettingBodySerDe(
                BodySerDe delegate, ConjureErrorParameterFormat errorParameterFormat) {
            this.delegate = delegate;
            this.errorParameterFormat = Optional.of(errorParameterFormat);
        }

        @Override
        public Optional<ConjureErrorParameterFormat> errorParameterFormat() {
            return errorParameterFormat;
        }

        @Override
        public <T> Serializer<T> serializer(TypeMarker<T> type) {
            return delegate.serializer(type);
        }

        @Override
        public <T> Deserializer<T> deserializer(TypeMarker<T> type) {
            return delegate.deserializer(type);
        }

        @Override
        public <T> Deserializer<T> deserializer(ExceptionDeserializerArgs<T> exceptionDeserializerArgs) {
            return delegate.deserializer(exceptionDeserializerArgs);
        }

        @Override
        public Deserializer<Void> emptyBodyDeserializer() {
            return delegate.emptyBodyDeserializer();
        }

        @Override
        public Deserializer<Void> emptyBodyDeserializer(ExceptionDeserializerArgs<Void> exceptionDeserializerArgs) {
            return delegate.emptyBodyDeserializer(exceptionDeserializerArgs);
        }

        @Override
        public Deserializer<InputStream> inputStreamDeserializer() {
            return delegate.inputStreamDeserializer();
        }

        @Override
        public Deserializer<InputStream> inputStreamDeserializer(
                ExceptionDeserializerArgs<InputStream> exceptionDeserializerArgs) {
            return delegate.inputStreamDeserializer(exceptionDeserializerArgs);
        }

        @Override
        public Deserializer<Optional<InputStream>> optionalInputStreamDeserializer() {
            return delegate.optionalInputStreamDeserializer();
        }

        @Override
        public Deserializer<Optional<InputStream>> optionalInputStreamDeserializer(
                ExceptionDeserializerArgs<Optional<InputStream>> exceptionDeserializerArgs) {
            return delegate.optionalInputStreamDeserializer(exceptionDeserializerArgs);
        }

        @Override
        public RequestBody serialize(BinaryRequestBody value) {
            return delegate.serialize(value);
        }
    }
}
