/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.net.HttpHeaders;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

/**
 * Adds support for transparently encoding sending <code>Content-Encoding: gzip</code> requests
 * in a client agnostic way based on a Conjure endpoint tag. This requires prior knowledge that
 * the remote server is capable of handling compressed data.
 */
final class ContentEncodingChannel implements EndpointChannel {

    private static final String ENABLEMENT_TAG = "compress-request";
    private static final String GZIP = "gzip";
    private static final int BUFFER_SIZE = 8 * 1024;

    private final EndpointChannel delegate;

    /** Wraps a delegate if the endpoint has opted into request compression. */
    static EndpointChannel of(EndpointChannel delegate, Endpoint endpoint) {
        if (endpoint.tags().contains(ENABLEMENT_TAG)) {
            return new ContentEncodingChannel(delegate);
        }
        return delegate;
    }

    ContentEncodingChannel(EndpointChannel delegate) {
        this.delegate = Preconditions.checkNotNull(delegate, "Channel is required");
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        Request augmentedRequest = wrap(request);
        return delegate.execute(augmentedRequest);
    }

    static Request wrap(Request request) {
        Optional<RequestBody> body = request.body();
        if (body.isEmpty()
                || request.headerParams().containsKey(HttpHeaders.CONTENT_ENCODING)
                || request.headerParams().containsKey(HttpHeaders.CONTENT_LENGTH)) {
            // Do not replace existing content-encoding values
            return request;
        }
        return Request.builder()
                .from(request)
                .putHeaderParams(HttpHeaders.CONTENT_ENCODING, GZIP)
                .body(new ContentEncodingRequestBody(body.get()))
                .build();
    }

    private static final class ContentEncodingRequestBody implements RequestBody {

        private final RequestBody delegate;

        ContentEncodingRequestBody(RequestBody delegate) {
            this.delegate = delegate;
        }

        @Override
        public void writeTo(OutputStream output) throws IOException {
            try (OutputStream gzipOutput = new BestSpeedGzipOutputStream(output);
                    // Buffer inputs to the compressor to reduce native interaction overhead
                    OutputStream bufferedOutput = new BufferedOutputStream(gzipOutput, BUFFER_SIZE)) {
                delegate.writeTo(bufferedOutput);
            }
        }

        @Override
        public String contentType() {
            return delegate.contentType();
        }

        @Override
        public boolean repeatable() {
            return delegate.repeatable();
        }

        @Override
        public OptionalLong contentLength() {
            // When content is compressed, the content-length is mutated.
            return OptionalLong.empty();
        }

        @Override
        public void close() {
            delegate.close();
        }

        @Override
        public String toString() {
            return "ContentEncodingRequestBody{" + delegate + '}';
        }
    }

    /**
     * Specialized implementation of {@link GZIPOutputStream} which uses {@link Deflater#BEST_SPEED} to reduce
     * CPU utilization with a slight cost to compression ratio.
     */
    private static final class BestSpeedGzipOutputStream extends GZIPOutputStream {

        BestSpeedGzipOutputStream(OutputStream out) throws IOException {
            super(out, BUFFER_SIZE);
            def.setLevel(Deflater.BEST_SPEED);
        }

        @Override
        public String toString() {
            return "BestSpeedGzipOutputStream{" + out + '}';
        }
    }

    @Override
    public String toString() {
        return "ContentEncodingChannel{" + delegate + '}';
    }
}
