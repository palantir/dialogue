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
import com.palantir.dialogue.Request;
import com.palantir.dialogue.Response;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

final class ChannelToEndpointChannel implements Channel {

    private final Function<Endpoint, Channel> adapter;
    private final Map<Object, Channel> cache;

    ChannelToEndpointChannel(Function<Endpoint, Channel> adapter) {
        this.adapter = adapter;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public ListenableFuture<Response> execute(Endpoint endpoint, Request request) {
        return channelFor(endpoint).execute(endpoint, request);
    }

    private Channel channelFor(Endpoint endpoint) {
        return cache.computeIfAbsent(key(endpoint), _key -> adapter.apply(endpoint));
    }

    private static boolean isConstant(Endpoint endpoint) {
        return endpoint instanceof Enum;
    }

    private static Object key(Endpoint endpoint) {
        return isConstant(endpoint) ? endpoint : stringKey(endpoint);
    }

    private static String stringKey(Endpoint endpoint) {
        return endpoint.serviceName() + '.' + endpoint.endpointName();
    }
}
