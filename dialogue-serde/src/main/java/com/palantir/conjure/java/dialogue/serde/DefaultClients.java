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

package com.palantir.conjure.java.dialogue.serde;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.Clients;
import com.palantir.dialogue.Deserializer;
import com.palantir.dialogue.Endpoint;
import com.palantir.dialogue.RemoteExceptions;
import com.palantir.dialogue.Request;

enum DefaultClients implements Clients {
    INSTANCE;

    @Override
    public <T> ListenableFuture<T> call(
            Channel channel, Endpoint endpoint, Request request, Deserializer<T> deserializer) {
        return Futures.transform(
                channel.execute(endpoint, request), deserializer::deserialize, MoreExecutors.directExecutor());
    }

    @Override
    public <T> T blocking(Channel channel, Endpoint endpoint, Request request, Deserializer<T> deserializer) {
        ListenableFuture<T> call = call(channel, endpoint, request, deserializer);
        return RemoteExceptions.getUnchecked(call);
    }
}
