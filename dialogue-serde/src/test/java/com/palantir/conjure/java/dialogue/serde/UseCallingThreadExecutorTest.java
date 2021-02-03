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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.palantir.tritium.metrics.registry.DefaultTaggedMetricRegistry;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class UseCallingThreadExecutorTest {

    @Mock
    private Random random;

    private UseCallingThreadExecutor featureFlag;
    private DialogueFeatureFlagsMetrics metrics;

    @BeforeEach
    public void beforeEach() {
        TaggedMetricRegistry taggedMetricRegistry = new DefaultTaggedMetricRegistry();
        featureFlag = new UseCallingThreadExecutor(random, taggedMetricRegistry);
        metrics = DialogueFeatureFlagsMetrics.of(taggedMetricRegistry);
    }

    @Test
    public void testMarksEnabledMetrics() {
        featureFlag.setCallingThreadExecutorProbability(0.1f);
        when(random.nextFloat()).thenReturn(0.01f);
        assertThat(featureFlag.shouldUseCallingThreadExecutor()).isTrue();
        assertThat(metrics.callingThreadExecutorEnabled().getCount()).isOne();
    }
}
