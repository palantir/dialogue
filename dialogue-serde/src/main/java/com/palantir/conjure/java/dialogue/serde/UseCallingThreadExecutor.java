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

import com.codahale.metrics.Meter;
import com.google.common.annotations.VisibleForTesting;
import com.palantir.logsafe.Preconditions;
import com.palantir.random.SafeThreadLocalRandom;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Random;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Package private API for enabling
 * {@link com.palantir.dialogue.blocking.CallingThreadExecutor} optimization.
 */
final class UseCallingThreadExecutor {

    private static final Logger log = LoggerFactory.getLogger(UseCallingThreadExecutor.class);
    private static final float DEFAULT_PROBABILITY = 0.01f;
    private static final float DISABLED = 0.0f;
    private static final float ENABLED = 1.0f;

    /** All the calling code is static, does not make sense to plumb all that through for a temporary feature flag. */
    @SuppressWarnings("deprecated")
    private static final UseCallingThreadExecutor INSTANCE =
            new UseCallingThreadExecutor(SafeThreadLocalRandom.get(), SharedTaggedMetricRegistries.getSingleton());

    private final Random random;
    private final Consumer<RuntimeException> failureConsumer;
    private final DialogueFeatureFlagsMetrics metrics;
    private volatile float probability = DEFAULT_PROBABILITY;

    @VisibleForTesting
    UseCallingThreadExecutor(Random random, TaggedMetricRegistry taggedMetricRegistry) {
        this.random = random;
        this.metrics = DialogueFeatureFlagsMetrics.of(taggedMetricRegistry);
        failureConsumer = runtimeException -> {
            log.info("Unexpected exception from calling thread executor. Disabling this feature", runtimeException);
            metrics.callingThreadExecutorFailure().mark();
            setCallingThreadExecutorProbability(DISABLED);
        };
    }

    boolean shouldUseCallingThreadExecutor() {
        boolean enabled = shouldUseCallingThreadExecutorImpl();
        Meter meter = enabled ? metrics.callingThreadExecutorEnabled() : metrics.callingThreadExecutorDisabled();
        meter.mark();
        return enabled;
    }

    Consumer<RuntimeException> failureConsumer() {
        return failureConsumer;
    }

    /**
     * Sets the probability for using the {@link com.palantir.dialogue.blocking.CallingThreadExecutor} optimization.
     *
     * Applications that would like to try this out need to add a dependency on {@code dialogue-serde} and then
     * add their own class in the {@code com.palantir.conjure.java.dialogue.serde} and making this method public.
     *
     * @param newProbability number between {@code 0.0} and {@code 1.0}.
     */
    void setCallingThreadExecutorProbability(float newProbability) {
        Preconditions.checkArgument(
                newProbability >= DISABLED && newProbability <= ENABLED,
                "Probability should be between 0.0 and " + "1.0");
        probability = newProbability;
    }

    static UseCallingThreadExecutor get() {
        return INSTANCE;
    }

    private boolean shouldUseCallingThreadExecutorImpl() {
        float currentProbability = probability;
        if (currentProbability == DISABLED) {
            return false;
        } else if (currentProbability == ENABLED) {
            return true;
        }
        return random.nextFloat() < currentProbability;
    }
}
