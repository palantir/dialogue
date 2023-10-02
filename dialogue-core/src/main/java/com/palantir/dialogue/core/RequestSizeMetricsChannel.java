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
import com.google.common.base.Suppliers;
import com.google.common.io.CountingOutputStream;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;
import java.util.function.Supplier;

final class RequestSizeMetricsChannel implements EndpointChannel {
    private static final SafeLogger log = SafeLoggerFactory.get(RequestSizeMetricsChannel.class);
    // MIN_REPORTED_REQUEST_SIZE filters recording small requests to reduce metric cardinality
    private static final long MIN_REPORTED_REQUEST_SIZE = 1 << 20;
    private final EndpointChannel delegate;
    private final Supplier<Histogram> retryableRequestSize;
    private final Supplier<Histogram> nonretryableRequestSize;

    static EndpointChannel create(Config cf, EndpointChannel channel, Endpoint endpoint) {
        ClientConfiguration clientConf = cf.clientConf();
        return new RequestSizeMetricsChannel(channel, cf.channelName(), endpoint, clientConf.taggedMetricRegistry());
    }

    RequestSizeMetricsChannel(
            EndpointChannel delegate, String channelName, Endpoint endpoint, TaggedMetricRegistry registry) {
        this.delegate = delegate;
        DialogueClientMetrics dialogueClientMetrics = DialogueClientMetrics.of(registry);
        this.retryableRequestSize = Suppliers.memoize(() -> dialogueClientMetrics
                .requestsSize()
                .channelName(channelName)
                .serviceName(endpoint.serviceName())
                .endpoint(endpoint.endpointName())
                .retryable("true")
                .build());
        this.nonretryableRequestSize = Suppliers.memoize(() -> dialogueClientMetrics
                .requestsSize()
                .channelName(channelName)
                .serviceName(endpoint.serviceName())
                .endpoint(endpoint.endpointName())
                .retryable("false")
                .build());
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
        Supplier<Histogram> requestSizeHistogram =
                body.get().repeatable() ? this.retryableRequestSize : this.nonretryableRequestSize;

        return Request.builder()
                .from(request)
                .body(new RequestSizeRecordingRequestBody(body.get(), requestSizeHistogram))
                .build();
    }

    private static class RequestSizeRecordingRequestBody implements RequestBody {
        private final RequestBody delegate;
        private final Supplier<Histogram> size;

        RequestSizeRecordingRequestBody(RequestBody requestBody, Supplier<Histogram> size) {
            this.delegate = requestBody;
            this.size = size;
        }

        @Override
        public void writeTo(OutputStream output) throws IOException {
            CountingOutputStream countingOut = new CountingOutputStream(output);
            delegate.writeTo(countingOut);
            if (countingOut.getCount() > MIN_REPORTED_REQUEST_SIZE) {
                size.get().update(countingOut.getCount());
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
        public void close() {
            delegate.close();
        }
    }
}
