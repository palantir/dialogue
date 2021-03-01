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
import com.palantir.logsafe.UnsafeArg;
import com.palantir.random.SafeThreadLocalRandom;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Optional;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Package private API for enabling
 * {@link com.palantir.dialogue.blocking.CallingThreadExecutor} optimization.
 */
final class UseCallingThreadExecutor {

    @VisibleForTesting
    static final String SYSTEM_PROPERTY = "dialogue.experimental.blockoncallingthread.rate";

    @VisibleForTesting
    static final float DEFAULT_PROBABILITY = 1.0f;

    private static final Logger log = LoggerFactory.getLogger(UseCallingThreadExecutor.class);

    private static final float DISABLED = 0.0f;
    private static final float ENABLED = 1.0f;

    /** All the calling code is static, does not make sense to plumb all that through for a temporary feature flag. */
    @SuppressWarnings("deprecation")
    private static final Delegate INSTANCE = new Delegate(
            SafeThreadLocalRandom.get(),
            SharedTaggedMetricRegistries.getSingleton(),
            getInitialProbability().orElse(DEFAULT_PROBABILITY));

    private UseCallingThreadExecutor() {}

    static boolean shouldUseCallingThreadExecutor() {
        return INSTANCE.shouldUseCallingThreadExecutor();
    }

    @VisibleForTesting
    static void setCallingThreadExecutorProbability(float newProbability) {
        INSTANCE.setProbability(newProbability);
    }

    static final class Delegate {

        private final Random random;
        private final Meter enabledMeter;
        private final Meter disabledMeter;
        private volatile float probability;

        @VisibleForTesting
        Delegate(Random random, TaggedMetricRegistry taggedMetricRegistry, float probability) {
            this.random = random;
            DialogueFeatureFlagsMetrics metrics = DialogueFeatureFlagsMetrics.of(taggedMetricRegistry);
            this.enabledMeter = metrics.callingThreadExecutorEnabled();
            this.disabledMeter = metrics.callingThreadExecutorDisabled();

            this.probability = probability;
        }

        boolean shouldUseCallingThreadExecutor() {
            boolean enabled = shouldUseCallingThreadExecutorImpl();
            Meter meter = enabled ? enabledMeter : disabledMeter;
            meter.mark();
            return enabled;
        }

        void setProbability(float newProbability) {
            Preconditions.checkArgument(
                    UseCallingThreadExecutor.isInBounds(probability), "Probability should be between 0.0 and 1.0");
            this.probability = newProbability;
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

    @SuppressWarnings("CatchBlockLogException")
    @VisibleForTesting
    static Optional<Float> getInitialProbability() {
        return Optional.ofNullable(System.getProperty(SYSTEM_PROPERTY, null))
                .flatMap(probabilityString -> {
                    try {
                        return Optional.of(Float.parseFloat(probabilityString));
                    } catch (NumberFormatException e) {
                        log.error("Could not parse probability value", UnsafeArg.of("probability", probabilityString));
                        return Optional.empty();
                    }
                })
                .filter(UseCallingThreadExecutor::isInBounds);
    }

    private static boolean isInBounds(float probability) {
        return probability >= DISABLED && probability <= ENABLED;
    }
}
