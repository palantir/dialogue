/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.QueuedChannel.QueuedChannelInstrumentation;
import com.palantir.dialogue.futures.DialogueFutures;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

final class StickyEndpointChannels2 implements Supplier<Channel> {

    private final Supplier<EndpointChannelFactory> delegate;

    StickyEndpointChannels2(Supplier<EndpointChannelFactory> endpointChannelFactory) {
        this.delegate = endpointChannelFactory;
    }

    @Override
    public Channel get() {
        return new StickyChannel2(delegate.get());
    }

    @Override
    public String toString() {
        return "StickyEndpointChannels2{" + delegate + "}";
    }

    static Supplier<Channel> create(Config cf, LimitedChannel nodeSelectionChannel, EndpointChannelFactory delegate) {
        Supplier<Channel> queueOverrideSupplier = new QueueOverrideSupplier(cf, nodeSelectionChannel);
        return new StickyEndpointChannels2(
                new StickyEndpointChannels2EndpointFactorySupplier(queueOverrideSupplier, delegate));
    }

    private static final class QueueOverrideSupplier implements Supplier<Channel> {

        private final String channelName;
        private final int maxQueueSize;
        private final QueuedChannelInstrumentation queuedChannelInstrumentation;
        private final LimitedChannel nodeSelectionChannel;

        private QueueOverrideSupplier(Config cf, LimitedChannel nodeSelectionChannel) {
            this.channelName = cf.channelName();
            this.maxQueueSize = cf.maxQueueSize();
            this.queuedChannelInstrumentation = QueuedChannel.stickyInstrumentation(
                    DialogueClientMetrics.of(cf.clientConf().taggedMetricRegistry()), channelName);
            this.nodeSelectionChannel = nodeSelectionChannel;
        }

        @Override
        public Channel get() {
            LimitedChannel stickyLimitedChannel =
                    StickyConcurrencyLimitedChannel.create(nodeSelectionChannel, channelName);
            return QueuedChannel.createForSticky(
                    channelName, maxQueueSize, queuedChannelInstrumentation, stickyLimitedChannel);
        }
    }

    private static final class StickyEndpointChannels2EndpointFactorySupplier
            implements Supplier<EndpointChannelFactory> {

        private final Supplier<Channel> queueOverrideSupplier;
        private final EndpointChannelFactory delegate;

        StickyEndpointChannels2EndpointFactorySupplier(
                Supplier<Channel> queueOverrideSupplier, EndpointChannelFactory delegate) {
            this.queueOverrideSupplier = queueOverrideSupplier;
            this.delegate = delegate;
        }

        @Override
        public EndpointChannelFactory get() {
            Channel queueOverride = queueOverrideSupplier.get();
            return endpoint -> {
                EndpointChannel endpointChannel = delegate.endpoint(endpoint);
                return (EndpointChannel) request -> {
                    QueueAttachments.setQueueOverride(request, queueOverride);
                    return endpointChannel.execute(request);
                };
            };
        }
    }

    private static final class StickyChannel2 implements EndpointChannelFactory, Channel {

        private final EndpointChannelFactory channelFactory;
        private final StickyRouter router = new StickyRouter();

        private StickyChannel2(EndpointChannelFactory channelFactory) {
            this.channelFactory = channelFactory;
        }

        @Override
        public EndpointChannel endpoint(Endpoint endpoint) {
            return new StickyEndpointChannel(router, channelFactory.endpoint(endpoint));
        }

        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            return endpoint(endpoint).execute(request);
        }

