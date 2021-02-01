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

import com.google.common.util.concurrent.AtomicDouble;
import com.palantir.logsafe.Preconditions;
import com.palantir.random.SafeThreadLocalRandom;

/**
 * Package private API for enabling
 * {@link com.palantir.dialogue.blocking.CallingThreadExecutor} optimization.
 */
final class UseCallingThreadExecutor {

    private static final AtomicDouble PROBABILITY = new AtomicDouble(0.0);

    private UseCallingThreadExecutor() {}

    static boolean shouldUseCallingThreadExecutor() {
        return SafeThreadLocalRandom.get().nextDouble() < PROBABILITY.doubleValue();
    }

    /**
     * Sets the probability for using the {@link com.palantir.dialogue.blocking.CallingThreadExecutor} optimization.
     *
     * Applications that would like to try this out need to add a dependency on {@code dialogue-serde} and then
     * add their own class in the {@code com.palantir.conjure.java.dialogue.serde} and making this method public.
     *
     * @param probability number between {@code 0.0} and {@code 1.0}.
     */
    static void setCallingThreadExecutorProbability(double probability) {
        Preconditions.checkArgument(
                probability >= 0.0 && probability <= 1.0, "Probability should be between 0.0 and " + "1.0");
        PROBABILITY.set(probability);
    }
}
