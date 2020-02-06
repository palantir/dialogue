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

import com.google.common.primitives.Ints;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelPool;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpContentDecompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import java.util.Objects;
import javax.net.ssl.SSLException;

// Needs to evict idle connections after ~55 seconds
// Needs some upper bound
// OkHttp uses a global pool shared between clients. That creates an issue where connections with different
// trust configurations can share connections, however this _is_ a behavior change. We should add the configuration
// which resulted in our SslContext to the key.
final class ConnectionPool {

    private static final long IDLE_TIMEOUT_MILLIS = 55_000;
    private final Routes routes;

    ConnectionPool(
            EventLoopGroup workerGroup,
            Class<? extends SocketChannel> socketChannelType,
            ClientConfiguration configuration) {
        this.routes = new Routes(workerGroup, socketChannelType, configuration);
    }

    // Provide our own abstraction around ChannelPool?
    ChannelPool route(String host, int port, boolean tls) {
        return routes.get(new Key(host, port, tls));
    }

    private static final class Routes extends AbstractChannelPoolMap<Key, ChannelPool> {

        private final EventLoopGroup workerGroup;
        private final Class<? extends SocketChannel> socketChannelType;
        private final ClientConfiguration configuration;
        private final SslContext context;

        Routes(
                EventLoopGroup workerGroup,
                Class<? extends SocketChannel> socketChannelType,
                ClientConfiguration configuration) {
            this.workerGroup = workerGroup;
            this.socketChannelType = socketChannelType;
            this.configuration = configuration;
            this.context = sslContext(configuration);
        }

        @Override
        protected SimpleChannelPool newPool(Key key) {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup);
            bootstrap.channel(socketChannelType);
            bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
            bootstrap.option(
                    ChannelOption.CONNECT_TIMEOUT_MILLIS,
                    Ints.checkedCast(configuration.connectTimeout().toMillis()));
            long socketTimeoutMillis = Math.max(
                    configuration.readTimeout().toMillis(),
                    configuration.writeTimeout().toMillis());
            bootstrap.option(
                    ChannelOption.SO_TIMEOUT, Ints.checkedCast(Math.max(IDLE_TIMEOUT_MILLIS, socketTimeoutMillis)));
            // Contents are fully buffered. Sorry, Nagle.
            // Worth investigating using TCP_CORK in combination with NODELAY
            // on kernels > 2.5.71, otherwise CORK takes precedence.
            bootstrap.option(ChannelOption.TCP_NODELAY, true);
            return new SimpleChannelPool(bootstrap, new Handler(key, context)) {
                @Override
                protected ChannelFuture connectChannel(Bootstrap bstrap) {
                    return bstrap.connect(key.host, key.port).addListener((ChannelFutureListener) future -> {
                        if (future.isSuccess()) {
                            Channel channel = future.channel();
                            channel.attr(Attributes.POOL_KEY).set(this);
                        }
                    });
                }
            };
        }

        private static SslContext sslContext(ClientConfiguration configuration) {
            try {
                return SslContextBuilder.forClient()
                        .sslProvider(OpenSsl.isAvailable() ? SslProvider.OPENSSL_REFCNT : SslProvider.JDK)
                        .trustManager(configuration.trustManager())
                        .build();
            } catch (SSLException e) {
                throw new SafeRuntimeException("Failed to build netty SslContext", e);
            }
        }
    }

    private static final class Handler implements ChannelPoolHandler {
        private final Key key;
        private final SslContext context;

        Handler(Key key, SslContext context) {
            this.key = key;
            this.context = context;
        }

        @Override
        public void channelReleased(Channel _ch) {}

        @Override
        public void channelAcquired(Channel _ch) {}

        @Override
        public void channelCreated(Channel channel) {
            ChannelPipeline pipeline = channel.pipeline();
            if (key.tls) {
                pipeline.addLast(context.newHandler(channel.alloc()));
            }
            pipeline.addLast(new HttpClientCodec());
            pipeline.addLast(new HttpContentDecompressor());
            pipeline.addLast(new HttpObjectAggregator(1024 * 1024 * 1024 /* 1g */));
            pipeline.addLast(new ResponseHandler());
        }
    }

    private static final class Key {
        private final String host;
        private final int port;
        private final boolean tls;

        Key(String host, int port, boolean tls) {
            this.host = host;
            this.port = port;
            this.tls = tls;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            Key key = (Key) other;
            return port == key.port && tls == key.tls && host.equals(key.host);
        }

        @Override
        public int hashCode() {
            return Objects.hash(host, port, tls);
        }

        @Override
        public String toString() {
            return "Key{" + "host='" + host + '\'' + ", port=" + port + ", tls=" + tls + '}';
        }
    }
}
