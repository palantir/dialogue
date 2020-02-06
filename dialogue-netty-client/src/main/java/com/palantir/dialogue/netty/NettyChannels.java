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

import com.google.common.base.Suppliers;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.core.ChannelFactory;
import com.palantir.dialogue.core.DialogueChannel;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.pool.ChannelPool;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.function.Supplier;

public final class NettyChannels {

    private static final Supplier<EventLoopGroup> eventGroup = Suppliers.memoize(() -> NettyTransport.selected()
            .newEventLoopGroup(
                    0,
                    new ThreadFactoryBuilder()
                            .setDaemon(true)
                            .setNameFormat("dialogue-netty-%d")
                            .build()));

    private NettyChannels() {}

    public static Channel create(ClientConfiguration conf) {
        return DialogueChannel.builder()
                .channelFactory(createFactory(conf))
                .clientConfiguration(conf)
                .channelName("dialogue-netty-channel")
                .build();
    }

    public static ChannelFactory createFactory(ClientConfiguration conf) {
        ConnectionPool pool =
                new ConnectionPool(eventGroup.get(), NettyTransport.selected().socketType(), conf);
        return uri -> {
            URI target = URI.create(uri);
            ChannelPool channelPool =
                    pool.route(target.getHost(), target.getPort(), !"http".equalsIgnoreCase(target.getScheme()));
            try {
                return new NettyChannel(channelPool, target.toURL());
            } catch (MalformedURLException e) {
                throw new SafeIllegalArgumentException("Failed to convert URI to URL", e);
            }
        };
    }
}
