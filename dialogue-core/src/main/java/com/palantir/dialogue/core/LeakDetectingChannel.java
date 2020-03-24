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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Random;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class LeakDetectingChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(LeakDetectingChannel.class);

    private final Channel delegate;
    private final String channelName;
    private final DialogueClientMetrics metrics;
    private final Random random;
    private final float leakDetectionProbability;

    LeakDetectingChannel(
            Channel delegate,
            String channelName,
            DialogueClientMetrics metrics,
            Random random,
            float leakDetectionProbability) {
        this.delegate = delegate;
        this.channelName = channelName;
        this.metrics = metrics;
        this.random = random;
        this.leakDetectionProbability = leakDetectionProbability;
        Preconditions.checkArgument(
                leakDetectionProbability >= 0, "Leak detection probability must be positive or zero");
        Preconditions.checkArgument(leakDetectionProbability <= 1, "Leak detection probability must not exceed one");
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        ListenableFuture<Response> result = delegate.execute(endpoint, request);
        return shouldApplyLeakDetection() ? wrap(result, endpoint) : result;
    }

    private ListenableFuture<Response> wrap(ListenableFuture<Response> result, Endpoint endpoint) {
        return Futures.transform(
                result,
                response -> new LeakDetectingResponse(response, new LeakDetector(response, endpoint)),
                MoreExecutors.directExecutor());
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
                        .channelName(channelName)
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
                            SafeArg.of("channel", channelName));
                } else {
                    log.warn(
                            "Detected a leaked response from service {} endpoint {} on channel {}",
                            SafeArg.of("service", endpoint.serviceName()),
                            SafeArg.of("endpoint", endpoint.endpointName()),
                            SafeArg.of("channel", channelName),
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
