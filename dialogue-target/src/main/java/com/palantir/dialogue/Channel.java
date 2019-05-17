/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

/**
 * A channel is an abstraction of a transport layer (e.g., HTTP) that is consumed by server and client stubs.
 *
 * <h4>Threading Model</h4>
 * Implementations of {@link Channel#execute(Endpoint, Request)} must return immediately, and must not perform
 * blocking operations. Channel implementations using blocking constructs must internally leverage an executor
 * to expose only a non-blocking API.
 *
 * <h4>Behavior</h4>
 * Implementations of {@link Channel#execute(Endpoint, Request)} must never throw. A failed {@link ListenableFuture}
 * must be returned instead.
 */
public interface Channel {
    ListenableFuture<Response> execute(Endpoint endpoint, Request request);
}
