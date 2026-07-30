/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.util.concurrent.FutureCallback;
import com.palantir.dialogue.Response;
import com.palantir.dialogue.core.LimitedChannel.LimitEnforcement;
import org.jspecify.annotations.Nullable;

interface ConcurrencyLimiter {

    /**
     * Returns a new request permit if there is capacity under the current {@link #getLimit() limit}, otherwise
     * {@code null}. The caller must eventually release the permit by completing the corresponding response future
     */
    @Nullable
    Permit acquire(LimitEnforcement limitEnforcement);

    /** The current concurrency limit (maximum number of concurrent in-flight permits). */
    double getLimit();

    /** The current number of in-flight permits. */
    int getInflight();

    void setChannelNameForLogging(String value);

    interface Permit extends FutureCallback<Response> {}
}
