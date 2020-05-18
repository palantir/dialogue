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

/**
 * {@code EndpointChannel} is an abstraction of a transport layer (e.g., HTTP) that is used client stubs.
 *
 * This is a version of {@link Channel} that always sends the given request to a predefined {@link Endpoint}.
 * Constructed using {@link EndpointChannelFactory#endpoint}.
 */
public interface EndpointChannel {
    ListenableFuture<Response> execute(Request request);
}
