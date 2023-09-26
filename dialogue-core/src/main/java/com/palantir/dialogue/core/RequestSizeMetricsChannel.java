/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import com.codahale.metrics.Histogram;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

final class RequestSizeMetricsChannel implements EndpointChannel {
    private final EndpointChannel delegate;
    private final Histogram requestSize;

    static EndpointChannel create(Config cf, EndpointChannel channel, Endpoint endpoint) {
        ClientConfiguration clientConf = cf.clientConf();
        return new RequestSizeMetricsChannel(channel, endpoint, clientConf.taggedMetricRegistry());
    }

    RequestSizeMetricsChannel(EndpointChannel delegate, Endpoint endpoint, TaggedMetricRegistry registry) {
        this.delegate = delegate;
        DialogueClientMetrics dialogueClientMetrics = DialogueClientMetrics.of(registry);
        this.requestSize = dialogueClientMetrics
                .requestsSize()
                .serviceName(endpoint.serviceName())
                .endpoint(endpoint.endpointName())
                .build();
    }

    @Override
    public ListenableFuture<Response> execute(Request request) {
        Request augmentedRequest = wrap(request);
        return delegate.execute(augmentedRequest);
    }

    private Request wrap(Request request) {
        Optional<RequestBody> body = request.body();
        if (body.isEmpty()) {
            // No need to record empty bodies
            return request;
        }

        return Request.builder()
                .from(request)
                .body(new RequestSizeRecordingRequestBody(body.get(), this.requestSize))
                .build();
    }

    private class RequestSizeRecordingRequestBody implements RequestBody {
        private final RequestBody delegate;
        private final Histogram size;
        private SizeTrackingOutputStream out;

        RequestSizeRecordingRequestBody(RequestBody requestBody, Histogram size) {
            this.delegate = requestBody;
            this.size = size;
            // we'll never actually write to this output stream, but is safe to perform all operations on in case a
            // client closes without calling write.
            this.out = new SizeTrackingOutputStream(OutputStream.nullOutputStream(), size);
        }

        @Override
        public void writeTo(OutputStream output) throws IOException {
            out = new SizeTrackingOutputStream(output, size);
            delegate.writeTo(out);
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
        public void close() {
            try {
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            delegate.close();
        }

        /**
         * {@link SizeTrackingOutputStream} records the total number of bytes written to the output stream.
         */
        private final class SizeTrackingOutputStream extends FilterOutputStream {
            private final Histogram size;
            private long writes = 0;

            SizeTrackingOutputStream(OutputStream delegate, Histogram size) {
                super(delegate);
                this.size = size;
            }

            @Override
            public void write(byte[] buffer, int off, int len) throws IOException {
                writes += len;
                out.write(buffer, off, len);
            }

            @Override
            public void write(int value) throws IOException {
                writes += 1;
                out.write(value);
            }

            @Override
            public void close() throws IOException {
                this.size.update(writes);
                super.close();
            }
        }
    }
}
