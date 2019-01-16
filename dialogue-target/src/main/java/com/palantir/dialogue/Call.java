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

import com.palantir.dialogue.api.Observer;

/**
 * Represents an active RPC call between a client and a server. Call instances are transient objects in the sense that
 * they are created by a {@link Channel} when a client initiates a new call to an {@link Endpoint}, and disposed when
 * the call terminates (successfully, with failure, or due to explicit {@link #cancel cancellation}).
 */
public interface Call<RespT> {

    /**
     * Executes this call and presents the success or failure result to the given observer. A call may only be executed
     * once, repeated calls yield a {@link IllegalStateException}.
     */
    void execute(Observer<RespT> observer);

    /** Cancels this call. Call implementations should support multiple, idempotent cancellation. */
    void cancel();
}
