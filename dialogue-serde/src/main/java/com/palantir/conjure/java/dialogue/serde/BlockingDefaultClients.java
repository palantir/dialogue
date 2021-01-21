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

package com.palantir.conjure.java.dialogue.serde;

import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.java.dialogue.serde.DefaultClients.EndpointChannelAdapter;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Clients;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.EndpointChannel;
import com.palantir.dialogue.Request;

enum BlockingDefaultClients implements Clients {
    INSTANCE;

    @Override
    public <T> ListenableFuture<T> call(EndpointChannel channel, Request request, Deserializer<T> deserializer) {
        // request.executeInCallingThread();
        ListenableFuture<T> toReturn = DefaultClients.INSTANCE.call(channel, request, deserializer);
        // request.getCallingThreadExecutor()
        //         .ifPresent(callingThreadExecutor -> callingThreadExecutor.executeQueue(toReturn));
        return toReturn;
    }

    @Override
    public <T> ListenableFuture<T> call(
            Channel channel, Endpoint endpoint, Request request, Deserializer<T> deserializer) {
        return call(new EndpointChannelAdapter(endpoint, channel), request, deserializer);
    }

    @Override
    public <T> T block(ListenableFuture<T> future) {
        return DefaultClients.INSTANCE.block(future);
    }
}
