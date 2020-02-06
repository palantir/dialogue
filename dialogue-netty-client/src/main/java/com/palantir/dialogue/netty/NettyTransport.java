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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.util.concurrent.ThreadFactory;

/**
 * Gets the netty transport implementation for the current environment.
 * The {@link EventLoopGroup} and {@link SocketChannel} types must match.
 */
enum NettyTransport {
    NIO(NioEventLoopGroup::new, NioSocketChannel.class),
    EPOLL(EpollEventLoopGroup::new, EpollSocketChannel.class);

    static NettyTransport selected() {
        return Epoll.isAvailable() ? EPOLL : NIO;
    }

    @SuppressWarnings("ImmutableEnumChecker")
    private final EventLoopGroupFactory eventLoopGroupFactory;

    private final Class<? extends SocketChannel> socketType;

    NettyTransport(EventLoopGroupFactory eventLoopGroupFactory, Class<? extends SocketChannel> socketType) {
        this.eventLoopGroupFactory = eventLoopGroupFactory;
        this.socketType = socketType;
    }

    public EventLoopGroup newEventLoopGroup(int threads, ThreadFactory threadFactory) {
        return eventLoopGroupFactory.create(threads, threadFactory);
    }

    public Class<? extends SocketChannel> socketType() {
        return socketType;
    }

    interface EventLoopGroupFactory {

        EventLoopGroup create(int threads, ThreadFactory threadFactory);
    }
}
