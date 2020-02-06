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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Response;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpUtil;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
    private static final Logger log = LoggerFactory.getLogger(ResponseHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
        try {
            int status = msg.status().code();
            ListMultimap<String, String> headers = responseHeaders(msg.headers());
            ByteBuf content = msg.content().retain();
            SettableFuture<Response> callback =
                    ctx.channel().attr(Attributes.CALLBACK_KEY).getAndSet(null);
            if (callback == null
                    || !callback.set(new Response() {

                        private final InputStream body = new ByteBufInputStream(content, true /* release on close*/);

                        @Override
                        public InputStream body() {
                            return body;
                        }

                        @Override
                        public int code() {
                            return status;
                        }

                        @Override
                        public ListMultimap<String, String> headers() {
                            return headers;
                        }

                        // TODO(ckozak): Efficient getFirstHeader implementation and lazily build the header map

                        @Override
                        public void close() {
                            try {
                                body.close();
                            } catch (IOException | RuntimeException e) {
                                log.warn("Failed to close body", e);
                            }
                        }
                    })) {
                log.info("Channel has already completed, resources will be freed");
                content.release();
            }
        } catch (Throwable t) {
            exceptionCaught(ctx, t);
        } finally {
            Channel channel = ctx.channel();
            ChannelPool pool = channel.attr(Attributes.POOL_KEY).get();
            if (!HttpUtil.isKeepAlive(msg)) {
                ctx.close().addListener(future -> pool.release(channel).addListener(CloseListener.INSTANCE));
            } else {
                pool.release(ctx.channel()).addListener(CloseListener.INSTANCE);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Channel channel = ctx.channel();
        ChannelPool pool = channel.attr(Attributes.POOL_KEY).get();
        ctx.close().addListener(future -> pool.release(channel).addListener(CloseListener.INSTANCE));
        SettableFuture<Response> future = channel.attr(Attributes.CALLBACK_KEY).getAndSet(null);
        if (future != null) {
            future.setException(cause);
        }
    }

    // neither pretty nor efficient.
    private static ListMultimap<String, String> responseHeaders(HttpHeaders headers) {
        ListMultimap<String, String> results = MultimapBuilder.treeKeys(String.CASE_INSENSITIVE_ORDER)
                .arrayListValues()
                .build();
        headers.names().forEach(headerName -> results.putAll(headerName, headers.getAll(headerName)));
        return results;
    }
}
