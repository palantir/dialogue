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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.StickyAttachments.StickyTarget;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import org.immutables.value.Value;

@Value.Enclosing
final class StickyEndpointChannels2 implements Supplier<Channel> {

    private final Supplier<EndpointChannelFactory> delegate;

    private StickyEndpointChannels2(Supplier<EndpointChannelFactory> endpointChannelFactory) {
        this.delegate = Preconditions.checkNotNull(endpointChannelFactory, "endpointChannelFactory");
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
        return new StickyEndpointChannels2(
                new StickyEndpointChannels2EndpointFactorySupplier(cf, nodeSelectionChannel, delegate));
    }

    private static final class StickyEndpointChannels2EndpointFactorySupplier
            implements Supplier<EndpointChannelFactory> {

        private final Config cf;
        private final LimitedChannel nodeSelectionChannel;
        private final EndpointChannelFactory delegate;

        StickyEndpointChannels2EndpointFactorySupplier(
                Config cf, LimitedChannel nodeSelectionChannel, EndpointChannelFactory delegate) {
            this.cf = cf;
            this.nodeSelectionChannel = nodeSelectionChannel;
            this.delegate = delegate;
        }

        @Override
        public EndpointChannelFactory get() {
            LimitedChannel stickyLimitedChannel =
                    StickyConcurrencyLimitedChannel.create(nodeSelectionChannel, cf.channelName());
            Channel queueOverride = QueuedChannel.createForSticky(cf, stickyLimitedChannel);
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

        /**
         * .
         * @deprecated prefer {@link #endpoint}, as it allows binding work upfront
         */
        @Deprecated
        @Override
        public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
            return endpoint(endpoint).execute(request);
        }

        @Override
        public String toString() {
            return "Sticky{" + channelFactory + '}';
        }
    }

    private static final class StickyRouter {

        private final InFlightCallSuccessTransformer successTransformer = new InFlightCallSuccessTransformer();
        private final InFlightCallFailureTransformer failureTransformer = new InFlightCallFailureTransformer();

        @Nullable
        private volatile StickyTarget stickyTarget;

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
                    // Cannot use DialogueFutures#addDirectCallback because we want ordering of listeners:
                    // we want the success/failure callbacks to run BEFORE the queued requests inspect the result of
                    // the first call.
                    ListenableFuture<Response> result = DialogueFutures.transform(
                            executeWithStickyToken(request, endpointChannel), successTransformer);
                    result = DialogueFutures.catchingAllAsync(result, failureTransformer);
                    callInFlight = Futures.nonCancellationPropagating(result);
                    return result;
                } else {
                    ListenableFuture<Response> result = callInFlightSnapshot;
                    result = DialogueFutures.transformAsync(result, _input -> execute(request, endpointChannel));
                    result = DialogueFutures.catchingAllAsync(result, _throwable -> execute(request, endpointChannel));
                    return result;
                }
            }
        }

        private final class InFlightCallSuccessTransformer implements Function<Response, Response> {
            @Override
            public Response apply(Response response) {
                successfulCall(response);
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
                StickyTarget newStickyTarget =
                        response.attachments().getOrDefault(StickyAttachments.STICKY_TOKEN, null);
                if (newStickyTarget != null) {
                    stickyTarget = newStickyTarget;
                }
            }
        }

        private synchronized void failed() {
            callInFlight = null;
        }

        private static ListenableFuture<Response> executeWithStickyToken(
                Request request, EndpointChannel endpointChannel) {
            request.attachments().put(StickyAttachments.REQUEST_STICKY_TOKEN, Boolean.TRUE);
            return endpointChannel.execute(request);
        }

        private static ListenableFuture<Response> executeWithStickyTarget(
                StickyTarget stickyTarget, Request request, EndpointChannel endpointChannel) {
            request.attachments().put(StickyAttachments.STICKY, stickyTarget);
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