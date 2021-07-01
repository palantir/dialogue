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

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.EndpointChannelFactory;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.RoutingAttachments;
import com.palantir.dialogue.RoutingAttachments.HostId;
import com.palantir.dialogue.RoutingAttachments.RoutingKey;
import com.palantir.logsafe.Preconditions;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

public final class StickyEndpointChannels2 implements Supplier<Channel> {

    private final EndpointChannelFactory delegate;

    private StickyEndpointChannels2(EndpointChannelFactory endpointChannelFactory) {
        this.delegate = Preconditions.checkNotNull(endpointChannelFactory, "endpointChannelFactory");
    }

    @Override
    public Channel get() {
        return new Sticky(delegate);
    }

    @Override
    public String toString() {
        return "StickyEndpointChannels2{}";
    }

    @ThreadSafe
    private static final class Sticky implements EndpointChannelFactory, Channel {

        private final EndpointChannelFactory channelFactory;
        private final StickyRouter router = new DefaultStickyRouter();

        private Sticky(EndpointChannelFactory channelFactory) {
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

    public static Supplier<Channel> create(EndpointChannelFactory endpointChannelFactory) {
        return new StickyEndpointChannels2(endpointChannelFactory);
    }

    interface StickyRouter {
        ListenableFuture<Response> execute(Request request, EndpointChannel endpointChannel);
    }

    private static final class DefaultStickyRouter implements StickyRouter {

        @Nullable
        private volatile RoutingKey routingKey;

        @Override
        public ListenableFuture<Response> execute(Request request, EndpointChannel endpointChannel) {
            if (routingKey != null) {
                request.attachments().put(RoutingAttachments.ROUTING_KEY, routingKey);
                return endpointChannel.execute(request);
            }

            synchronized (this) {
                if (routingKey != null) {
                    request.attachments().put(RoutingAttachments.ROUTING_KEY, routingKey);
                    return endpointChannel.execute(request);
                }

                // Not great, but valid implementation: block until one of the requests is successful to figure out
                // the hostId; can be made non-blocking here, but it's a more difficult impl.
                request.attachments().put(RoutingAttachments.ATTACH_HOST_ID, Boolean.TRUE);
                ListenableFuture<Response> future = endpointChannel.execute(request);
                try {
                    Response response = future.get();
                    HostId successfulHostId =
                            response.attachments().getOrDefault(RoutingAttachments.EXECUTED_ON_HOST_KEY, null);
                    Preconditions.checkNotNull(successfulHostId, "successfulHostId");
                    routingKey = RoutingKey.create(Optional.ofNullable(successfulHostId));
                    return future;
                } catch (ExecutionException | InterruptedException | RuntimeException e) {
                    return future;
                }
            }
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
            return "ScoreTrackingEndpointChannel{" + "delegate=" + delegate + '}';
        }
    }
}
