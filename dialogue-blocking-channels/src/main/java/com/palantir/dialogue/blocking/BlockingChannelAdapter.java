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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.tracing.Tracers;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** Simple adapter to allow simple {@link BlockingChannel} implementations to be used as {@link Channel channels}. */
public final class BlockingChannelAdapter {

    private static final Supplier<ListeningExecutorService> blockingExecutor = Suppliers.memoize(
            () -> MoreExecutors.listeningDecorator(Tracers.wrap(Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                    .setNameFormat("dialogue-blocking-channel-%d")
                    .setDaemon(true)
                    .build()))));

    public static Channel of(BlockingChannel blockingChannel) {
        return of(blockingChannel, blockingExecutor.get());
    }

    private static Channel of(BlockingChannel blockingChannel, ListeningExecutorService executor) {
        return new BlockingChannelAdapterChannel(blockingChannel, executor);
    }

    private BlockingChannelAdapter() {}

    private static final class BlockingChannelAdapterChannel implements Channel, Closeable {

        private final BlockingChannel delegate;
        private final ListeningExecutorService executor;

        BlockingChannelAdapterChannel(BlockingChannel delegate, ListeningExecutorService executor) {
            this.delegate = delegate;
            this.executor = executor;
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            return executor.submit(new BlockingChannelAdapterTask(delegate, endpoint, request));
        }

        @Override
        public String toString() {
            return "BlockingChannelAdapterChannel{delegate=" + delegate + ", executor=" + executor + '}';
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        /**
         * Callable to run a blocking request. This could be a lambda,
         * but concrete classes provide better stack traces.
         */
        static final class BlockingChannelAdapterTask implements Callable<Response> {

            private final BlockingChannel delegate;
            private final Endpoint endpoint;
            private final Request request;

            BlockingChannelAdapterTask(BlockingChannel delegate, Endpoint endpoint, Request request) {
                this.delegate = delegate;
                this.endpoint = endpoint;
                this.request = request;
            }

            @Override
            public Response call() throws IOException {
                return delegate.execute(endpoint, request);
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
