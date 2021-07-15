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

package com.palantir.dialogue.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public final class HostAndLimitedChannelsTest {

    @Mock
    private LimitedChannel channel1;

    @Mock
    private LimitedChannel channel2;

    private HostAndLimitedChannels hostAndLimitedChannels;

    private final Random random = new Random(1234512312);

    @BeforeEach
    public void beforeEach() {
        hostAndLimitedChannels = HostAndLimitedChannels.createAndAssignHostIdx(ImmutableList.of(channel1, channel2));
    }

    @Test
    public void testShufflingKeepsSanity() {
        HostAndLimitedChannels shuffled = hostAndLimitedChannels.shuffle(random);
        assertThat(shuffled.getChannels())
                .containsExactly(
                        hostAndLimitedChannels.getChannels().get(1),
                        hostAndLimitedChannels.getChannels().get(0));
        hostAndLimitedChannels.getChannels().forEach(channel -> assertThat(shuffled.getByHostIdx(channel.getHostIdx()))
                .isEqualTo(channel));
    }
}
