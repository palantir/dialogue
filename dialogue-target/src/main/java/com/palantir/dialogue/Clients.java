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

package com.palantir.dialogue;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Future;

/**
 * Provides functionality for generated code to make both blocking and asynchronous calls without
 * duplicating logic.
 */
public interface Clients {

    /**
     * Makes a request to the specified {@link Endpoint} and deserializes the response using a provided deserializer.
     */
    <T> ListenableFuture<T> call(Channel channel, Endpoint endpoint, Request request, Deserializer<T> deserializer);

    <T> ListenableFuture<T> call(EndpointChannel channel, Request request, Deserializer<T> deserializer);

    EndpointChannel bindEndpoint(Channel channel, Endpoint endpoint);

    /**
     * Similar to {@link com.google.common.util.concurrent.Futures#getUnchecked(Future)}, except with custom handling
     * for conjure exceptions and cancellation on interruption.
     */
    <T> T block(ListenableFuture<T> future);
}
