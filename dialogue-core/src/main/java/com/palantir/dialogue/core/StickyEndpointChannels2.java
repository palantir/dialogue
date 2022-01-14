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

import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import org.immutables.value.Value;

@Value.Enclosing
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

        private final Config cf;
        private final LimitedChannel nodeSelectionChannel;

        private QueueOverrideSupplier(Config cf, LimitedChannel nodeSelectionChannel) {
            this.cf = cf;
            this.nodeSelectionChannel = nodeSelectionChannel;
        }

        @Override
        public Channel get() {
            LimitedChannel stickyLimitedChannel =
                    StickyConcurrencyLimitedChannel.create(nodeSelectionChannel, cf.channelName());
            return QueuedChannel.createForSticky(cf, stickyLimitedChannel);
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

        private final InFlightCallSuccessTransformer successTransformer = new InFlightCallSuccessTransformer();
        private final InFlightCallFailureTransformer failureTransformer = new InFlightCallFailureTransformer();
        private final AsyncFunction<Throwable, Response> cancellationTransformer = _input -> {
            failed();
            return Futures.immediateCancelledFuture();
        };

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
                ListenableFuture<Response> result;
                if (callInFlightSnapshot == null) {
                    // Cannot use DialogueFutures#addDirectCallback because we want ordering of listeners:
                    // we want the success/failure callbacks to run BEFORE the queued requests inspect the result of
                    // the first call.
                    result = DialogueFutures.transform(
                            executeWithStickyToken(request, endpointChannel), successTransformer);
                    result = DialogueFutures.catchingAllAsync(result, failureTransformer, cancellationTransformer);
                    callInFlight = result;
                } else {
                    // Each subsequent (parallel) call may be independently cancelled, that cancellation
                    // must not leak to other pending calls.
                    SettableFuture<Response> response = SettableFuture.create();
                    DialogueFutures.addDirectListener(callInFlightSnapshot, () -> {
                        ListenableFuture<Response> queuedRequestResponse = execute(request, endpointChannel);
                        DialogueFutures.addDirectCallback(queuedRequestResponse, new FutureCallback<>() {
                            @Override
                            public void onSuccess(Response result) {
                                if (!response.set(result)) {
                                    result.close();
                                }
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                response.setException(throwable);
                            }
                        });
                        // If the returned future is cancelled, this request should be as well.
                        DialogueFutures.addDirectListener(response, () -> {
                            if (queuedRequestResponse.isCancelled()) {
                                queuedRequestResponse.cancel(false);
                            }
                        });
                    });
                    return response;
                }
                return result;
            }
        }

        private final class InFlightCallSuccessTransformer implements Function<Response, Response> {
            @Override
            public Response apply(Response response) {
                try {
                    successfulCall(response);
                } catch (Throwable t) {
                    response.close();
                    throw new SafeIllegalStateException("Failed to update state with successful call", t);
                }
                return response;
            }
        }

        private final class InFlightCallFailureTransformer implements AsyncFunction<Throwable, Response> {
            @Override
            public ListenableFuture<Response> apply(Throwable input) throws Exception {
                failed();
                return Futures.immediateFailedFuture(input);
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
