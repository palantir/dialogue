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
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.random.SafeThreadLocalRandom;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Random;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResponseLeakDetector {

    private static final Logger log = LoggerFactory.getLogger(ResponseLeakDetector.class);

    private final String clientName;
    private final DialogueClientMetrics metrics;
    private final Random random;
    private final float leakDetectionProbability;

    public static ResponseLeakDetector of(String clientName, TaggedMetricRegistry metrics) {
        return new ResponseLeakDetector(
                clientName, DialogueClientMetrics.of(metrics), SafeThreadLocalRandom.get(), .01f);
    }

    ResponseLeakDetector(
            String clientName, DialogueClientMetrics metrics, Random random, float leakDetectionProbability) {
        this.clientName = clientName;
        this.metrics = metrics;
        this.random = random;
        this.leakDetectionProbability = leakDetectionProbability;
    }

    public Response wrap(Response input, Endpoint endpoint) {
        if (shouldApplyLeakDetection()) {
            return new LeakDetectingResponse(input, new LeakDetector(input, endpoint));
        }
        return input;
    }

    private boolean shouldApplyLeakDetection() {
        if (leakDetectionProbability >= 1) {
            return true;
        }
        if (leakDetectionProbability <= 0) {
            return false;
        }
        return random.nextFloat() <= leakDetectionProbability;
    }

    /**
     * {@link LeakDetector} object is shared between the {@link Response} and {@link Response#body()} to ensure
     * at least one of the two has been closed.
     */
    private final class LeakDetector {

        private final Endpoint endpoint;
        private final Response response;

        @Nullable
        private final Throwable creationTrace;

        private boolean armed = true;

        LeakDetector(Response response, Endpoint endpoint) {
            this.response = response;
            this.endpoint = endpoint;
            this.creationTrace = log.isTraceEnabled() ? new SafeRuntimeException("created here") : null;
        }

        void disarm() {
            armed = false;
        }

        @Override
        @SuppressWarnings("NoFinalizer")
        protected void finalize() throws Throwable {
            if (armed) {
                metrics.responseLeak()
                        .clientName(clientName)
                        .serviceName(endpoint.serviceName())
                        .endpoint(endpoint.endpointName())
                        .build()
                        .mark();
                if (creationTrace == null) {
                    log.warn(
                            "Detected a leaked response from service {} endpoint {} on channel {}. Enable trace "
                                    + "logging to record stack traces.",
                            SafeArg.of("service", endpoint.serviceName()),
                            SafeArg.of("endpoint", endpoint.endpointName()),
                            SafeArg.of("client", clientName));
                } else {
                    log.warn(
                            "Detected a leaked response from service {} endpoint {} on channel {}",
                            SafeArg.of("service", endpoint.serviceName()),
                            SafeArg.of("endpoint", endpoint.endpointName()),
                            SafeArg.of("client", clientName),
                            creationTrace);
                }
                response.close();
            }
            super.finalize();
        }

        @Override
        public String toString() {
            return "LeakDetector{endpoint=" + endpoint + ", response=" + response + ", armed=" + armed + '}';
        }
    }

    private static final class LeakDetectingInputStream extends FilterInputStream {

        private final LeakDetector leakDetector;

        LeakDetectingInputStream(InputStream delegate, LeakDetector leakDetector) {
            super(delegate);
            this.leakDetector = leakDetector;
        }

        @Override
        public void close() throws IOException {
            leakDetector.disarm();
            super.close();
        }

        @Override
        public String toString() {
            return "LeakDetectingInputStream{leakDetector=" + leakDetector + ", in=" + in + '}';
        }
    }

    private static final class LeakDetectingResponse implements Response {

        private final Response delegate;
        private final LeakDetector leakDetector;

        @Nullable
        private InputStream leakDetectingStream;

        LeakDetectingResponse(Response delegate, LeakDetector leakDetector) {
            this.delegate = delegate;
            this.leakDetector = leakDetector;
        }

        @Override
        public InputStream body() {
            if (leakDetectingStream == null) {
                leakDetectingStream = new LeakDetectingInputStream(delegate.body(), leakDetector);
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
        public void close() {
            leakDetector.disarm();
            delegate.close();
        }

        @Override
        public String toString() {
            return "LeakDetectingResponse{delegate=" + delegate + ", leakDetector=" + leakDetector + '}';
        }
    }
}