        @Override
        public String toString() {
            return "Sticky{" + channelFactory + '}';
        }
    }

    @ThreadSafe
    private static final class StickyRouter {

        @Nullable
        private volatile Consumer<Request> stickyTarget;

        @Nullable
        @GuardedBy("this")
        private volatile ListenableFuture<Response> callInFlight;

        public ListenableFuture<Response> execute(Request request, EndpointChannel endpointChannel) {
            if (stickyTarget != null) {
                return executeWithStickyTarget(stickyTarget, request, endpointChannel);
            }

            synchronized (this) {
                if (stickyTarget != null) {
                    return executeWithStickyTarget(stickyTarget, request, endpointChannel);
                }

                ListenableFuture<Response> callInFlightSnapshot = callInFlight;
                if (callInFlightSnapshot == null) {
                    ListenableFuture<Response> executeWithStickyTokenResult =
                            executeWithStickyToken(request, endpointChannel);
                    // callInFlight must be updated prior to adding the callback, otherwise a quick completion
                    // may unset 'callInFlight' before it has been set in the first place!
                    SettableFuture<Response> result = SettableFuture.create();
                    callInFlight = result;
                    // The reason for this additional indirect future is that our internal state needs to be updated
                    // BEFORE listeners waiting on this future are allowed to be notified. If we do not do that,
                    // those listeners will StackOverflow.
                    DialogueFutures.addDirectCallback(executeWithStickyTokenResult, new FutureCallback<>() {
                        @Override
                        public void onSuccess(Response response) {
                            successfulCall(response);
                            if (!result.set(response)) {
                                response.close();
                            }
                        }

                        @Override
                        public void onFailure(Throwable throwable) {
                            failed();
                            result.setException(throwable);
                        }
                    });
                    // If the returned future is cancelled, this request should be as well.
                    DialogueFutures.addDirectListener(result, () -> {
                        if (result.isCancelled()) {
                            executeWithStickyTokenResult.cancel(false);
                        }
                    });
                    return result;
                } else {
                    // Each subsequent (parallel) call may be independently cancelled, that cancellation
                    // must not leak to other pending calls.
                    SettableFuture<Response> result = SettableFuture.create();
                    DialogueFutures.addDirectListener(callInFlightSnapshot, () -> {
                        if (!result.isDone()) {
                            ListenableFuture<Response> queuedRequestResponse = execute(request, endpointChannel);
                            DialogueFutures.addDirectCallback(queuedRequestResponse, new FutureCallback<>() {
                                @Override
                                public void onSuccess(Response response) {
                                    if (!result.set(response)) {
                                        response.close();
                                    }
                                }

                                @Override
                                public void onFailure(Throwable throwable) {
                                    result.setException(throwable);
                                }
                            });
                            // If the returned future is cancelled, this request should be as well.
                            DialogueFutures.addDirectListener(result, () -> {
                                if (result.isCancelled()) {
                                    queuedRequestResponse.cancel(false);
                                }
                            });
                        }
                    });
                    return result;
                }
            }
        }

        private synchronized void successfulCall(Response response) {
            callInFlight = null;
            if (stickyTarget == null) {
                stickyTarget = StickyAttachments.copyStickyTarget(response);
            }
        }

        private synchronized void failed() {
            callInFlight = null;
        }

        private static ListenableFuture<Response> executeWithStickyToken(
                Request request, EndpointChannel endpointChannel) {
            StickyAttachments.requestStickyToken(request);
            return endpointChannel.execute(request);
        }

        private static ListenableFuture<Response> executeWithStickyTarget(
                Consumer<Request> stickyTarget, Request request, EndpointChannel endpointChannel) {
            stickyTarget.accept(request);
            return endpointChannel.execute(request);
        }
    }

    private static final class StickyEndpointChannel implements EndpointChannel {
        private final StickyRouter stickyRouter;
        private final EndpointChannel delegate;

        StickyEndpointChannel(StickyRouter stickyRouter, EndpointChannel delegate) {
            this.stickyRouter = stickyRouter;
            this.delegate = delegate;
        }

        @Override
        public ListenableFuture<Response> execute(Request request) {
            return stickyRouter.execute(request, delegate);
        }

        @Override
        public String toString() {
            return "StickyEndpointChannel{delegate=" + delegate + '}';
        }
    }
}
