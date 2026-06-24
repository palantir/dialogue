/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.collect.ListMultimap;
import com.palantir.conjure.java.api.errors.ConjureErrorParameterFormat;
import com.palantir.dialogue.BinaryRequestBody;
import com.palantir.dialogue.BodySerDe;
import com.palantir.dialogue.Clients;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.ExceptionDeserializerArgs;
import com.palantir.dialogue.PlainSerDe;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.ResponseAttachments;
import com.palantir.dialogue.Serializer;
import com.palantir.dialogue.TypeMarker;
import java.io.InputStream;
import java.util.Optional;

/**
 * Wraps a user-supplied {@link ConjureRuntime} such that deserializers will throw an exception upon reading
 * more bytes than the specified limit.
 * <p>
 * Since {@link ConjureRuntime} is an interface, we can't mutate the caller's instance (passed via {@code withRuntime}).
 */
final class ResponseSizeLimitingConjureRuntime implements ConjureRuntime {
    private final ConjureRuntime delegate;
    private final BodySerDe bodySerDe;

    ResponseSizeLimitingConjureRuntime(ConjureRuntime delegate, long maxResponseSize) {
        this.delegate = delegate;
        this.bodySerDe = new ResponseSizeLimitingBodySerDe(delegate.bodySerDe(), maxResponseSize);
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

    private static final class ResponseSizeLimitingBodySerDe implements BodySerDe {
        private final BodySerDe delegate;
        private final long maxResponseSize;

        ResponseSizeLimitingBodySerDe(BodySerDe delegate, long maxResponseSize) {
            this.delegate = delegate;
            this.maxResponseSize = maxResponseSize;
        }

        @Override
        public Optional<ConjureErrorParameterFormat> errorParameterFormat() {
            return delegate.errorParameterFormat();
        }

        @Override
        public <T> Serializer<T> serializer(TypeMarker<T> type) {
            return delegate.serializer(type);
        }

        @Override
        public <T> Deserializer<T> deserializer(TypeMarker<T> type) {
            return new ResponseSizeLimitingDeserializer<>(delegate.deserializer(type), maxResponseSize);
        }

        @Override
        public <T> Deserializer<T> deserializer(ExceptionDeserializerArgs<T> exceptionDeserializerArgs) {
            return new ResponseSizeLimitingDeserializer<>(
                    delegate.deserializer(exceptionDeserializerArgs), maxResponseSize);
        }

        @Override
        public Deserializer<Void> emptyBodyDeserializer() {
            return new ResponseSizeLimitingDeserializer<>(delegate.emptyBodyDeserializer(), maxResponseSize);
        }

        @Override
        public Deserializer<Void> emptyBodyDeserializer(ExceptionDeserializerArgs<Void> exceptionDeserializerArgs) {
            return new ResponseSizeLimitingDeserializer<>(
                    delegate.emptyBodyDeserializer(exceptionDeserializerArgs), maxResponseSize);
        }

        // Note: we don't apply the limit to input stream deserializers, since they are not directly deserialized
        // and could be trivially limited by the callers if desired

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

    private static final class ResponseSizeLimitingDeserializer<T> implements Deserializer<T> {
        private final Deserializer<T> delegate;
        private final long maxResponseSize;

        ResponseSizeLimitingDeserializer(Deserializer<T> delegate, long maxResponseSize) {
            this.delegate = delegate;
            this.maxResponseSize = maxResponseSize;
        }

        @Override
        public T deserialize(Response response) {
            return delegate.deserialize(new ResponseSizeLimitingResponse(response, maxResponseSize));
        }

        @Override
        public Optional<String> accepts() {
            return Optional.empty();
        }
    }

    private static final class ResponseSizeLimitingResponse implements Response {
        private final Response delegate;
        private final SizeLimitedInputStream limitedBody;

        ResponseSizeLimitingResponse(Response delegate, long maxResponseSize) {
            this.delegate = delegate;
            this.limitedBody = new SizeLimitedInputStream(delegate.body(), maxResponseSize);
        }

        @Override
        public InputStream body() {
            return limitedBody;
        }

        @Override
        public int code() {
            return delegate.code();
        }

        @Override
        public ListMultimap<String, String> headers() {
            return delegate.headers();
        }

        @Override
        public Optional<String> getFirstHeader(String header) {
            return delegate.getFirstHeader(header);
        }

        @Override
        public ResponseAttachments attachments() {
            return delegate.attachments();
        }

        @Override
        public void close() {
            // This will close the underlying body input stream as well as needed
            // We don't need to close the SizeLimitedInputStream wrapper itself
            delegate.close();
        }
    }
}
