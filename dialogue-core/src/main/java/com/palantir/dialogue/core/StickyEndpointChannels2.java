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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.RoutingAttachments;
import com.palantir.dialogue.RoutingAttachments.HostId;
import com.palantir.dialogue.RoutingAttachments.RoutingKey;
import com.palantir.dialogue.futures.DialogueFutures;
import com.palantir.logsafe.Preconditions;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Value.Enclosing
public final class StickyEndpointChannels2 implements Supplier<Supplier<Channel>> {

    private static final Logger log = LoggerFactory.getLogger(StickyEndpointChannels2.class);

    private final EndpointChannelFactory delegate;

    private StickyEndpointChannels2(EndpointChannelFactory endpointChannelFactory) {
        this.delegate = Preconditions.checkNotNull(endpointChannelFactory, "endpointChannelFactory");
    }

    @Override
    public Supplier<Channel> get() {
        return new StickySessionSupplier(delegate);
    }

    @Override
    public String toString() {
        return "StickyEndpointChannels2{}";
    }

    private static final class StickySessionSupplier implements Supplier<Channel> {

        private final EndpointChannelFactory channelFactory;
        private final StickyRouter router = new DefaultStickyRouter();

        private StickySessionSupplier(EndpointChannelFactory channelFactory) {
            this.channelFactory = channelFactory;
        }

        @Override
        public Channel get() {
            return new Sticky(channelFactory, router);
        }
    }

    private static final class Sticky implements EndpointChannelFactory, Channel {

        private final EndpointChannelFactory channelFactory;
        private final StickyRouter router;
        private final RoutingKey routingKey;

        private Sticky(EndpointChannelFactory channelFactory, StickyRouter router) {
            this.router = router;
            this.channelFactory = channelFactory;
            this.routingKey = RoutingKey.create();
        }

        @Override
        public EndpointChannel endpoint(Endpoint endpoint) {
            return new StickyEndpointChannel(router, channelFactory.endpoint(endpoint), routingKey);
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

    public static StickyEndpointChannels2 create(EndpointChannelFactory endpointChannelFactory) {
        return new StickyEndpointChannels2(endpointChannelFactory);
    }

    interface StickyRouter {
        ListenableFuture<Response> execute(Request request, EndpointChannel endpointChannel);
    }

    // This single-in-flight-call has been implemented a few times, this impl is likely a bit buggy,
    // but shows the approach here.
    // Initial implementation just blocked all calls until we have hostId, but that means simulations were not
    // working.
    private static final class DefaultStickyRouter implements StickyRouter {

        @Nullable
        private volatile HostId hostId;

        private boolean callInFlight;

        private final Queue<DeferredCall> deferredCalls = new ArrayDeque<>();

        @Override
        public ListenableFuture<Response> execute(Request request, EndpointChannel endpointChannel) {
            if (hostId != null) {
                return executeWithHostId(hostId, request, endpointChannel);
            }

            // TODO(12345): Fixup to make sure this doesn't fail if it's reentrant: technically futures can complete
            // whilst we're blocking on delegate execute.
            synchronized (this) {
                if (hostId != null) {
                    request.attachments().put(RoutingAttachments.EXECUTE_ON_HOST_ID_KEY, hostId);
                    return endpointChannel.execute(request);
                }

                DeferredCall call = ImmutableStickyEndpointChannels2.DeferredCall.builder()
                        .endpointChannel(endpointChannel)
                        .request(request)
                        .build();
                deferredCalls.add(call);

                trySchedule();

                return call.responseFuture();
            }
        }

        private ListenableFuture<Response> executeWithAttachHostId(Request request, EndpointChannel endpointChannel) {
            request.attachments().put(RoutingAttachments.ATTACH_HOST_ID, Boolean.TRUE);
            return endpointChannel.execute(request);
        }

        private static ListenableFuture<Response> executeWithHostId(
                HostId hostId, Request request, EndpointChannel endpointChannel) {
            request.attachments().put(RoutingAttachments.EXECUTE_ON_HOST_ID_KEY, hostId);
            return endpointChannel.execute(request);
        }

        private synchronized void trySchedule() {
            if (hostId != null) {
                // Drain the queue
                StickyEndpointChannels2.DeferredCall queueHead;
                while ((queueHead = deferredCalls.poll()) != null) {
                    executeWithHostAndForward(hostId, queueHead);
                }
                return;
            }

            while (!callInFlight) {
                StickyEndpointChannels2.DeferredCall queueHead = deferredCalls.poll();
                if (queueHead == null) {
                    return;
                }
                // If the future has been completed (most likely via cancel) the call should not be queued.
                // There's a race where cancel may be invoked between this check and execution, but the scheduled
                // request will be quickly cancelled in that case.
                SettableFuture<Response> queuedResponse = queueHead.responseFuture();
                if (queuedResponse.isDone()) {
                    return;
                }

                callInFlight = true;
                DialogueFutures.addDirectCallback(
                        executeWithAttachHostId(queueHead.request(), queueHead.endpointChannel()),
                        new ForwardAndSchedule(queueHead.responseFuture()));
            }
        }

        private void executeWithHostAndForward(HostId curHostId, StickyEndpointChannels2.DeferredCall deferredCall) {
            DialogueFutures.addDirectCallback(
                    executeWithHostId(curHostId, deferredCall.request(), deferredCall.endpointChannel()),
                    new ForwardAndSchedule(deferredCall.responseFuture()));
        }

        private synchronized void updateHostId(Response response) {
            if (hostId == null) {
                HostId successfulHostId = response.attachments()
                        .getOrDefault(RoutingAttachments.EXECUTED_ON_HOST_ID_RESPONSE_ATTACHMENT_KEY, null);
                if (successfulHostId != null) {
                    hostId = successfulHostId;
                }
            }
        }

        /**
         * Forward the success or failure of the call to the SettableFuture that was previously returned to the caller.
         * This also schedules the next set of requests to be run.
         */
        private class ForwardAndSchedule implements FutureCallback<Response> {
            private final SettableFuture<Response> response;

            ForwardAndSchedule(SettableFuture<Response> response) {
                this.response = response;
            }

            @Override
            public synchronized void onSuccess(Response result) {
                if (!response.set(result)) {
                    result.close();
                }
                callInFlight = false;
                updateHostId(result);
                trySchedule();
            }

            @Override
            public synchronized void onFailure(Throwable throwable) {
                if (!response.setException(throwable)) {
                    if (throwable instanceof CancellationException) {
                        log.debug("Call was canceled", throwable);
                    } else {
                        log.info("Call failed after the future completed", throwable);
                    }
                }
                callInFlight = false;
                trySchedule();
            }
        }
    }

    @Value.Immutable
    interface DeferredCall {
        Request request();

        @Value.Derived
        default SettableFuture<Response> responseFuture() {
            return SettableFuture.create();
        }

        EndpointChannel endpointChannel();
    }

    private static final class StickyEndpointChannel implements EndpointChannel {
        private final StickyRouter stickyRouter;
        private final EndpointChannel delegate;
        private final RoutingKey routingKey;

        StickyEndpointChannel(StickyRouter stickyRouter, EndpointChannel delegate, RoutingKey routingKey) {
            this.stickyRouter = stickyRouter;
            this.delegate = delegate;
            this.routingKey = routingKey;
        }

        @Override
        public ListenableFuture<Response> execute(Request request) {
            request.attachments().put(RoutingAttachments.ROUTING_KEY, routingKey);
            return stickyRouter.execute(request, delegate);
        }

        @Override
        public String toString() {
            return "ScoreTrackingEndpointChannel{" + "delegate=" + delegate + '}';
        }
    }
}
