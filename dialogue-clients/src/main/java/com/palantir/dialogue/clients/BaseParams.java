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

package com.palantir.dialogue.clients;

import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.dialogue.ConjureRuntime;
import com.palantir.dialogue.core.DialogueChannel;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import org.immutables.value.Value;

/** Parameters necessary for {@link DialogueChannel#builder()} and constructing an actual BlockingFoo instance. */
interface BaseParams extends AugmentClientConfig {

    @Value.Default
    default ConjureRuntime runtime() {
        return DefaultConjureRuntime.builder().build();
    }

    /** Exponential backoffs are scheduled on this executor. If this is omitted a singleton will be used. */
    Optional<ScheduledExecutorService> retryExecutor();

    /**
     * The Apache http client uses blocking socket operations, so threads from this executor will be used to wait for
     * responses. It's strongly recommended that custom executors support tracing-java. Cached executors are the best
     * fit because we use concurrency limiters to bound concurrent requests.
     */
    Optional<ExecutorService> blockingExecutor();
}
