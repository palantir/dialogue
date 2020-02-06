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

import com.google.common.util.concurrent.SettableFuture;
import com.palantir.dialogue.Response;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.AttributeKey;

final class Attributes {

    static final AttributeKey<SettableFuture<Response>> CALLBACK_KEY =
            AttributeKey.valueOf(NettyChannel.class, "CALLBACK_KEY");
    static final AttributeKey<ChannelPool> POOL_KEY = AttributeKey.valueOf(ConnectionPool.class, "POOL");

    private Attributes() {}
}
