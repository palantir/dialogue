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

package com.palantir.dialogue.netty;

import com.google.common.base.Strings;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.Request;
import com.palantir.dialogue.RequestBody;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.BaseUrl;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.UnsafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

final class NettyChannel implements com.palantir.dialogue.Channel {

    private final ChannelPool channelPool;
    private final ByteBufAllocator allocator = PooledByteBufAllocator.DEFAULT;
    private final BaseUrl baseUrl;

    NettyChannel(ChannelPool channelPool, URL baseUrl) {
        this.channelPool = channelPool;
        this.baseUrl = BaseUrl.of(baseUrl);
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        if (request.body().isPresent() && endpoint.httpMethod() == com.palantir.dialogue.HttpMethod.GET) {
            return Futures.immediateFailedFuture(
                    new SafeIllegalArgumentException("GET endpoints must not have a request body"));
        }
        SettableFuture<Response> result = SettableFuture.create();
        Future<Channel> connectFuture = channelPool.acquire().addListener((GenericFutureListener<Future<Channel>>)
                future -> {
                    try {
                        if (future.isSuccess()) {
                            Channel channel = future.getNow();
                            channel.attr(Attributes.CALLBACK_KEY).set(result);
                            // Do we need to register a listener here? Are we guaranteed to get a results?
                            ChannelFuture channelFuture = channel.writeAndFlush(toRequest(endpoint, request));
                            channelFuture.addListener(future1 -> {
                                if (!future1.isSuccess()) {
                                    result.setException(future1.cause());
                                }
                            });
                            result.addListener(
                                    () -> {
                                        if (result.isCancelled()) {
                                            channelFuture.cancel(true);
                                        }
                                    },
                                    MoreExecutors.directExecutor());
                        } else {
                            result.setException(future.cause());
                        }
                    } catch (Throwable t) {
                        result.setException(t);
                    }
                });
        result.addListener(
                () -> {
                    if (result.isCancelled()) {
                        connectFuture.cancel(true);
                    }
                },
                MoreExecutors.directExecutor());
        return result;
    }

    private FullHttpRequest toRequest(Endpoint endpoint, Request request) {
        URL completeUrl = baseUrl.render(endpoint, request);
        URI target;
        try {
            target = completeUrl.toURI();
        } catch (URISyntaxException e) {
            throw new SafeRuntimeException("Failed to create URI", e, UnsafeArg.of("url", completeUrl));
        }

        String path = Strings.nullToEmpty(target.getRawPath());
        String query = Strings.nullToEmpty(target.getRawQuery());
        String rendered = query.isEmpty() ? path : path + '?' + query;
        DefaultFullHttpRequest nettyRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                nettyMethod(endpoint),
                rendered,
                request.body().map(this::nettyBody).orElse(Unpooled.EMPTY_BUFFER));

        // Fill headers
        HttpHeaders headers = nettyRequest.headers();
        request.headerParams().forEach(headers::add);
        if (request.body().isPresent()) {
            headers.set(HttpHeaderNames.CONTENT_TYPE, request.body().get().contentType());
            headers.set(HttpHeaderNames.CONTENT_LENGTH, nettyRequest.content().readableBytes());
        }
        headers.set(HttpHeaderNames.HOST, target.getHost());
        return nettyRequest;
    }

    private static HttpMethod nettyMethod(Endpoint endpoint) {
        // Fill request body and set HTTP method
        switch (endpoint.httpMethod()) {
            case GET:
                return HttpMethod.GET;
            case PATCH:
                return HttpMethod.PATCH;
            case POST:
                return HttpMethod.POST;
            case PUT:
                return HttpMethod.PUT;
            case DELETE:
                return HttpMethod.DELETE;
        }
        throw new SafeIllegalStateException("Unknown method", SafeArg.of("method", endpoint.httpMethod()));
    }

    private ByteBuf nettyBody(RequestBody body) {
        ByteBuf buffer = allocator.directBuffer();
        try {
            body.writeTo(new ByteBufOutputStream(buffer));
        } catch (IOException e) {
            throw new SafeRuntimeException("Failed to buffer content", e);
        }
        return buffer;
    }
}
