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

/**
 * A 'staged' version of {@link Channel}, that allows implementors to precompute necessary objects (e.g. counter
 * lookups) based on the provided {@code endpoint}, saving CPU cycles on each call.
 */
public interface EndpointChannelFactory {

    /**
     * Construct a new {@link EndpointChannel} which will send all requests to the given {@link Endpoint}. This
     * method is expected to be called once at startup time. Behaviour is undefined if called with the same endpoint
     * many times.
     */
    EndpointChannel endpoint(Endpoint endpoint);
}
