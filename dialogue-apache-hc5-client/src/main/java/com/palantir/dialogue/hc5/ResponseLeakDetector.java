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

package com.palantir.dialogue.hc5;

import com.google.common.collect.ListMultimap;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.ResponseAttachments;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import com.palantir.tracing.Tracer;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.Cleaner.Cleanable;
import java.util.Optional;
import javax.annotation.Nullable;

final class ResponseLeakDetector {

    private static final SafeLogger log = SafeLoggerFactory.get(ResponseLeakDetector.class);

    private final String clientName;
    private final DialogueClientMetrics metrics;

    static ResponseLeakDetector of(String clientName, TaggedMetricRegistry metrics) {
        return new ResponseLeakDetector(clientName, DialogueClientMetrics.of(metrics));
    }

    ResponseLeakDetector(String clientName, DialogueClientMetrics metrics) {
        this.clientName = clientName;
        this.metrics = metrics;
    }

    Response wrap(Response input, Endpoint endpoint) {
        LeakDetector detector = new LeakDetector(input, endpoint, clientName, metrics);
        LeakDetectingResponse response = new LeakDetectingResponse(input, detector);
        return response;
    }

    @Override
    public String toString() {
        return "ResponseLeakDetector{clientName='" + clientName + '}';
    }

    /**
     * {@link LeakDetector} object is shared between the {@link Response} and {@link Response#body()} to ensure
     * at least one of the two has been closed.
     */
    private static final class LeakDetector implements Runnable {

        private final Endpoint endpoint;
        private final Response response;
        private final String clientName;
        private final DialogueClientMetrics metrics;

        @Nullable
        private final String creationTraceId;

        @Nullable
        private final Throwable creationTrace;

        private boolean armed = true;

        LeakDetector(Response response, Endpoint endpoint, String clientName, DialogueClientMetrics metrics) {
            this.response = response;
            this.endpoint = endpoint;
            this.clientName = clientName;
            this.metrics = metrics;
            this.creationTraceId = Tracer.hasTraceId() ? Tracer.getTraceId() : null;
            this.creationTrace = log.isTraceEnabled() ? new SafeRuntimeException("created here") : null;
        }

        void disarm() {
            armed = false;
        }

        @Override
        public void run() {
            if (armed) {
                metrics.responseLeak()
                        .clientName(clientName)
                        .serviceName(endpoint.serviceName())
                        .endpoint(endpoint.endpointName())
                        .build()
                        .mark();
                if (creationTrace == null) {
                    log.warn(
                            "Detected a leaked response from service {} endpoint {} on channel {} with traceId {}. "
                                    + "Enable trace logging to record stack traces.",
                            SafeArg.of("service", endpoint.serviceName()),
                            SafeArg.of("endpoint", endpoint.endpointName()),
                            SafeArg.of("client", clientName),
                            SafeArg.of("creationTraceId", creationTraceId));
                } else {
                    log.warn(
                            "Detected a leaked response from service {} endpoint {} on channel {} with traceId {}",
                            SafeArg.of("service", endpoint.serviceName()),
                            SafeArg.of("endpoint", endpoint.endpointName()),
                            SafeArg.of("client", clientName),
                            SafeArg.of("creationTraceId", creationTraceId),
                            creationTrace);
                }
                response.close();
            }
        }

        @Override
        public String toString() {
            return "LeakDetector{endpoint=" + endpoint + ", response=" + response + ", armed=" + armed + '}';
        }
    }

    private static final class LeakDetectingInputStream extends FilterInputStream {

        private final LeakDetectingResponse leakDetectingResponse;

        LeakDetectingInputStream(InputStream delegate, LeakDetectingResponse leakDetectingResponse) {
            super(delegate);
            this.leakDetectingResponse = leakDetectingResponse;
        }

        @Override
        public void close() throws IOException {
            try {
                leakDetectingResponse.disarm();
            } finally {
                super.close();
            }
        }

        @Override
        public String toString() {
            return "LeakDetectingInputStream{leakDetectingResponse=" + leakDetectingResponse + ", in=" + in + '}';
        }
    }

    private static final class LeakDetectingResponse implements Response {

        private final Response delegate;
        private final LeakDetector leakDetector;
        private final Cleanable clean;

        @Nullable
        private InputStream leakDetectingStream;

        LeakDetectingResponse(Response delegate, LeakDetector leakDetector) {
            this.delegate = delegate;
            this.leakDetector = leakDetector;
            clean = CleanerSupport.register(this, leakDetector);
        }

        @Override
        public InputStream body() {
            if (leakDetectingStream == null) {
                leakDetectingStream = new LeakDetectingInputStream(delegate.body(), this);
            }
            return leakDetectingStream;
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
            try {
                disarm();
            } finally {
                delegate.close();
            }
        }

        void disarm() {
            leakDetector.disarm();
            clean.clean();
        }

        @Override
        public String toString() {
            return "LeakDetectingResponse{delegate=" + delegate + ", leakDetector=" + leakDetector + '}';
        }
    }
}
