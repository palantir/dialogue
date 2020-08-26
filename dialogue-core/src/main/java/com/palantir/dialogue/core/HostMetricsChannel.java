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

import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.client.config.HostEventsSink;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.blocking.BlockingChannel;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

final class HostMetricsChannel implements Filter {
    private final Ticker clock;
    private final HostEventsSink.HostEventCallback hostEventCallback;

    private HostMetricsChannel(HostEventsSink hostMetrics, Ticker ticker, String serviceName, String host, int port) {
        this.clock = ticker;
        this.hostEventCallback = Preconditions.checkNotNull(hostMetrics, "HostEventsSink is required")
                .callback(
                        Preconditions.checkNotNull(serviceName, "Service is required"),
                        Preconditions.checkNotNull(host, "Host is required"),
                        port);
    }

    static Filter create(Config cf, String uri) {
        Optional<HostEventsSink> hostEventsSink = cf.clientConf().hostEventsSink();
        if (!hostEventsSink.isPresent()) {
            return NoopFilter.INSTANCE;
        }

        if (hostEventsSink.get().getClass().getSimpleName().equals("NoOpHostEventsSink")) {
            // special-casing for the implementation in conjure-java-runtime
            return NoopFilter.INSTANCE;
        }

        try {
            URL parsed = new URL(uri);
            String host = parsed.getHost();
            int port = parsed.getPort() != -1 ? parsed.getPort() : parsed.getDefaultPort();

            return new HostMetricsChannel(hostEventsSink.get(), cf.ticker(), cf.channelName(), host, port);
        } catch (MalformedURLException e) {
            throw new SafeIllegalArgumentException("Failed to parse URI", UnsafeArg.of("uri", uri));
        }
    }

    @Override
    public ListenableFuture<Response> executeFilter(Endpoint endpoint, Request request, Channel next) {
        ListenableFuture<Response> result = next.execute(endpoint, request);
        DialogueFutures.addDirectCallback(result, new Callback());
        return result;
    }

    @Override
    public Response executeFilter(Endpoint endpoint, Request request, BlockingChannel next) throws IOException {
        long startNanos = clock.read();
        try {
            Response result = next.execute(endpoint, request);
            hostEventCallback.record(result.code(), TimeUnit.NANOSECONDS.toMicros(clock.read() - startNanos));
            return result;
        } catch (IOException e) {
            hostEventCallback.recordIoException();
            throw e;
        }
    }

    @Override
    public String toString() {
        return "HostMetricsChannel{hostEventCallback=" + hostEventCallback + '}';
    }

    private final class Callback implements FutureCallback<Response> {
        private final long startNanos = clock.read();

        @Override
        public void onSuccess(Response result) {
            hostEventCallback.record(result.code(), TimeUnit.NANOSECONDS.toMicros(clock.read() - startNanos));
        }

        @Override
        public void onFailure(Throwable throwable) {
            if (throwable instanceof IOException) {
                hostEventCallback.recordIoException();
            }
        }
    }
}
