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
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Package-private internal api.
 * This partial Encoding implementation exists to allow binary responses to share the same safety
 * and validation provided by structured encodings. This is only consumed internally to create
 * a binary-specific <pre>EncodingDeserializerRegistry</pre>.
 */
enum BinaryEncoding implements Encoding {
    INSTANCE;

    static final String CONTENT_TYPE = "application/octet-stream";
    static final TypeMarker<InputStream> MARKER = new TypeMarker<InputStream>() {};
    static final TypeMarker<Optional<InputStream>> OPTIONAL_MARKER = new TypeMarker<Optional<InputStream>>() {};

    @Override
    public <T> Serializer<T> serializer(TypeMarker<T> _type) {
        throw new UnsupportedOperationException("BinaryEncoding does not support serializers");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Deserializer<T> deserializer(TypeMarker<T> type) {
        if (MARKER.equals(type)) {
            return (Deserializer<T>) InputStreamDeserializer.INSTANCE;
        } else if (OPTIONAL_MARKER.equals(type)) {
            return (Deserializer<T>) OptionalInputStreamDeserializer.INSTANCE;
        }
        throw new SafeIllegalStateException(
                "BinaryEncoding only supports InputStream and Optional<InputStream>", SafeArg.of("requested", type));
    }

    @Override
    public String getContentType() {
        return CONTENT_TYPE;
    }

    @Override
    public boolean supportsContentType(String contentType) {
        return Encodings.matchesContentType(CONTENT_TYPE, contentType);
    }

    @Override
    public String toString() {
        return "BinaryEncoding{" + CONTENT_TYPE + '}';
    }

    enum OptionalInputStreamDeserializer implements Deserializer<Optional<InputStream>> {
        INSTANCE;

        @Override
        public Optional<InputStream> deserialize(InputStream input) {
            // intentionally not closing this, otherwise users wouldn't be able to get any data out of it!
            return Optional.of(InputStreamDeserializer.INSTANCE.deserialize(input));
        }

        @Override
        public String toString() {
            return "OptionalInputStreamDeserializer{}";
        }
    }

    enum InputStreamDeserializer implements Deserializer<InputStream> {
        INSTANCE;

        @Override
        public InputStream deserialize(InputStream input) {
            // intentionally not closing this, otherwise users wouldn't be able to get any data out of it!
            return detectLeaks(input);
        }

        @Override
        public String toString() {
            return "InputStreamDeserializer{}";
        }
    }

    private static InputStream detectLeaks(InputStream in) {
        if (CleanerSupport.enabled()) {
            AtomicBoolean isClosed = new AtomicBoolean();
            CloseTrackingInputStream stream = new CloseTrackingInputStream(in, isClosed);
            CleanerSupport.register(stream, new CleanerRunnable(in, isClosed));
            return stream;
        }
        return in;
    }

    private static final class CleanerRunnable implements Runnable {
        private static final Logger log = LoggerFactory.getLogger(CleanerRunnable.class);

        @Nullable
        private InputStream stream;

        private final AtomicBoolean isClosed;

        CleanerRunnable(InputStream stream, AtomicBoolean isClosed) {
            Preconditions.checkArgument(
                    !(stream instanceof CloseTrackingInputStream),
                    "The delegate stream is required, not the CloseTrackingInputStream");
            this.stream = stream;
            this.isClosed = isClosed;
        }

        @Override
        public void run() {
            if (!isClosed.get()) {
                log.warn("Detected a leaked streaming binary response. "
                        + "This is a product bug, please use try-with-resource everywhere.");
                InputStream value = stream;
                if (value != null) {
                    stream = null;
                    try {
                        value.close();
                    } catch (IOException | RuntimeException e) {
                        log.warn("Failed to close leaked binary response stream", e);
                    }
                }
            }
        }
    }

    private static final class CloseTrackingInputStream extends FilterInputStream {

        private final AtomicBoolean closed;

        private CloseTrackingInputStream(InputStream in, AtomicBoolean closed) {
            super(in);
            this.closed = closed;
        }

        @Override
        public void close() throws IOException {
            closed.set(true);
            super.close();
        }
    }
}
