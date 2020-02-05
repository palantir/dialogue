/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tritium.metrics.registry.MetricName;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A channel that observes metrics about the processed requests and responses.
 * TODO(rfink): Consider renaming since this is no longer the only one doing instrumentation
 */
final class InstrumentedChannel implements Channel {

    static final String CLIENT_RESPONSE_METRIC_NAME = "client.response";
    static final String SERVICE_NAME_TAG = "service-name";

    private final Channel delegate;
    private final TaggedMetricRegistry registry;

    InstrumentedChannel(Channel delegate, TaggedMetricRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        ListenableFuture<Response> response = delegate.execute(endpoint, request);
        Futures.addCallback(
                response,
                new FutureCallback<Response>() {
                    @Override
                    public void onSuccess(@Nullable Response _result) {
                        record(endpoint);
                    }

                    @Override
                    public void onFailure(Throwable _throwable) {
                        record(endpoint);
                    }

                    private void record(Endpoint endpoint) {
                        long micros = stopwatch.elapsed(TimeUnit.MICROSECONDS);
                        registry.timer(name(endpoint.serviceName())).update(micros, TimeUnit.MICROSECONDS);
                    }
                },
                MoreExecutors.directExecutor());

        return response;
    }

    private MetricName name(String serviceName) {
        return MetricName.builder()
                .safeName(CLIENT_RESPONSE_METRIC_NAME)
                .putSafeTags(SERVICE_NAME_TAG, serviceName)
                .build();
    }
}
