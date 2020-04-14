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
package com.palantir.dialogue.blocking;

import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.logsafe.SafeArg;
import com.palantir.tracing.Tracers;
import com.palantir.tritium.metrics.MetricRegistries;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Simple adapter to allow simple {@link BlockingChannel} implementations to be used as {@link Channel channels}. */
public final class BlockingChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(BlockingChannelAdapter.class);

    @SuppressWarnings("deprecation") // No reasonable way to pass a tagged registry to this singleton
    private static final Supplier<ExecutorService> blockingExecutor = Suppliers.memoize(() -> Tracers.wrap(
            "dialogue-blocking-channel",
            Executors.newCachedThreadPool(MetricRegistries.instrument(
                    SharedTaggedMetricRegistries.getSingleton(),
                    new ThreadFactoryBuilder()
                            .setNameFormat("dialogue-blocking-channel-%d")
                            .setDaemon(true)
                            .build(),
                    "dialogue-blocking-channel"))));

    public static Channel of(BlockingChannel blockingChannel) {
        return of(blockingChannel, blockingExecutor.get());
    }

    private static Channel of(BlockingChannel blockingChannel, ExecutorService executor) {
        return new BlockingChannelAdapterChannel(blockingChannel, executor);
    }

    private BlockingChannelAdapter() {}

    private static final class BlockingChannelAdapterChannel implements Channel {

        private final BlockingChannel delegate;
        private final ExecutorService executor;

        BlockingChannelAdapterChannel(BlockingChannel delegate, ExecutorService executor) {
            this.delegate = delegate;
            this.executor = executor;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            SettableFuture<Response> settableFuture = SettableFuture.create();
            BlockingChannelAdapterTask runnable =
                    new BlockingChannelAdapterTask(delegate, endpoint, request, settableFuture);
            Future<?> future = executor.submit(runnable);
            // The executor task should be interrupted on termination
            Futures.addCallback(
                    settableFuture,
                    new FutureCallback<Response>() {
                        @Override
                        public void onSuccess(Response _result) {}

                        @Override
                        public void onFailure(Throwable throwable) {
                            if (throwable instanceof CancellationException) {
                                future.cancel(true);
                            }
                        }
                    },
                    MoreExecutors.directExecutor());
            return settableFuture;
        }

        @Override
        public String toString() {
            return "BlockingChannelAdapterChannel{delegate=" + delegate + ", executor=" + executor + '}';
        }

        static final class BlockingChannelAdapterTask implements Runnable {

            private final SettableFuture<Response> result;
            private final BlockingChannel delegate;
            private final Endpoint endpoint;
            private final Request request;

            BlockingChannelAdapterTask(
                    BlockingChannel delegate, Endpoint endpoint, Request request, SettableFuture<Response> result) {
                this.result = result;
                this.delegate = delegate;
                this.endpoint = endpoint;
                this.request = request;
            }

            @Override
            public void run() {
                // If a user cancels the SettableFuture we exposed, then there's no need to call the delegate.
                if (result.isDone()) {
                    return;
                }
                try {
                    Response response = delegate.execute(endpoint, request);
                    if (!result.set(response)) {
                        log.info(
                                "Future has already been completed, response will be closed",
                                SafeArg.of("service", endpoint.serviceName()),
                                SafeArg.of("endpoint", endpoint.endpointName()));
                        response.close();
                    }
                } catch (Throwable t) {
                    if (!result.setException(t)) {
                        log.info(
                                "Failed to set future exception",
                                SafeArg.of("service", endpoint.serviceName()),
                                SafeArg.of("endpoint", endpoint.endpointName()),
                                t);
                    }
                }
            }

            @Override
            public String toString() {
                return "BlockingChannelAdapterTask{delegate="
                        + delegate
                        + ", endpoint="
                        + endpoint
                        + ", request="
                        + request
                        + '}';
            }
        }
    }
}
