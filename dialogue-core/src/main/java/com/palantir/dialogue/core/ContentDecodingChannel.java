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

package com.palantir.dialogue.core;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds support for transparently requesting and decoding <code>Content-Encoding: gzip</code> responses
 * in a client agnostic way. Client implementations may choose to decompress GZIP data more efficiently
 * if possible.
 * This allows client implementations to avoid considering content-encoding in most cases, and
 * sets a specific <code>Accept-Encoding</code> header to avoid potentially using an unexpected
 * type based on client defaults (for example apache httpclient requests gzip and deflate by default).
 * https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.3
 * https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.11
 */
final class ContentDecodingChannel implements EndpointChannelFactory {

    private static final Logger log = LoggerFactory.getLogger(ContentDecodingChannel.class);

    private static final String ACCEPT_ENCODING = "accept-encoding";
    private static final String CONTENT_ENCODING = "content-encoding";
    private static final String CONTENT_LENGTH = "content-length";
    private static final String GZIP = "gzip";

    private final EndpointChannelFactory delegate;

    ContentDecodingChannel(EndpointChannelFactory delegate) {
        this.delegate = Preconditions.checkNotNull(delegate, "Channel is required");
    }

    @Override
    public EndpointChannel endpoint(Endpoint endpoint) {
        EndpointChannel proceed = delegate.endpoint(endpoint);
        return new ContentDecodingEndpointChannel(proceed);
    }

    private static final class ContentDecodingEndpointChannel implements EndpointChannel {
        private final EndpointChannel proceed;

        private ContentDecodingEndpointChannel(EndpointChannel proceed) {
            this.proceed = proceed;
        }

        @Override
        public ListenableFuture<Response> execute(Request request) {
            Request augmentedRequest = acceptEncoding(request);
            return Futures.transform(
                    proceed.execute(augmentedRequest),
                    ContentDecodingChannel::decompress,
                    MoreExecutors.directExecutor());
        }

        @Override
        public String toString() {
            return "ContentDecodingEndpointChannel{" + proceed + '}';
        }
    }

    private static Request acceptEncoding(Request request) {
        if (request.headerParams().containsKey(ACCEPT_ENCODING)) {
            // Do not replace existing accept-encoding values
            return request;
        }
        return Request.builder()
                .from(request)
                .putHeaderParams(ACCEPT_ENCODING, GZIP)
                .build();
    }

    private static Response decompress(Response input) {
        Optional<String> contentEncoding = input.getFirstHeader(CONTENT_ENCODING);
        if (contentEncoding.isPresent() && GZIP.equals(contentEncoding.get())) {
            return new ContentDecodingResponse(input);
        }
        return input;
    }

    @Override
    public String toString() {
        return "ContentDecodingChannel{" + delegate + '}';
    }

    private static final class ContentDecodingResponse implements Response {

        private final Response delegate;
        private final ListMultimap<String, String> headers;
        private final InputStream body;

        ContentDecodingResponse(Response delegate) {
            this.delegate = delegate;
            this.headers = Multimaps.filterKeys(delegate.headers(), ContentDecodingResponse::allowHeader);
            this.body = new DeferredGzipInputStream(delegate.body());
        }

        @Override
        public InputStream body() {
            return body;
        }

        @Override
        public int code() {
            return delegate.code();
        }

        @Override
        public ListMultimap<String, String> headers() {
            return headers;
        }

        // Remove the content-encoding header once content is decompressed, otherwise consumers may attempt
        // to decode again.
        private static boolean allowHeader(String headerName) {
            return !CONTENT_ENCODING.equalsIgnoreCase(headerName)
                    // Content-length of compressed data is not representative of the decoded length.
                    && !CONTENT_LENGTH.equalsIgnoreCase(headerName);
        }

        @Override
        public String toString() {
            return "ContentDecodingResponse{delegate=" + delegate + '}';
        }

        @Override
        public void close() {
            try {
                body.close();
            } catch (IOException e) {
                log.warn("Failed to close encoded body", e);
            } finally {
                delegate.close();
            }
        }
    }

    /** Wraps a {@link GZIPInputStream} deferring initialization until first byte is read. */
    private static class DeferredGzipInputStream extends InputStream {
        private static final int BUFFER_SIZE = 8 * 1024;
        private final InputStream original;

        @Nullable
        private InputStream delegate;

        DeferredGzipInputStream(InputStream original) {
            this.original = original;
        }

        private InputStream getDelegate() throws IOException {
            if (delegate == null) {
                // Buffer the GZIPInputStream contents in order to reduce expensive native Deflater interactions.
                delegate = new BufferedInputStream(
                        // Increase the buffer size from the default of 512 bytes
                        new GZIPInputStream(original, BUFFER_SIZE /* input buffer size */),
                        BUFFER_SIZE /* output buffer size */);
            }
            return delegate;
        }

        private InputStream getDelegateSafely() {
            try {
                return getDelegate();
            } catch (IOException e) {
                throw new SafeRuntimeException("Failed to create a GZIPInputStream", e);
            }
        }

        @Override
        public int read() throws IOException {
            return getDelegate().read();
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            return getDelegate().read(buffer);
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            return getDelegate().read(buffer, off, len);
        }

        @Override
        public long skip(long requested) throws IOException {
            return getDelegate().skip(requested);
        }

        @Override
        public int available() throws IOException {
            return getDelegate().available();
        }

        @Override
        public void close() throws IOException {
            // No need to create the expensive delegate instance to immediately close it.
            if (delegate == null) {
                original.close();
            } else {
                delegate.close();
            }
        }

        @Override
        public void mark(int readlimit) {
            getDelegateSafely().mark(readlimit);
        }

        @Override
        public void reset() throws IOException {
            getDelegate().reset();
        }

        @Override
        public boolean markSupported() {
            return getDelegateSafely().markSupported();
        }

        @Override
        public String toString() {
            return "DeferredGzipInputStream{original=" + original + '}';
        }
    }
}
